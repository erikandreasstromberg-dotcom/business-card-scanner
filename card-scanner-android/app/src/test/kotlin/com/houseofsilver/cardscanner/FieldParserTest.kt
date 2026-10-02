package com.houseofsilver.cardscanner

import com.houseofsilver.cardscanner.ocr.FieldConfidence
import com.houseofsilver.cardscanner.ocr.FieldParser
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [FieldParser], driven through [FieldParser.parseText] with the raw text ML Kit
 * produced for five real test cards.
 *
 * The cards in [realCards] are the ones that previously parsed wrong: the company wordmark won
 * the name slot and the person's name landed in company. They are kept verbatim as regressions.
 */
class FieldParserTest {

    // ------------------------------------------------------------------ regression: real cards

    @Test
    fun `card 1 - Vantage and Roe`() {
        val parsed = FieldParser.parseText(
            """
            VANTAGE & ROE
            MANAGEMENT CONSULTING — STOCKHOLM · OSLO
            Annika Reyes
            SENIOR CONSULTANT, OPERATIONS PRACTICE
            annika.reyes@vantageroe.com
            +46 70 412 88 05
            linkedin.com/in/annika-reyes
            Birger Jarlsgatan 24
            114 34 Stockholm, Sweden
            www.vantageroe.com
            """.trimIndent()
        )

        assertEquals("Annika Reyes", parsed.name.value)
        assertEquals(FieldConfidence.HIGH, parsed.name.confidence)
        assertEquals("VANTAGE & ROE", parsed.company.value)
        assertTrue(parsed.role.value!!.contains("SENIOR CONSULTANT"))
        assertEquals("annika.reyes@vantageroe.com", parsed.email.value)
        // Swedish street type is glued onto the name: "Jarlsgatan"
        assertEquals("Birger Jarlsgatan 24\n114 34 Stockholm, Sweden", parsed.address.value)
    }

    @Test
    fun `card 2 - Halcyon Bio - descriptor line must not win the role`() {
        val parsed = FieldParser.parseText(
            """
            HALCYON BIO
            PROTEIN ENGINEERING · CLINICAL-STAGE
            Farid Lindqvist
            CHIEF SCIENTIFIC OFFICER
            DIRECT
            farid.lindqvist@halcyonbio.de
            +49 30 5589 2140
            linkedin.com/in/farid-lindqvist
            www.halcyonbio.de
            Chausseestraße 111
            10115 Berlin, Germany
            """.trimIndent()
        )

        assertEquals("Farid Lindqvist", parsed.name.value)
        assertEquals("HALCYON BIO", parsed.company.value)
        // Previously returned "PROTEIN ENGINEERING" because "engineering" contains "engineer"
        assertEquals("CHIEF SCIENTIFIC OFFICER", parsed.role.value)
        assertEquals("Chausseestraße 111\n10115 Berlin, Germany", parsed.address.value)
    }

    @Test
    fun `card 3 - Verrand - diacritics and partial domain match`() {
        val parsed = FieldParser.parseText(
            """
            VERRAND
            ARCHITECTURE & INTERIORS · EST. 2009
            Sofía Nakamura
            HEAD OF DESIGN
            sofia.nakamura@studioverrand.com
            +65 8123 4487
            linkedin.com/in/sofia-nakamura
            www.studioverrand.com
            27 Keong Saik Road, #04-02
            Singapore 089136
            """.trimIndent()
        )

        // "Sofía" must match the un-accented "sofia" in the email local part
        assertEquals("Sofía Nakamura", parsed.name.value)
        // "VERRAND" is only part of the domain "studioverrand"
        assertEquals("VERRAND", parsed.company.value)
        assertEquals("HEAD OF DESIGN", parsed.role.value)
        assertEquals("27 Keong Saik Road, #04-02\nSingapore 089136", parsed.address.value)
    }

    @Test
    fun `card 4 - Numen - hyphenated surname and short company inside domain`() {
        val parsed = FieldParser.parseText(
            """
            NUMEN
            HOSPITALITY GROUP — HOTELS & RESIDENCES
            Jonas Al-Rashid
            DIRECTOR OF GUEST EXPERIENCE
            CONTACT
            jonas.alrashid@numenhospitality.ae
            +971 50 662 1148
            linkedin.com/in/jonas-alrashid
            OFFICE
            Emaar Square, Building 4, Unit 1204
            Downtown Dubai, UAE
            www.numenhospitality.ae
            """.trimIndent()
        )

        // Card spells it "Al-Rashid"; the email has "alrashid"
        assertEquals("Jonas Al-Rashid", parsed.name.value)
        assertEquals("NUMEN", parsed.company.value)
        assertEquals("DIRECTOR OF GUEST EXPERIENCE", parsed.role.value)
        assertEquals(
            "Emaar Square, Building 4, Unit 1204\nDowntown Dubai, UAE",
            parsed.address.value
        )
    }

