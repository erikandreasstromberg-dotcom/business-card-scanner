package com.houseofsilver.cardscanner.ui.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseofsilver.cardscanner.data.local.ScanEntity
import com.houseofsilver.cardscanner.di.AppModule
import com.houseofsilver.cardscanner.domain.model.ScanStatus
import com.houseofsilver.cardscanner.ocr.FieldParser
import com.houseofsilver.cardscanner.qr.CodeParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

sealed class CaptureUiState {
    object Idle : CaptureUiState()
    object Processing : CaptureUiState()
    data class Success(val scanId: String) : CaptureUiState()
    data class Error(val message: String) : CaptureUiState()
}

class CaptureViewModel(private val appModule: AppModule) : ViewModel() {

    companion object {
        /**
         * The card is scanned sideways: its long edge runs along the phone's long edge, so the
         * capture window is a tall rectangle. 0.63 is the business-card short/long ratio.
         */
        const val CARD_RATIO = 0.63f

        /**
         * Bounds on how much of the frame the window may fill. The height cap is the binding one
         * on tall screens; it leaves clearance at the bottom for the shutter button.
         */
        const val CARD_MAX_WIDTH_FRACTION = 0.86f
        const val CARD_MAX_HEIGHT_FRACTION = 0.70f

        /** Capture-window size for a frame of the given dimensions. Shared with CaptureScreen. */
        fun cardWindowSize(frameWidth: Float, frameHeight: Float): Pair<Float, Float> {
            val byWidth = frameWidth * CARD_MAX_WIDTH_FRACTION
            val byHeight = frameHeight * CARD_MAX_HEIGHT_FRACTION * CARD_RATIO
            val w = minOf(byWidth, byHeight)
            return w to (w / CARD_RATIO)
        }
    }

    private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Idle)
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun processCapture(bitmap: Bitmap, rotationDegrees: Int = 0, context: Context) {
        viewModelScope.launch {
            _uiState.value = CaptureUiState.Processing
            try {
                // Orient to what the user saw, then crop to the on-screen capture window.
                val rotated = rotateBitmap(bitmap, rotationDegrees)
                val cropped = cropToCardRegion(rotated)

                // The card sits sideways in the crop — let OCR figure out which way up.
                val ocrOutput = appModule.ocrProcessor.processBestOf(cropped)
                val ocrContact = FieldParser.toContact(FieldParser.parse(ocrOutput))

                // Same frame, second detector: a QR on the card (or a phone screen held up)
                // carries exact data where OCR can only guess. Fall back to the whole frame
                // if the code sat outside the card window.
                val codes = appModule.barcodeReader.scan(cropped)
                    .ifEmpty { appModule.barcodeReader.scan(rotated) }
                val code = codes.firstNotNullOfOrNull { CodeParser.parse(it) }
                val contact = CodeParser.merge(code, ocrContact)

                // Save the crop turned upright, so history thumbnails show a readable card.
                // ML Kit's rotationDegrees states the rotation the image *already has*, whereas
                // postRotate applies a correction — so it has to be negated, otherwise the
                // thumbnail lands 180° out (the two candidate rotations are 180° apart).
                val upright = rotateBitmap(cropped, -ocrOutput.rotationDegrees)
                val imageUri = saveBitmapToFile(upright, context)

                val scanId = UUID.randomUUID().toString()
                val entity = ScanEntity(
                    id = scanId,
                    contactName = contact.name,
                    contactEmail = contact.email,
                    contactCompany = contact.company,
                    contactRole = contact.role,
                    contactPhone = contact.phone,
                    contactWebsite = contact.website,
                    contactLinkedin = contact.linkedin,
                    contactAddress = contact.address,
                    rawText = listOfNotNull(
                        ocrOutput.rawText.takeIf { it.isNotBlank() },
                        codes.firstOrNull()
                    ).joinToString("\n\n"),
                    ocrConfidence = ocrOutput.confidence,
                    imageUri = imageUri?.toString(),
                    emailStatus = ScanStatus.PENDING.name,
                    emailSentAt = null,
                    emailSubjectSent = null,
                    emailBodySent = null,
                    templateVersion = null,
                    context = null,
                    scannedAt = System.currentTimeMillis(),
                    // Recorded so Review can suppress the low-confidence warning: a decoded
                    // code is exact, and OCR confidence says nothing about it.
                    source = if (code?.precise == true) "qr" else "camera"
                )
                appModule.scanDao.insertScan(entity)

                _uiState.value = CaptureUiState.Success(scanId)
            } catch (e: Exception) {
                _uiState.value = CaptureUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val normalised = ((degrees % 360) + 360) % 360
        if (normalised == 0) return bitmap
        val matrix = Matrix().apply { postRotate(normalised.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Crops to the same tall window drawn on screen, so OCR only sees the card. */
    private fun cropToCardRegion(bitmap: Bitmap): Bitmap {
        val (wF, hF) = cardWindowSize(bitmap.width.toFloat(), bitmap.height.toFloat())
        val cropW = wF.toInt().coerceAtLeast(1)
        val cropH = hF.toInt().coerceAtLeast(1)
        val left = ((bitmap.width - cropW) / 2).coerceAtLeast(0)
        val top = ((bitmap.height - cropH) / 2).coerceAtLeast(0)
        val safeW = cropW.coerceAtMost(bitmap.width - left)
        val safeH = cropH.coerceAtMost(bitmap.height - top)
        return Bitmap.createBitmap(bitmap, left, top, safeW, safeH)
    }

    private fun saveBitmapToFile(bitmap: Bitmap, context: Context): Uri? {
        return try {
            val file = File(context.filesDir, "cards/${UUID.randomUUID()}.jpg")
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            null
        }
    }

    fun resetState() {
        _uiState.value = CaptureUiState.Idle
    }
}
