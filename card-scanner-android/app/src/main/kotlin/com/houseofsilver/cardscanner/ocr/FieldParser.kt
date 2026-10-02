package com.houseofsilver.cardscanner.ocr

import com.houseofsilver.cardscanner.domain.model.Contact
import java.text.Normalizer

enum class FieldConfidence { HIGH, LOW }

data class ParsedField<T>(val value: T?, val confidence: FieldConfidence)

data class ParsedContact(
    val name: ParsedField<String>,
    val email: ParsedField<String>,
    val company: ParsedField<String>,
    val role: ParsedField<String>,
    val phone: ParsedField<String>,
    val website: ParsedField<String>,
    val linkedin: ParsedField<String>,
    val address: ParsedField<String>
)

/**
 * Parses OCR text into contact fields.
 *
 * The key insight driving the design: on a real business card the *largest* and *topmost* text is
 * usually the company wordmark, not the person — so layout heuristics alone get name and company
 * backwards. The email address and LinkedIn URL are far stronger signals and are extracted
 * reliably by regex:
 *
 *   annika.reyes@vantageroe.com  ->  name  "Annika Reyes"   company  "Vantage & Roe"
 *
 * So we derive name from the email local part + LinkedIn slug, and company from the email domain,
 * then match those back against the OCR lines. Matching back (rather than just title-casing the
 * email) keeps the card's real spelling — diacritics, hyphens, ampersands: "Sofía Nakamura",
 * "Jonas Al-Rashid", "Vantage & Roe". Fuzzy matching absorbs OCR slips such as reading
 * "Whitfield" as "Nhitfield".
 *
 * Layout heuristics remain as fallbacks for cards with no email or a freemail address.
 */
object FieldParser {

    private val emailRegex = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")

    private val phoneRegex = Regex(
        """(\+?[0-9]{1,3}[\s\-.]?)?(\(?\d{1,4}\)?[\s\-.]?)(\d{1,4}[\s\-.]?){2,4}\d{1,4}"""
    )

    private val linkedinRegex = Regex("""(?:https?://)?(?:www\.)?linkedin\.com/in/[a-zA-Z0-9\-_%]+/?""")

    private val urlRegex = Regex("""https?://[^\s/$.?#].[^\s]*""")

    private val postalCodeRegex = Regex(
        """(?:[A-Z]{1,2}\d{1,2}[A-Z]?\s?\d[A-Z]{2}|\d{3,6}(?:[\s\-]\d{2,4})?)"""
    )

    private val companySuffixRegex = Regex(
        """(?i)\b(AB|Ltd|LLC|LLP|Inc|GmbH|ApS|Oy|BV|NV|AG|SRL|SAS|PLC|Corp|Holding|Holdings)\b"""
    )

    /** "Est. 2014" on a maker's seal looks like a postal line; it isn't one. */
    private val establishedRegex = Regex("""(?i)\b(est|established|since|founded|anno)\b""")

    /** Job-title words that on their own strongly imply the line is a role. */
    private val strongRoleKeywords = setOf(
        "chief", "director", "head", "president", "founder", "cofounder", "co-founder",
        "owner", "partner", "officer", "ceo", "cto", "cfo", "coo", "cmo", "cso", "cpo",
        "vp", "svp", "evp", "manager", "consultant", "principal", "executive", "senior",
        "lead", "chair", "chairman", "chairwoman", "chairperson"
    )

    /** Weaker signals — a real role usually pairs these with something else. */
    private val weakRoleKeywords = setOf(
        "engineer", "developer", "designer", "analyst", "architect", "specialist",
        "advisor", "adviser", "associate", "coordinator", "supervisor", "administrator",
        "scientist", "researcher", "strategist", "counsel", "roaster", "curator", "master"
    )

    /** Whole-word country match — substring matching would fire "uk" inside ordinary words. */
    private val countryRegex = Regex(
        """(?i)\b(sweden|sverige|norway|norge|denmark|danmark|finland|iceland|germany|deutschland|""" +
            """france|spain|espana|italy|italia|poland|portugal|austria|belgium|ireland|greece|""" +
            """netherlands|holland|switzerland|schweiz|luxembourg|czechia|estonia|latvia|lithuania|""" +
            """singapore|japan|china|india|korea|south korea|thailand|vietnam|malaysia|indonesia|""" +
            """australia|new zealand|canada|brazil|mexico|argentina|chile|""" +
            """usa|u\.s\.a|united states|uk|united kingdom|england|scotland|wales|""" +
            """uae|emirates|qatar|bahrain|kuwait|oman|saudi arabia|israel|turkey|egypt|""" +
            """south africa|kenya|nigeria|morocco)\b"""
    )