    @Test
    fun `card 5 - Tessellate - tolerates a single-character OCR error in the name`() {
        val parsed = FieldParser.parseText(
            """
            EW
            TESSELLATE
            EMBEDDED PAYMENTS INFRASTRUCTURE
            Elena Nhitfield-Park
            CHIEF PRODUCT OFFICER
            elena.whitfield-park@tessellate.io
            +1 415 555 0184
            linkedin.com/in/elena-whitfield-park
            340 Bryant Street, Suite 210
            San Francisco, CA 94107, USA
            www.tessellate.io
            """.trimIndent()
        )

        // OCR read "Whitfield" as "Nhitfield" — one edit away, so it should still match
        assertEquals("Elena Nhitfield-Park", parsed.name.value)
        assertEquals("TESSELLATE", parsed.company.value)
        assertEquals("CHIEF PRODUCT OFFICER", parsed.role.value)
        assertEquals(
            "340 Bryant Street, Suite 210\nSan Francisco, CA 94107, USA",
            parsed.address.value
        )
    }

    @Test
    fun `surname Park is not mistaken for a street`() {
        // "Park" is a common street word but also this card's surname — the digit requirement
        // and the absence of "park" from the street vocabulary must both hold.
        val parsed = FieldParser.parseText(
            """
            TESSELLATE
            Elena Whitfield-Park
            CHIEF PRODUCT OFFICER
            elena.whitfield-park@tessellate.io
            """.trimIndent()
        )
        assertEquals("Elena Whitfield-Park", parsed.name.value)
        assertNull(parsed.address.value)
    }

    @Test
    fun `role and tagline lines never become the address`() {
        val parsed = FieldParser.parseText(
            """
            ACME PARTNERS
            Head of Community
            EST. 2009 · TRUSTED SINCE
            jane.doe@acmepartners.com
            """.trimIndent()
        )
        assertNull(parsed.address.value)
    }

    // ------------------------------------------------------------------ regression: set two

    @Test
    fun `set2 card 1 - Perihelion - split domain must not win company, phone shares email line`() {
        val parsed = FieldParser.parseText(
            """
            PERIHELION
            RECORDS & PUBLISHING — LISBOA
            Hana Delgado
            A&R DIRECTOR
            hana.delgado@perihelionrecords.pt · +351 91 284 7710
            linkedin.com/in/hana-delgado · www.perihelionrecords. pt
            Rua da Boavista 84, 1200-069 Lisbon, Portugal
            """.trimIndent()
        )

        assertEquals("Hana Delgado", parsed.name.value)
        // OCR split the domain as "www.perihelionrecords. pt" — it must not become the company
        assertEquals("PERIHELION", parsed.company.value)
        assertEquals("A&R DIRECTOR", parsed.role.value)
        // Phone sits after the email on the same line; "1200-069" in the address must not win
        assertEquals("+351 91 284 7710", parsed.phone.value)
        assertEquals("Rua da Boavista 84, 1200-069 Lisbon, Portugal", parsed.address.value)
    }

    @Test
    fun `set2 card 2 - Vandergrift - company recovered from website when email is missing`() {
        // OCR lost the email entirely and mangled the vertical spine wordmark
        val parsed = FieldParser.parseText(
            """
            VAND ERG RIFT MARINE
            Aleksander Nwosu
            HEAD OF TERMINAL OPERATIONS
            +31 6 2145 8830
            linkedin.com/in/aleksander-nwosu
            Wilhelminakade 175
            3072 AP Rotterdam, Netherlands
            www.vandergriftmarine.nl
            """.trimIndent()
        )

        assertNull(parsed.email.value)
        // Name still recoverable from the LinkedIn slug alone
        assertEquals("Aleksander Nwosu", parsed.name.value)
        // Website domain stands in for the missing email domain
        assertEquals("VAND ERG RIFT MARINE", parsed.company.value)
        assertEquals("HEAD OF TERMINAL OPERATIONS", parsed.role.value)
        // "Wilhelminakade" is a Dutch glued street name
        assertEquals(
            "Wilhelminakade 175\n3072 AP Rotterdam, Netherlands",
            parsed.address.value
        )
    }

    @Test
    fun `set2 card 3 - Fathom - seal founding year must not join the address`() {
        val parsed = FieldParser.parseText(
            """
            FATHOM
            ROASTERS
            EST. 2014
            SINGLE ORIGIN · MELBOURNE
            Priya Lindholm
            FOUNDER & HEAD ROASTER
            priya@fathomroasters.com.au
            +61 411 872 260
            linkedin.com/in/priya-lindholm · www.fathomroasters.com.au
            14 Bennetts Lane
            Melbourne VIC 3000, Australia
            """.trimIndent()
        )

        assertEquals("Priya Lindholm", parsed.name.value)
        assertEquals("FOUNDER & HEAD ROASTER", parsed.role.value)
        assertEquals("+61 411 872 260", parsed.phone.value)
        // "EST. 2014" previously slipped in as a postal line
        assertEquals("14 Bennetts Lane\nMelbourne VIC 3000, Australia", parsed.address.value)
    }

