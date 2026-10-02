package com.houseofsilver.cardscanner

import com.houseofsilver.cardscanner.data.local.ScanEntity
import com.houseofsilver.cardscanner.export.ScanCsv
import org.junit.Assert.*
import org.junit.Test

class ScanCsvTest {

    private fun scan(
        name: String = "Annika Reyes",
        email: String = "annika.reyes@vantageroe.com",
        company: String? = "Vantage & Roe",
        address: String? = null,
        note: String? = null
    ) = ScanEntity(
        id = "id-1",
        contactName = name,
        contactEmail = email,
        contactCompany = company,
        contactRole = "Senior Consultant",
        contactPhone = "+46 70 412 88 05",
        contactWebsite = "https://vantageroe.com",
        contactLinkedin = "linkedin.com/in/annika-reyes",
        contactAddress = address,
        rawText = "",
        ocrConfidence = 0.9f,
        imageUri = null,
        emailStatus = "SENT",
        emailSentAt = 0L,
        emailSubjectSent = null,
        emailBodySent = null,
        templateVersion = 1,
        context = note,
        scannedAt = 0L
    )

    @Test
    fun `header row is present and quoted`() {
        val csv = ScanCsv.build(listOf(scan()))
        assertTrue(csv.startsWith("\"Name\",\"Email\",\"Company\""))
    }

    @Test
    fun `one row per scan, CRLF terminated`() {
        val csv = ScanCsv.build(listOf(scan(), scan(name = "Farid Lindqvist")))
        // header + 2 records
        assertEquals(3, csv.split("\r\n").filter { it.isNotBlank() }.size)
    }

    @Test
    fun `multiline address collapses onto a single row`() {
        val csv = ScanCsv.build(
            listOf(scan(address = "Birger Jarlsgatan 24\n114 34 Stockholm, Sweden"))
        )
        assertTrue(csv.contains("\"Birger Jarlsgatan 24, 114 34 Stockholm, Sweden\""))
        assertEquals(2, csv.split("\r\n").filter { it.isNotBlank() }.size)
    }

    @Test
    fun `embedded quotes are doubled so columns do not shift`() {
        val csv = ScanCsv.build(listOf(scan(company = "The \"Big\" Agency")))
        assertTrue(csv.contains("\"The \"\"Big\"\" Agency\""))
    }

    @Test
    fun `commas in values stay inside their column`() {
        val csv = ScanCsv.build(listOf(scan(note = "Met at Slush, follow up in Q1")))
        assertTrue(csv.contains("\"Met at Slush, follow up in Q1\""))
    }

    @Test
    fun `null fields render as empty cells`() {
        val csv = ScanCsv.build(listOf(scan(company = null, address = null, note = null)))
        assertTrue(csv.contains("\"\""))
    }

    @Test
    fun `file name carries the scan count`() {
        assertTrue(ScanCsv.fileName(3, 0L).endsWith("-3.csv"))
        assertTrue(ScanCsv.fileName(3, 0L).startsWith("card-scans-"))
    }
}