    /** Address-line words that stand alone: "340 Bryant Street", "Suite 210", "Unit 1204". */
    private val streetWords = setOf(
        "street", "st", "str", "road", "rd", "avenue", "ave", "lane", "drive", "boulevard",
        "blvd", "square", "suite", "unit", "floor", "building", "bldg", "plaza", "highway",
        "terrace", "court", "crescent", "parkway", "route", "apartment", "apt", "room",
        "level", "box", "quay", "wharf", "gate", "pier", "berth",
        // Romance-language street types, which stand alone rather than gluing on
        "rue", "calle", "avenida", "rua", "via", "viale", "corso", "piazza", "praca",
        "strada", "camino", "paseo", "carrer"
    )

    /**
     * Nordic, German and Dutch street names glue the street type onto the name rather than
     * separating it — "Birger Jarlsgatan", "Chausseestraße", "Wilhelminakade" — so these
     * match as suffixes instead of whole words.
     */
    private val streetSuffixes = listOf(
        "gatan", "gata", "vägen", "vagen", "väg", "gränd", "torg", "stigen", "backen",
        "straße", "strasse", "allee", "platz", "weg", "damm", "ufer",
        "straat", "laan", "plein", "kade", "dijk", "gracht", "gade", "vej", "katu", "tie",
        "tún", "tun"
    )

    private val freemailDomains = setOf(
        "gmail.com", "googlemail.com", "outlook.com", "hotmail.com", "live.com",
        "yahoo.com", "icloud.com", "me.com", "proton.me", "protonmail.com", "gmx.de"
    )

    private val knownTlds = setOf(
        "com", "net", "org", "io", "ai", "co", "app", "dev", "me",
        "se", "no", "dk", "fi", "is", "de", "fr", "es", "it", "pt", "nl", "be", "at",
        "ch", "ie", "gr", "cz", "pl", "hu", "ro", "bg", "hr", "si", "sk", "ee", "lv", "lt",
        "uk", "us", "eu", "ca", "mx", "br", "ar", "cl",
        "ae", "sa", "qa", "il", "tr", "za", "ke", "ng", "ma",
        "sg", "jp", "kr", "cn", "hk", "tw", "in", "th", "vn", "my", "id", "ph",
        "au", "nz", "law", "legal", "consulting", "agency", "studio", "tech", "design"
    )

    /**
     * One or more dot-segments, so multi-part suffixes survive intact — a single optional
     * segment truncated "kliprand.co.za" to "kliprand.co".
     */
    private val bareDomainRegex = Regex(
        """(?:www\.)?[a-zA-Z0-9][a-zA-Z0-9\-]*(?:\.[a-zA-Z]{2,})+(?:/[^\s]*)?"""
    )

    /**
     * OCR sometimes puts a space after the final dot of a domain
     * ("www.perihelionrecords. pt"). Closing just that gap is safer than stripping every space
     * from the line, which glued a phone number onto the domain: "27824471096www.kliprand.co".
     */
    private fun repairSplitDomain(text: String): String =
        text.replace(Regex("""\.\s+(?=[a-zA-Z]{2,4}\b)"""), ".")

    // ---------------------------------------------------------------- entry points

    fun parse(ocrOutput: OcrOutput): ParsedContact {
        val lines = if (ocrOutput.blocks.isNotEmpty()) {
            ocrOutput.blocks.flatMap { block ->
                block.lines.map { line ->
                    LineInfo(text = line.text.trim(), boundingBox = line.boundingBox)
                }
            }
        } else {
            // No ML Kit blocks (unit tests, or a recognizer that only gave us flat text)
            ocrOutput.rawText.lines().map { LineInfo(text = it.trim(), boundingBox = null) }
        }
        return parseLines(lines.filter { it.text.isNotBlank() })
    }

    /** Test-friendly entry point: parse from plain text lines. */
    fun parseText(rawText: String): ParsedContact =
        parseLines(rawText.lines().map { LineInfo(it.trim(), null) }.filter { it.text.isNotBlank() })

