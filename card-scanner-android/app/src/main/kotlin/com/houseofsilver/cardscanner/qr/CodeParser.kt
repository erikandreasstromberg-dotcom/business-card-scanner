package com.houseofsilver.cardscanner.qr

import com.houseofsilver.cardscanner.domain.model.Contact

/**
 * Turns the payload of a scanned 2D code into contact fields.
 *
 * [CodeResult.precise] distinguishes the two very different things a card QR can hold:
 * a vCard carries real, structured contact data and should override anything OCR guessed;
 * a LinkedIn or website URL carries only a link, and the name we can infer from a URL slug
 * is worse than what OCR reads off the card (it loses diacritics and real capitalisation).
 */
object CodeParser {

    data class CodeResult(val contact: Contact, val precise: Boolean)

    private val linkedinRegex =
        Regex("""(?i)linkedin\.com/(?:in|pub)/([A-Za-z0-9\-_%]+)""")

    private val urlRegex = Regex("""(?i)^(https?://|www\.)\S+$""")

    fun parse(raw: String): CodeResult? {
        val text = raw.trim()
        if (text.isBlank()) return null

        return when {
            text.startsWith("BEGIN:VCARD", ignoreCase = true) ->
                parseVCard(text)?.let { CodeResult(it, precise = true) }

            text.startsWith("MECARD:", ignoreCase = true) ->
                parseMeCard(text)?.let { CodeResult(it, precise = true) }

            linkedinRegex.containsMatchIn(text) -> {
                val slug = linkedinRegex.find(text)!!.groupValues[1]
                CodeResult(
                    Contact(
                        name = nameFromSlug(slug),
                        email = "",
                        linkedin = text,
                        company = null, role = null, phone = null, website = null, address = null
                    ),
                    precise = false
                )
            }

            urlRegex.matches(text) -> CodeResult(
                Contact(
                    name = "", email = "", website = text,
                    company = null, role = null, phone = null, linkedin = null, address = null
                ),
                precise = false
            )

            else -> null
        }
    }

    // ---------------------------------------------------------------- vCard

    private fun parseVCard(text: String): Contact? {
        var name = ""
        var structuredName = ""
        var email = ""
        var phone = ""
        var company: String? = null
        var role: String? = null
        var website: String? = null
        var linkedin: String? = null
        var address: String? = null

        for (line in unfold(text)) {
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val head = line.substring(0, separator)
            val rawValue = line.substring(separator + 1)
            val value = unescape(rawValue).trim()
            if (value.isBlank()) continue

            when (head.substringBefore(';').uppercase()) {
                "FN" -> name = value
                // N is Family;Given;Middle;Prefix;Suffix — only used if FN is absent
                "N" -> {
                    val parts = splitComponents(rawValue)
                    val family = parts.getOrNull(0).orEmpty()
                    val given = parts.getOrNull(1).orEmpty()
                    structuredName = listOf(given, family).filter { it.isNotBlank() }.joinToString(" ")
                }
                "ORG" -> company = splitComponents(rawValue).first().takeIf { it.isNotBlank() }
                "TITLE" -> role = value
                "EMAIL" -> if (email.isBlank()) email = value.lowercase()
                "TEL" -> if (phone.isBlank()) phone = value
                "URL" -> if (value.contains("linkedin.com", ignoreCase = true)) {
                    if (linkedin == null) linkedin = value
                } else if (website == null) {
                    website = value
                }
                // ADR is PO;Ext;Street;City;Region;Postcode;Country
                "ADR" -> if (address == null) {
                    address = splitComponents(rawValue)
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                        .takeIf { it.isNotBlank() }
                }
            }
        }

        val finalName = name.ifBlank { structuredName }
        if (finalName.isBlank() && email.isBlank()) return null

        return Contact(
            name = finalName,
            email = email,
            company = company,
            role = role,
            phone = phone.takeIf { it.isNotBlank() },
            website = website,
            linkedin = linkedin,
            address = address
        )
    }

    /** vCard wraps long values by starting the continuation line with a space or tab. */
    private fun unfold(text: String): List<String> {
        val out = mutableListOf<String>()
        for (rawLine in text.replace("\r\n", "\n").split('\n')) {
            if (rawLine.startsWith(" ") || rawLine.startsWith("\t")) {
                if (out.isNotEmpty()) out[out.lastIndex] = out.last() + rawLine.drop(1)
            } else if (rawLine.isNotBlank()) {
                out += rawLine
            }
        }
        return out
    }

    /**
     * Splits a multi-component value on its separators only. Unescaping has to happen after the
     * split, not before — otherwise an escaped semicolon inside a value ("Silver\, Gold & Co\;
     * Ltd") turns into a separator and the tail of the name is lost.
     */
    private fun splitComponents(raw: String): List<String> =
        raw.split(Regex("""(?<!\\);""")).map { unescape(it).trim() }

    private fun unescape(value: String): String = value
        .replace("\\n", "\n")
        .replace("\\N", "\n")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")

    // ---------------------------------------------------------------- MECARD

    private fun parseMeCard(text: String): Contact? {
        val body = text.removePrefix("MECARD:").removePrefix("mecard:")
        var name = ""
        var email = ""
        var phone = ""
        var company: String? = null
        var role: String? = null
        var website: String? = null
        var address: String? = null

        for (field in body.split(Regex("""(?<!\\);"""))) {
            val separator = field.indexOf(':')
            if (separator <= 0) continue
            val key = field.substring(0, separator).uppercase()
            val value = unescape(field.substring(separator + 1)).trim()
            if (value.isBlank()) continue

            when (key) {
                // MECARD names are "Family,Given"
                "N" -> name = value.split(",").map { it.trim() }.let { parts ->
                    if (parts.size >= 2) "${parts[1]} ${parts[0]}" else parts[0]
                }
                "EMAIL" -> if (email.isBlank()) email = value.lowercase()
                "TEL" -> if (phone.isBlank()) phone = value
                "ORG" -> company = value
                "TITLE" -> role = value
                "URL" -> website = value
                "ADR" -> address = value
            }
        }

        if (name.isBlank() && email.isBlank()) return null
        return Contact(
            name = name,
            email = email,
            company = company,
            role = role,
            phone = phone.takeIf { it.isNotBlank() },
            website = website,
            linkedin = null,
            address = address
        )
    }

    // ---------------------------------------------------------------- helpers

    /**
     * "andres-kowalczyk" -> "Andres Kowalczyk". Tokens containing digits are dropped, since
     * LinkedIn appends a disambiguating hash to slugs ("john-smith-1a2b3c4").
     */
    fun nameFromSlug(slug: String): String = slug
        .split('-', '_')
        .filter { it.isNotBlank() && it.none { ch -> ch.isDigit() } }
        .joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercaseChar() }
        }

    /**
     * Merges code data with OCR output. A precise code overrides OCR; an imprecise one only
     * fills gaps, so a card's real spelling beats a name reconstructed from a URL slug.
     */
    fun merge(code: CodeResult?, ocr: Contact): Contact {
        if (code == null) return ocr
        val (strong, weak) = if (code.precise) code.contact to ocr else ocr to code.contact
        return Contact(
            name = strong.name.ifBlank { weak.name },
            email = strong.email.ifBlank { weak.email },
            company = strong.company ?: weak.company,
            role = strong.role ?: weak.role,
            phone = strong.phone ?: weak.phone,
            website = strong.website ?: weak.website,
            linkedin = strong.linkedin ?: weak.linkedin,
            address = strong.address ?: weak.address
        )
    }
}
