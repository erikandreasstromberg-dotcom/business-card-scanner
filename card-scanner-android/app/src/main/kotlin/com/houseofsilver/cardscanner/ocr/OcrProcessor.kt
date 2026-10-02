package com.houseofsilver.cardscanner.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrOutput(
    val blocks: List<Text.TextBlock>,
    val confidence: Float,
    val rawText: String,
    /** Clockwise rotation that produced this result, in degrees. */
    val rotationDegrees: Int = 0
)

class OcrProcessor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val emailRegex = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")

    /**
     * Runs recognition at each candidate rotation and returns the best result.
     *
     * Cards are held sideways in the capture frame, but which way the user turns the card is
     * up to them — so we try both 90° rotations and keep whichever reads better rather than
     * guessing. ML Kit rotates internally, so this costs no bitmap copies.
     */
    suspend fun processBestOf(bitmap: Bitmap, rotations: List<Int> = listOf(90, 270)): OcrOutput {
        var best: OcrOutput? = null
        var bestScore = Int.MIN_VALUE

        for (rotation in rotations) {
            val output = try {
                process(bitmap, rotation)
            } catch (e: Exception) {
                continue
            }
            val score = scoreOf(output)
            if (score > bestScore) {
                bestScore = score
                best = output
            }
        }

        return best ?: OcrOutput(emptyList(), 0f, "", rotations.firstOrNull() ?: 0)
    }

    suspend fun process(bitmap: Bitmap, rotationDegrees: Int = 0): OcrOutput {
        val image = InputImage.fromBitmap(bitmap, rotationDegrees)
        val visionText = runRecognition(image)

        val blocks = visionText.textBlocks
        val allConfidences = blocks
            .flatMap { block -> block.lines.flatMap { line -> line.elements } }
            .mapNotNull { element -> element.confidence }

        return OcrOutput(
            blocks = blocks,
            confidence = if (allConfidences.isNotEmpty()) allConfidences.average().toFloat() else 0f,
            rawText = visionText.text,
            rotationDegrees = rotationDegrees
        )
    }

    /**
     * Upright text reads as real words, so the wrong rotation yields far less recognised text
     * and rarely produces a valid email address. Weight an email hit heavily — it's the single
     * most load-bearing field downstream.
     */
    private fun scoreOf(output: OcrOutput): Int {
        var score = output.rawText.length
        if (emailRegex.containsMatchIn(output.rawText)) score += 500
        score += (output.confidence * 100).toInt()
        return score
    }

    private suspend fun runRecognition(image: InputImage): Text =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result -> cont.resume(result) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    fun close() {
        recognizer.close()
    }
}
