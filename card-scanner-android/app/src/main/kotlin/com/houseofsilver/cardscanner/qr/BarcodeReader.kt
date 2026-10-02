package com.houseofsilver.cardscanner.qr

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Reads 2D codes from a captured frame.
 *
 * No rotation handling here, unlike the OCR path: QR and Data Matrix carry their own orientation
 * markers, so the detector finds them whichever way the card is held.
 */
class BarcodeReader {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_AZTEC
            )
            .build()
    )

    suspend fun scan(bitmap: Bitmap): List<String> = suspendCancellableCoroutine { cont ->
        scanner.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { codes ->
                cont.resume(codes.mapNotNull { it.rawValue }.filter { it.isNotBlank() })
            }
            // A frame with no code is the normal case, not a failure worth propagating
            .addOnFailureListener { cont.resume(emptyList()) }
    }

    fun close() {
        scanner.close()
    }
}
