package com.houseofsilver.cardscanner.domain.model

import android.net.Uri

data class ScanResult(
    val id: String,
    val contact: Contact,
    val rawText: String,
    val confidence: Float,
    val imageUri: Uri?,
    val status: ScanStatus,
    val scannedAt: Long,
    val emailSentAt: Long? = null,
    val emailStatus: String? = null,
    val emailSubjectSent: String? = null,
    val emailBodySent: String? = null,
    val templateVersion: Int? = null,
    val context: String? = null
)

/** SAVED covers contacts recorded without an email — a LinkedIn QR, say — where no draft exists. */
enum class ScanStatus { PENDING, SENT, SAVED, FAILED, FALLBACK_TO_SELF, CANCELLED }