    // ---------------------------------------------------------------- core

    private fun parseLines(allLines: List<LineInfo>): ParsedContact {
        val email = extractEmail(allLines)
        val linkedin = extractLinkedin(allLines)
        val website = extractWebsite(allLines)

        // Address first: it holds postal codes that would otherwise be mistaken for phone numbers.
        val addressResult = collectAddressLines(allLines)
        val phone = extractPhone(allLines, addressResult.sources)

        val address: ParsedField<String> = if (addressResult.display.isNotEmpty()) {
            ParsedField(addressResult.display.joinToString("\n"), FieldConfidence.HIGH)
        } else {
            ParsedField(null, FieldConfidence.LOW)
        }

        val claimedTexts = buildSet {
            email.value?.let { add(it) }
            phone.value?.let { add(it) }
            linkedin.value?.let { add(it) }
            website.value?.let { add(it.removePrefix("https://")) }
            addressResult.sources.forEach { add(it) }
        }

        // Only the line *carrying* a claimed value is consumed. Deliberately not the reverse:
        // a short company wordmark is often a substring of the website ("TESSELLATE" inside
        // "www.tessellate.io"), and matching that way would swallow the company line.
        val unclaimed = allLines.filter { line ->
            claimedTexts.none { claimed ->
                claimed.isNotBlank() && line.text.contains(claimed, ignoreCase = true)
            }
        }

        // Reference strings built from the machine-readable identity signals.
        val emailLocal = email.value?.substringBefore('@')?.let { normalize(it) } ?: ""
        val linkedinSlug = linkedin.value
            ?.substringAfter("/in/")?.trimEnd('/')?.let { normalize(it) } ?: ""
        val identityRef = (emailLocal + linkedinSlug)

        val domainBase = companyDomainBase(email.value, website.value)

        val name = extractName(unclaimed, identityRef)
        val company = extractCompany(unclaimed, domainBase, name.value)
        val role = extractRole(unclaimed, name.value, company.value)

        return ParsedContact(
            name = name,
            email = email,
            company = company,
            role = role,
            phone = phone,
            website = inferWebsiteFromEmail(website, email.value?.substringAfter('@')?.lowercase()),
            linkedin = linkedin,
            address = address
        )
    }

    /**
     * The stem used to recognise the company on the card. Prefers the email domain, but falls
     * back to the website — cards where OCR drops the email still carry "www.company.com", and
     * that is enough to identify a mangled wordmark ("VAND ERG RIFT MARINE").
     */
    private fun companyDomainBase(email: String?, website: String?): String? {
        val emailDomain = email?.substringAfter('@')?.lowercase()?.takeIf { it !in freemailDomains }
        val siteDomain = website
            ?.removePrefix("https://")?.removePrefix("http://")
            ?.removePrefix("www.")?.substringBefore('/')?.lowercase()
        val source = emailDomain ?: siteDomain ?: return null
        return normalize(source.substringBefore('.')).takeIf { it.length >= 3 }
    }

    // ---------------------------------------------------------------- name

    private fun extractName(lines: List<LineInfo>, identityRef: String): ParsedField<String> {
        if (identityRef.isNotBlank()) {
            val best = lines
                .filter { looksLikeNameLine(it.text) }
                .map { it to nameScore(it.text, identityRef) }
                .filter { (_, score) -> score >= 0.5 }
                .maxByOrNull { (line, score) ->
                    // Prefer a strong match; break ties toward 2–4 word lines (typical full names)
                    val words = wordsOf(line.text).size
                    score + if (words in 2..4) 0.05 else 0.0
                }
            if (best != null) {
                return ParsedField(best.first.text.trim(), FieldConfidence.HIGH)
            }
        }

        // No usable email/LinkedIn signal — fall back to the most prominent name-shaped line.
        val fallback = lines
            .filter { looksLikeNameLine(it.text) && wordsOf(it.text).size in 2..4 }
            .maxByOrNull { it.boundingBox?.height() ?: 0 }
            ?: lines.firstOrNull { looksLikeNameLine(it.text) }

        return ParsedField(fallback?.text?.trim(), FieldConfidence.LOW)
    }