    @Test
    fun `set2 card 4 - Ravelin - malformed domain line must not win company`() {
        val parsed = FieldParser.parseText(
            """
            RAVELIN
            SECURITY ENGINEERING · INCIDENT RESPONSE
            Ingrid Haddad
            CHIEF INFORMATION SECURITY OFFICER
            ingrid.haddad@ravelinsecurity.ee
            +372 5123 4471
            linkedin.com/in/ingrid-haddad
            ww.ravelinsecurity.ee
            Rotermanni 8
            10111 Tallinn, Estonia
            """.trimIndent()
        )

        assertEquals("Ingrid Haddad", parsed.name.value)
        // OCR dropped a "w" — still a domain line, still not the company
        assertEquals("RAVELIN", parsed.company.value)
        assertEquals("CHIEF INFORMATION SECURITY OFFICER", parsed.role.value)
        assertEquals("+372 5123 4471", parsed.phone.value)
    }

    @Test
    fun `set2 card 5 - Ondol - letterspaced wordmark still matches its domain`() {
        val parsed = FieldParser.parseText(
            """
            OND O L
            CLAYWORKS · CERAMICS & SURFACES
            Camille Oyelaran
            STUDIO DIRECTOR
            camille@ondollayworks.kr
            +82 10 4482 7160
            linkedin.com/in/camilleayelaran
            41 Jahamun-ro 7-gil, Jongno-gu
            Seoul 03044, South Korea
            www.ondolclayworks.kr
            """.trimIndent()
        )

        assertEquals("Camille Oyelaran", parsed.name.value)
        assertEquals("OND O L", parsed.company.value)
        assertEquals("STUDIO DIRECTOR", parsed.role.value)
        assertEquals("+82 10 4482 7160", parsed.phone.value)
    }

    // ------------------------------------------------------------------ regression: set three

    @Test
    fun `set3 Kliprand - phone and website crowded onto address lines`() {
        val parsed = FieldParser.parseText(
            """
            KLIPRAND
            WINE ESTATE · STELLENBOSCH
            Thandiwe Marchetti
            CELLAR MASTER
            thandiwe.marchetti@kliprand.co.za · +27 82 447 1096
            +27 82 447 1096 www.kliprand.co.za
            Blaauwklippen Road R44, Stellenbosch 7600, South Africa
            """.trimIndent()
        )

        assertEquals("Thandiwe Marchetti", parsed.name.value)
        assertEquals("KLIPRAND", parsed.company.value)
        // "Cellar Master" has no obvious title noun beyond "master"
        assertEquals("CELLAR MASTER", parsed.role.value)
        // Previously empty: the phone line had been absorbed into the address
        assertEquals("+27 82 447 1096", parsed.phone.value)
        // Multi-part suffix must survive; previously truncated to ".co"
        assertEquals("https://www.kliprand.co.za", parsed.website.value)
        // The phone+website line must not join the address
        assertEquals(
            "Blaauwklippen Road R44, Stellenbosch 7600, South Africa",
            parsed.address.value
        )
    }

    @Test
    fun `set3 Tinta Alba - wordmark split across two lines`() {
        val parsed = FieldParser.parseText(
            """
            TINTA
            ALBA
            EDICIONES · CDMX
            Noor Beaumont
            EDITORIAL DIRECTOR
            noor.beaumont@tintaalba.mx
            +52 55 4188 2073
            www.tintaalba.mx
            Calle Colima 168, Roma Norte
            06700 Mexico City, Mexico
            """.trimIndent()
        )

        assertEquals("Noor Beaumont", parsed.name.value)
        // A narrow column broke the wordmark; the halves together match the domain exactly
        assertEquals("TINTA ALBA", parsed.company.value)
        assertEquals("EDITORIAL DIRECTOR", parsed.role.value)
        assertEquals(
            "Calle Colima 168, Roma Norte\n06700 Mexico City, Mexico",
            parsed.address.value
        )
    }

    @Test
    fun `set3 Kelda - address keeps its place name when a website shares the line`() {
        val parsed = FieldParser.parseText(
            """
            KELDA
            GEOTHERMAL — DRILLING & DISTRICT HEAT
            Keiko Halloran
            CHIEF OPERATING OFFICER
            +354 692 4417
            linkedin.com/in/keiko-halloran
            Borgartún 27
            105 Reykjavík, Iceland www.keldageo.is
            """.trimIndent()
        )

        assertEquals("Keiko Halloran", parsed.name.value)
        // No email at all — the company stem comes from the website domain
        assertEquals("KELDA", parsed.company.value)
        // The website is stripped out rather than the whole line being dropped
        assertEquals("Borgartún 27\n105 Reykjavík, Iceland", parsed.address.value)
    }

