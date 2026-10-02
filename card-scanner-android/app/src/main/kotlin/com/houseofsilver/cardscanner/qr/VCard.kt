package com.houseofsilver.cardscanner.qr

import com.houseofsilver.cardscanner.domain.model.Contact

/**
 * Builds a vCard 3.0 payload for QR encoding.
 *
 * vCard rather than a bare URL or MECARD because both iOS and Android camera apps recognise it
 * and offer "Add to contacts" directly — the person opposite you needs no particular app.
 */
object VCard {

    fun build(contact: Contact): String {
        val lines = mutableListOf("BEGIN:VCARD", "VERSION:3.0")

        val (family, given) = splitName(contact.name)
        lines += "N:${esc(family)};${esc(given)};;;"
        lines += "FN:${esc(contact.name)}"

        contact.company?.takeIf { it.isNotBlank() }?.let { lines += "ORG:${esc(it)}" }
        contact.role?.takeIf { it.isNotBlank() }?.let { lines += "TITLE:${esc(it)}" }
        contact.email.takeIf { it.isNotBlank() }?.let { lines += "EMAIL;TYPE=INTERNET:${esc(it)}" }
        contact.phone?.takeIf { it.isNotBlank() }?.let { lines += "TEL;TYPE=CELL:${esc(it)}" }
        contact.website?.takeIf { it.isNotBlank() }?.let { lines += "URL:${esc(it)}" }
        contact.linkedin?.takeIf { it.isNotBlank() }?.let { lines += "URL:${esc(normaliseUrl(it))}" }
        contact.address?.takeIf { it.isNotBlank() }?.let {
            // vCard ADR is structured; everything goes in the street slot rather than guessing
            lines += "ADR;TYPE=WORK:;;${esc(it.replace("\n", ", "))};;;;"
        }

        lines += "END:VCARD"
        return lines.joinToString("\r\n")
    }

    /** True when there is enough here to be worth showing someone. */
    fun isUsable(contact: Contact): Boolean =
        contact.name.isNotBlank() || contact.email.isNotBlank()

    private fun splitName(full: String): Pair<String, String> {
        val parts = full.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "" to ""
            parts.size == 1 -> parts[0] to ""
            else -> parts.last() to parts.dropLast(1).joinToString(" ")
        }
    }

    private fun normaliseUrl(value: String): String =
        if (value.startsWith("http://") || value.startsWith("https://")) value else "https://$value"

    /** Backslash, semicolon, comma and newline are structural in vCard values. */
    private fun esc(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
}