    /**
     * Fraction of the line's words that appear in the email local part / LinkedIn slug.
     * Words of 5+ characters are allowed one edit, so "Nhitfield" still matches "whitfield".
     */
    private fun nameScore(line: String, identityRef: String): Double {
        val tokens = wordsOf(line).map { normalize(it) }.filter { it.length >= 2 }
        if (tokens.isEmpty()) return 0.0
        val matched = tokens.count { refContains(identityRef, it) }
        return matched.toDouble() / tokens.size
    }

    private fun refContains(reference: String, token: String): Boolean {
        if (reference.contains(token)) return true
        if (token.length < 5) return false
        // Allow a single edit against any same-ish-length window of the reference
        for (len in (token.length - 1)..(token.length + 1)) {
            if (len <= 0 || len > reference.length) continue
            for (start in 0..(reference.length - len)) {
                if (levenshtein(reference.substring(start, start + len), token) <= 1) return true
            }
        }
        return false
    }

    private fun looksLikeNameLine(text: String): Boolean {
        if (text.length !in 3..60) return false
        if (looksLikeContactLine(text)) return false
        if (postalCodeRegex.containsMatchIn(text)) return false
        if (!text.any { it.isLetter() }) return false
        if (text.count { it.isDigit() } > 2) return false
        // Reject code/filename debris that shows up when scanning a screen
        if (text.contains(Regex("""\w\.\w"""))) return false
        return wordsOf(text).size in 1..5
    }

    /**
     * True for lines that carry an address, URL or handle rather than a human-readable label.
     * Spaces are squeezed out first because OCR often splits a domain — "www.perihelionrecords. pt"
     * — which would otherwise slip past and win the company slot.
     */
    private fun looksLikeContactLine(text: String): Boolean {
        if (text.contains("@")) return true
        if (text.contains("linkedin.com", ignoreCase = true)) return true
        if (urlRegex.containsMatchIn(text)) return true
        if (text.contains("www.", ignoreCase = true)) return true
        val match = bareDomainRegex.find(repairSplitDomain(text)) ?: return false
        val tld = match.value.substringAfterLast('.').substringBefore('/').lowercase()
        return tld in knownTlds
    }

    // ---------------------------------------------------------------- company

    private fun extractCompany(
        lines: List<LineInfo>,
        domainBase: String?,
        nameText: String?
    ): ParsedField<String> {
        val candidates = lines.filter { line ->
            !line.text.equals(nameText, ignoreCase = true) &&
                line.text.length >= 2 &&
                line.text.any { it.isLetter() } &&
                !looksLikeContactLine(line.text)
        }
        if (candidates.isEmpty()) return ParsedField(null, FieldConfidence.LOW)

        // Priority 1: the line that matches the email/website domain — "vantageroe.com" -> "Vantage & Roe"
        if (domainBase != null) {
            var bestText: String? = null
            var bestScore = 0.0
            for (line in candidates) {
                val score = domainScore(line.text, domainBase)
                if (score > bestScore) {
                    bestScore = score
                    bestText = line.text.trim()
                }
            }
            // A narrow column can break a wordmark across two lines ("TINTA" / "ALBA"). Merging is
            // only safe on an exact domain match — a loose one would swallow the tagline beneath
            // the wordmark ("NUMEN" + "HOSPITALITY GROUP — HOTELS & RESIDENCES").
            for (i in 0 until candidates.size - 1) {
                val joined = "${candidates[i].text.trim()} ${candidates[i + 1].text.trim()}"
                if (domainScore(joined, domainBase) >= 1.0) {
                    bestText = joined
                    break
                }
            }
            if (bestText != null) return ParsedField(bestText, FieldConfidence.HIGH)
        }

        // Priority 2: an explicit legal suffix (AB, GmbH, Ltd…)
        candidates.firstOrNull { companySuffixRegex.containsMatchIn(it.text) }?.let {
            return ParsedField(it.text.trim(), FieldConfidence.HIGH)
        }

        // Priority 3: most prominent remaining line
        val byFontSize = candidates
            .filter { (it.boundingBox?.height() ?: 0) > 0 }
            .maxByOrNull { it.boundingBox!!.height() }
        if (byFontSize != null) return ParsedField(byFontSize.text.trim(), FieldConfidence.LOW)

        return ParsedField(candidates.first().text.trim(), FieldConfidence.LOW)
    }

