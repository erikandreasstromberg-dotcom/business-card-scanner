package com.houseofsilver.cardscanner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey val id: String,
    val contactName: String,
    val contactEmail: String,
    val contactCompany: String?,
    val contactRole: String?,
    val contactPhone: String?,
    val contactWebsite: String?,
    val contactLinkedin: String?,
    val contactAddress: String?,
    val rawText: String,
    val ocrConfidence: Float,
    val imageUri: String?,
    val emailStatus: String,
    val emailSentAt: Long?,
    val emailSubjectSent: String?,
    val emailBodySent: String?,
    val templateVersion: Int?,
    val context: String?,
    val scannedAt: Long,
    val source: String = "camera"
)
