package com.houseofsilver.cardscanner

import com.houseofsilver.cardscanner.domain.model.Contact
import com.houseofsilver.cardscanner.qr.CodeParser
import org.junit.Assert.*
import org.junit.Test

class CodeParserTest {

    // Contact declares role before company, so these use named arguments throughout
    private val emptyOcr = Contact(name = "", email = "")

    // ------------------------------------------------------------------ vCard

    @Test
    fun `parses a vcard into every field`() {
        val result = CodeParser.parse(
            """
            BEGIN:VCARD
            VERSION:3.0
            N:Kowalczyk;Andrés;;;
            FN:Andrés Kowalczyk
            ORG:Vireo Robotics
            TITLE:Head of Autonomy
            EMAIL;TYPE=INTERNET:a.kowalczyk@vireorobotics.fr
            TEL;TYPE=CELL:+33 6 74 21 38 90
            URL:https://www.vireorobotics.fr
            URL:https://linkedin.com/in/andres-kowalczyk
            ADR;TYPE=WORK:;;12 Rue de Metz;Toulouse;;31000;France
            END:VCARD
            """.trimIndent()
        )!!

        assertTrue(result.precise)
        val c = result.contact
        assertEquals("Andrés Kowalczyk", c.name)
        assertEquals("a.kowalczyk@vireorobotics.fr", c.email)
        assertEquals("Vireo Robotics", c.company)
        assertEquals("Head of Autonomy", c.role)
        assertEquals("+33 6 74 21 38 90", c.phone)
        assertEquals("https://www.vireorobotics.fr", c.website)
        // A LinkedIn URL must land in its own field, not overwrite the website
        assertEquals("https://linkedin.com/in/andres-kowalczyk", c.linkedin)
        assertEquals("12 Rue de Metz, Toulouse, 31000, France", c.address)
    }

    @Test
    fun `falls back to the structured N line when FN is absent`() {
        val result = CodeParser.parse(
            "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Halloran;Keiko;;;\r\nEND:VCARD"
        )!!
        assertEquals("Keiko Halloran", result.contact.name)
    }

    @Test
    fun `unescapes structural characters`() {
        val result = CodeParser.parse(
            "BEGIN:VCARD\nFN:Ada Lovelace\nORG:Silver\\, Gold & Co\\; Ltd\nEND:VCARD"
        )!!
        assertEquals("Silver, Gold & Co; Ltd", result.contact.company)
    }

    @Test
    fun `unfolds continuation lines`() {
        val result = CodeParser.parse(
            "BEGIN:VCARD\nFN:Ada Lovelace\nORG:Analytical Engine\n  Company\nEND:VCARD"
        )!!
        assertEquals("Analytical Engine Company", result.contact.company)
    }

    // ------------------------------------------------------------------ MECARD

    @Test
    fun `parses mecard and reorders the name`() {
        val result = CodeParser.parse("MECARD:N:Marchetti,Thandiwe;TEL:+27824471096;EMAIL:t@kliprand.co.za;;")!!
        assertTrue(result.precise)
        assertEquals("Thandiwe Marchetti", result.contact.name)
        assertEquals("t@kliprand.co.za", result.contact.email)
        assertEquals("+27824471096", result.contact.phone)
    }

    // ------------------------------------------------------------------ LinkedIn

    @Test
    fun `linkedin url yields a profile and an imprecise name`() {
        val result = CodeParser.parse("https://www.linkedin.com/in/andres-kowalczyk")!!
        assertFalse(result.precise)
        assertEquals("Andres Kowalczyk", result.contact.name)
        assertEquals("https://www.linkedin.com/in/andres-kowalczyk", result.contact.linkedin)
        assertEquals("", result.contact.email)
    }

    @Test
    fun `slug hash suffix is dropped from the derived name`() {
        assertEquals("John Smith", CodeParser.nameFromSlug("john-smith-1a2b3c4"))
    }

    @Test
    fun `plain url becomes a website`() {
        val result = CodeParser.parse("https://vireorobotics.fr")!!
        assertFalse(result.precise)
        assertEquals("https://vireorobotics.fr", result.contact.website)
    }

    @Test
    fun `unrecognised payload is ignored`() {
        assertNull(CodeParser.parse("just some text on a poster"))
        assertNull(CodeParser.parse(""))
    }

    // ------------------------------------------------------------------ merge

    @Test
    fun `precise code overrides ocr`() {
        val ocr = Contact(name = "Andres Kowalczy", email = "wrong@example.com")
        val code = CodeParser.parse(
            "BEGIN:VCARD\nFN:Andrés Kowalczyk\nEMAIL:a.kowalczyk@vireorobotics.fr\nEND:VCARD"
        )
        val merged = CodeParser.merge(code, ocr)
        assertEquals("Andrés Kowalczyk", merged.name)
        assertEquals("a.kowalczyk@vireorobotics.fr", merged.email)
    }

    @Test
    fun `precise code still lets ocr fill gaps it left`() {
        val ocr = Contact(
            name = "", email = "",
            company = "Vireo Robotics", role = "Head of Autonomy"
        )
        val code = CodeParser.parse("BEGIN:VCARD\nFN:Andrés Kowalczyk\nEND:VCARD")
        val merged = CodeParser.merge(code, ocr)
        assertEquals("Andrés Kowalczyk", merged.name)
        assertEquals("Vireo Robotics", merged.company)
        assertEquals("Head of Autonomy", merged.role)
    }

    @Test
    fun `imprecise code never overwrites what ocr read off the card`() {
        // The card spells it with an accent; the URL slug cannot
        val ocr = Contact(name = "Andrés Kowalczyk", email = "a.kowalczyk@vireorobotics.fr")
        val code = CodeParser.parse("https://linkedin.com/in/andres-kowalczyk")
        val merged = CodeParser.merge(code, ocr)
        assertEquals("Andrés Kowalczyk", merged.name)
        assertEquals("a.kowalczyk@vireorobotics.fr", merged.email)
        // but it does contribute the profile URL OCR had no way to know
        assertEquals("https://linkedin.com/in/andres-kowalczyk", merged.linkedin)
    }

    @Test
    fun `imprecise code supplies a name when ocr found none`() {
        val code = CodeParser.parse("https://linkedin.com/in/keiko-halloran")
        val merged = CodeParser.merge(code, emptyOcr)
        assertEquals("Keiko Halloran", merged.name)
        assertEquals("", merged.email)
    }

    @Test
    fun `no code leaves ocr untouched`() {
        val ocr = Contact(name = "Lars Ferreira", email = "lars@sableroux.ch")
        assertEquals(ocr, CodeParser.merge(null, ocr))
    }
}
