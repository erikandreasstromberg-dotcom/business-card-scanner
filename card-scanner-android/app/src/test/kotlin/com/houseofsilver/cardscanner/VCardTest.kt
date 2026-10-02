package com.houseofsilver.cardscanner

import com.houseofsilver.cardscanner.domain.model.Contact
import com.houseofsilver.cardscanner.qr.VCard
import org.junit.Assert.*
import org.junit.Test

class VCardTest {

    private val full = Contact(
        name = "Mira Holm",
        email = "mira@example.com",
        company = "Holm Studio AB",
        role = "Founder & CEO",
        phone = "+46 70 123 45 67",
        website = "example.com",
        linkedin = "linkedin.com/in/mira-holm",
        address = null
    )

    @Test
    fun `wraps payload in a vcard envelope`() {
        val out = VCard.build(full)
        assertTrue(out.startsWith("BEGIN:VCARD\r\nVERSION:3.0"))
        assertTrue(out.endsWith("END:VCARD"))
    }

    @Test
    fun `splits family name from given names`() {
        assertTrue(VCard.build(full).contains("N:Holm;Mira;;;"))
        assertTrue(VCard.build(full).contains("FN:Mira Holm"))
    }

    @Test
    fun `single word name still produces a valid N line`() {
        val out = VCard.build(full.copy(name = "Prince"))
        assertTrue(out.contains("N:Prince;;;;"))
    }

    @Test
    fun `escapes structural characters in values`() {
        // A comma in the company would otherwise split the field
        val out = VCard.build(full.copy(company = "Silver, Gold & Co; Ltd"))
        assertTrue(out.contains("ORG:Silver\\, Gold & Co\\; Ltd"))
    }

    @Test
    fun `bare linkedin handle becomes an absolute url`() {
        assertTrue(VCard.build(full).contains("URL:https://linkedin.com/in/mira-holm"))
    }

    @Test
    fun `existing scheme on linkedin is left alone`() {
        val out = VCard.build(full.copy(linkedin = "https://linkedin.com/in/mira-holm"))
        assertFalse(out.contains("https://https://"))
    }

    @Test
    fun `blank optional fields are omitted entirely`() {
        val out = VCard.build(Contact(name = "Ada Lovelace", email = "ada@example.com"))
        assertFalse(out.contains("ORG:"))
        assertFalse(out.contains("TEL"))
        assertFalse(out.contains("URL:"))
        assertTrue(out.contains("EMAIL;TYPE=INTERNET:ada@example.com"))
    }

    @Test
    fun `multiline address collapses into the street slot`() {
        val out = VCard.build(full.copy(address = "Birger Jarlsgatan 24\n114 34 Stockholm"))
        assertTrue(out.contains("ADR;TYPE=WORK:;;Birger Jarlsgatan 24\\, 114 34 Stockholm;;;;"))
    }

    @Test
    fun `needs a name or an email to be worth showing`() {
        val blank = Contact(name = "", email = "")
        assertFalse(VCard.isUsable(blank))
        assertTrue(VCard.isUsable(blank.copy(name = "Ada")))
        assertTrue(VCard.isUsable(blank.copy(email = "ada@example.com")))
    }
}