    /** How well a line matches the company domain, e.g. "Halcyon Bio" vs "halcyonbio". */
    private fun domainScore(line: String, domainBase: String): Double {
        val n = normalize(line)
        if (n.length < 4) return 0.0
        return when {
            n == domainBase -> 1.0
            // "Verrand" inside "studioverrand", "Numen" inside "numenhospitality"
            domainBase.contains(n) -> 0.5 + 0.4 * (n.length.toDouble() / domainBase.length)
            n.contains(domainBase) -> 0.5 + 0.4 * (domainBase.length.toDouble() / n.length)
            else -> 0.0
        }
    }

    // ---------------------------------------------------------------- role

    private fun extractRole(
        lines: List<LineInfo>,
        nameText: String?,
        companyText: String?
    ): ParsedField<String> {
        val best = lines
            .filter {
                !it.text.equals(nameText, ignoreCase = true) &&
                    !it.text.equals(companyText, ignoreCase = true) &&
                    it.text.length in 3..80
            }
            .map { it to roleScore(it.text) }
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }

        return if (best != null) {
            ParsedField(best.first.text.trim(), FieldConfidence.HIGH)
        } else {
            ParsedField(null, FieldConfidence.LOW)
        }
    }

    /**
     * Counts job-title words, matched on whole-word boundaries so a company descriptor like
     * "Protein engineering" doesn't register as the "engineer" role.
     */
    private fun roleScore(line: String): Int {
        val words = wordsOf(line).map { it.lowercase().trim('.', ',') }
        if (words.isEmpty()) return 0
        var score = 0
        for (w in words) {
            when {
                w in strongRoleKeywords -> score += 3
                w in weakRoleKeywords -> score += 1
            }
        }
        // Location/descriptor lines are usually separated by bullets or dashes
        if (line.contains('·') || line.contains('—')) score -= 1
        return score.coerceAtLeast(0)
    }

    // ---------------------------------------------------------------- structured fields

    private fun extractEmail(lines: List<LineInfo>): ParsedField<String> {
        for (line in lines) {
            emailRegex.find(line.text)?.let {
                return ParsedField(it.value.lowercase(), FieldConfidence.HIGH)
            }
        }
        return ParsedField(null, FieldConfidence.LOW)
    }

    /**
     * Phone numbers routinely share a line with the email or website
     * ("hana@x.pt · +351 91 284 7710"), so those are stripped out and the remainder searched
     * rather than skipping the whole line. Address lines are excluded outright — a postal code
     * like "1200-069" is otherwise indistinguishable from a phone number.
     */
    private fun extractPhone(
        lines: List<LineInfo>,
        addressLines: Set<String>
    ): ParsedField<String> {
        val candidates = mutableListOf<String>()
        for (line in lines) {
            if (line.text in addressLines) continue
            if (line.text.contains("linkedin.com", ignoreCase = true)) continue

            var remainder = emailRegex.replace(line.text, " ")
            remainder = urlRegex.replace(remainder, " ")
            remainder = bareDomainRegex.replace(remainder, " ")

            val match = phoneRegex.find(remainder) ?: continue
            if (match.value.count { it.isDigit() } >= 8) candidates.add(match.value.trim())
        }
        // An international prefix is the strongest confirmation we actually found a number.
        val best = candidates.firstOrNull { it.contains('+') } ?: candidates.firstOrNull()
        return if (best != null) {
            ParsedField(best, FieldConfidence.HIGH)
        } else {
            ParsedField(null, FieldConfidence.LOW)
        }
    }

    private fun extractLinkedin(lines: List<LineInfo>): ParsedField<String> {
        for (line in lines) {
            linkedinRegex.find(line.text)?.let {
                return ParsedField(it.value, FieldConfidence.HIGH)
            }
        }
        return ParsedField(null, FieldConfidence.LOW)
    }

    private fun extractWebsite(lines: List<LineInfo>): ParsedField<String> {
        for (line in lines) {
            val match = urlRegex.find(line.text) ?: continue
            if (match.value.contains("linkedin.com", ignoreCase = true)) continue
            return ParsedField(match.value, FieldConfidence.HIGH)
        }
        for (line in lines) {
            if (line.text.contains("@")) continue
            if (line.text.contains("linkedin.com", ignoreCase = true)) continue
            val match = bareDomainRegex.find(repairSplitDomain(line.text)) ?: continue
            val tld = match.value.substringAfterLast('.').substringBefore('/').lowercase()
            if (tld in knownTlds) {
                return ParsedField("https://" + match.value, FieldConfidence.HIGH)
            }
        }
        return ParsedField(null, FieldConfidence.LOW)
    }

    private fun inferWebsiteFromEmail(
        website: ParsedField<String>,
        domain: String?
    ): ParsedField<String> {
        if (website.value != null) return website
        if (domain == null || domain in freemailDomains) return website
        return ParsedField("https://$domain", FieldConfidence.LOW)
    }

    /** [display] is the cleaned address; [sources] are the raw lines it came from. */
    private class AddressResult(val display: List<String>, val sources: Set<String>)

    /**
     * Cards often crowd a phone number or website onto the same line as the address. Those
     * tokens are stripped rather than the whole line being discarded — dropping the line loses
     * "105 Reykjavík, Iceland", while keeping it whole imported "+27 82 447 1096 www.kliprand.co.za"
     * into the address. Once stripped, a line that was only a phone reads as one and is skipped.
     */
    private fun collectAddressLines(lines: List<LineInfo>): AddressResult {
        val display = mutableListOf<String>()
        val sources = mutableSetOf<String>()
        for (line in lines) {
            if (line.text.contains("linkedin.com", ignoreCase = true)) continue
            if (establishedRegex.containsMatchIn(line.text)) continue

            val cleaned = stripContactTokens(line.text)
            if (cleaned.isBlank()) continue
            if (isProbablyPhone(cleaned)) continue

            val hasCountry = countryRegex.containsMatchIn(cleaned)
            // A postal/city line only counts once a street line has opened the address, and must
            // read like a place rather than a stray number ("EST. 2014", a seal's founding year).
            val hasPostal = display.isNotEmpty() &&
                postalCodeRegex.containsMatchIn(cleaned) &&
                wordsOf(cleaned).count { w -> w.any { it.isLetter() } } >= 2

            if (hasCountry || looksLikeStreetLine(cleaned) || hasPostal) {
                display.add(cleaned)
                sources.add(line.text)
                if (display.size >= 4) break
            }
        }
        return AddressResult(display, sources)
    }

    /** Removes emails, URLs and bare domains, leaving whatever prose surrounded them. */
    private fun stripContactTokens(text: String): String {
        var t = urlRegex.replace(text, " ")
        t = emailRegex.replace(t, " ")
        t = bareDomainRegex.replace(t) { match ->
            val tld = match.value.substringAfterLast('.').substringBefore('/').lowercase()
            if (tld in knownTlds) " " else match.value
        }
        return t.replace(Regex("""[\s·|]{2,}"""), " ")
            .trim()
            .trim(',', '·', '|', '-', ';')
            .trim()
    }

    /**
     * Street lines always carry a number ("340 Bryant Street", "Chausseestraße 111"). Requiring
     * a digit is what keeps role and tagline text — "Head of Community", "Est. 2009" — out of
     * the address, since those match no street word at all once boundaries are enforced.
     */
    private fun looksLikeStreetLine(text: String): Boolean {
        if (!text.any { it.isDigit() }) return false
        val words = wordsOf(text).map { it.lowercase() }
        if (words.any { it in streetWords }) return true
        return words.any { w -> w.length >= 6 && streetSuffixes.any { w.endsWith(it) } }
    }

    private fun isProbablyPhone(text: String): Boolean {
        val digits = text.count { it.isDigit() }
        val letters = text.count { it.isLetter() }
        return digits >= 7 && letters == 0
    }

    // ---------------------------------------------------------------- helpers

    private fun wordsOf(text: String): List<String> =
        text.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotBlank() }

    /** Lowercase, strip diacritics and punctuation: "Sofía Nakamura" -> "sofianakamura". */
    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .lowercase()
            .filter { it.isLetterOrDigit() }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }

    fun toContact(parsed: ParsedContact): Contact = Contact(
        name = parsed.name.value ?: "",
        email = parsed.email.value ?: "",
        role = parsed.role.value,
        company = parsed.company.value,
        phone = parsed.phone.value,
        website = parsed.website.value,
        linkedin = parsed.linkedin.value,
        address = parsed.address.value
    )

    private data class LineInfo(
        val text: String,
        val boundingBox: android.graphics.Rect?
    )
}
