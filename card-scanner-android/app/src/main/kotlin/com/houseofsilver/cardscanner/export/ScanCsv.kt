package com.houseofsilver.cardscanner.export

import com.houseofsilver.cardscanner.data.local.ScanEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders scans as CSV for import into a CRM.
 *
 * Every cell is quoted unconditionally — simpler than deciding per value, and it keeps
 * commas, quotes and stray separators in OCR'd text from shifting columns. Embedded newlines
 * (addresses are multi-line) collapse to ", " so each record stays on one row, which is what
 * spreadsheet importers cope with most reliably.
 */
object ScanCsv {

    private val HEADERS = listOf(
        "Name", "Email", "Company", "Role", "Phone",
        "Website", "LinkedIn", "Address", "Note", "Scanned"
    )

    private const val ROW_SEPARATOR = "\r\n"

    fun build(scans: List<ScanEntity>): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder()
        sb.append(HEADERS.joinToString(",") { cell(it) }).append(ROW_SEPARATOR)
        for (scan in scans) {
            sb.append(
                listOf(
                    scan.contactName,
                    scan.contactEmail,
                    scan.contactCompany,
                    scan.contactRole,
                    scan.contactPhone,
                    scan.contactWebsite,
                    scan.contactLinkedin,
                    scan.contactAddress,
                    scan.context,
                    stamp.format(Date(scan.scannedAt))
                ).joinToString(",") { cell(it) }
            ).append(ROW_SEPARATOR)
        }
        return sb.toString()
    }

    fun fileName(count: Int, now: Long): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        return "card-scans-$stamp-$count.csv"
    }

    private fun cell(value: String?): String {
        // Normalise line endings first, then collapse them — addresses are stored with a bare \n
        val flattened = (value ?: "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\n", ", ")
            .replace(Regex(" {2,}"), " ")
            .trim()
        return "\"" + flattened.replace("\"", "\"\"") + "\""
    }
}