    @Test
    fun `set3 Vireo - french street word`() {
        val parsed = FieldParser.parseText(
            """
            VIREO ROBOTICS
            AUTONOMOUS INSPECTION SYSTEMS
            Andrés Kowalczyk
            HEAD OF AUTONOMY
            a.kowalczyk@vireorobotics.fr
            +33 6 74 21 38 90
            12 Rue de Metz
            31000 Toulouse, France
            """.trimIndent()
        )

        assertEquals("Andrés Kowalczyk", parsed.name.value)
        assertEquals("VIREO ROBOTICS", parsed.company.value)
        assertEquals("12 Rue de Metz\n31000 Toulouse, France", parsed.address.value)
    }

    @Test
    fun `set3 Sable et Roux - swiss glued street name`() {
        val parsed = FieldParser.parseText(
            """
            SABLE & ROUX
            ADVOCATES — CORPORATE & ARBITRATION
            Lars Ferreira
            MANAGING PARTNER
            lars.ferreira@sableroux.ch
            +41 79 421 66 08
            linkedin.com/in/lars-ferreira
            Bahnhofstrasse 52
            8001 Zürich, Switzerland
            www.sableroux.ch
            """.trimIndent()
        )

        assertEquals("Lars Ferreira", parsed.name.value)
        assertEquals("SABLE & ROUX", parsed.company.value)
        assertEquals("MANAGING PARTNER", parsed.role.value)
        assertEquals("Bahnhofstrasse 52\n8001 Zürich, Switzerland", parsed.address.value)
    }

    // ------------------------------------------------------------------ field extraction

    @Test
    fun `swedish business card with name role email phone`() {
        val parsed = FieldParser.parseText(
            """
            Anders Lindqvist
            Head of Product
            anders.lindqvist@nordicsoftware.se
            +46 70 123 45 67
            Nordic Software AB
            """.trimIndent()
        )

        assertEquals("anders.lindqvist@nordicsoftware.se", parsed.email.value)
        assertEquals(FieldConfidence.HIGH, parsed.email.confidence)
        assertNotNull(parsed.phone.value)
        assertEquals("Anders Lindqvist", parsed.name.value)
        assertEquals("Nordic Software AB", parsed.company.value)
        assertTrue(parsed.role.value!!.contains("Head", ignoreCase = true))
    }

    @Test
    fun `card with only email and website - no name found`() {
        val parsed = FieldParser.parseText(
            """
            info@example.com
            https://example.com
            """.trimIndent()
        )

        assertEquals("info@example.com", parsed.email.value)
        assertEquals(FieldConfidence.HIGH, parsed.email.confidence)
        assertNotNull(parsed.website.value)
        assertTrue(parsed.website.value!!.contains("example.com"))
        assertEquals(FieldConfidence.LOW, parsed.name.confidence)
    }

    @Test
    fun `card with linkedin url`() {
        val parsed = FieldParser.parseText(
            """
            Maria Svensson
            CEO
            maria@sthlmventures.se
            https://linkedin.com/in/mariasvensson
            Stockholm Ventures
            """.trimIndent()
        )

        assertNotNull(parsed.linkedin.value)
        assertTrue(parsed.linkedin.value!!.contains("linkedin.com/in/mariasvensson"))
        assertEquals(FieldConfidence.HIGH, parsed.linkedin.confidence)

        // Website may be inferred from the email domain, but must never be the LinkedIn URL
        assertFalse(parsed.website.value.orEmpty().contains("linkedin.com"))

        assertEquals("maria@sthlmventures.se", parsed.email.value)
        // Surname comes from the LinkedIn slug, not the email local part
        assertEquals("Maria Svensson", parsed.name.value)
    }

    @Test
    fun `email regex does not match partial address`() {
        assertNull(FieldParser.parseText("Contact us at support").email.value)
    }

    @Test
    fun `phone not matched from email address`() {
        assertNull(FieldParser.parseText("john@acme.com").phone.value)
    }

    @Test
    fun `freemail address does not become the company`() {
        val parsed = FieldParser.parseText(
            """
            NORTHWIND LABS
            Mira Holmqvist
            FOUNDER & CEO
            miraholmqvist@gmail.com
            linkedin.com/in/miraholmqvist
            """.trimIndent()
        )

        // Name tokens both appear inside the run-together local part
        assertEquals("Mira Holmqvist", parsed.name.value)
        // gmail.com must not be treated as a company domain, and must not become the website
        assertNull(parsed.website.value)
        assertEquals("NORTHWIND LABS", parsed.company.value)
    }
}
