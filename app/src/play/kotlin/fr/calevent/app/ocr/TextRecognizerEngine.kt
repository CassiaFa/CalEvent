package fr.calevent.app.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import fr.calevent.core.parse.Box
import fr.calevent.core.parse.RecognizedText
import fr.calevent.core.parse.TextElement
import fr.calevent.core.parse.TextLine
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device text recognition with ML Kit's Latin model bundled in the APK: no download, no network.
 * One instance can process several images; [close] it when done (e.g. in `ViewModel.onCleared`).
 */
internal class TextRecognizerEngine : OcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Recognizes the text of [bitmap]. [rotationDegrees] (0, 90, 180 or 270) is the clockwise
     * rotation that makes the image upright, e.g. CameraX `imageInfo.rotationDegrees`. Returned
     * coordinates are pixels of the upright image. Cancelling the coroutine drops the result.
     */
    override suspend fun recognize(bitmap: Bitmap, rotationDegrees: Int): RecognizedText {
        val image = InputImage.fromBitmap(bitmap, rotationDegrees)
        val text = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
        val quarterTurn = rotationDegrees % 180 != 0
        return text.toRecognizedText(
            width = if (quarterTurn) bitmap.height else bitmap.width,
            height = if (quarterTurn) bitmap.width else bitmap.height,
        )
    }

    override fun close() = recognizer.close()
}

/** Converts ML Kit's result into the engine-independent form used by `:core`. */
internal fun Text.toRecognizedText(width: Int, height: Int): RecognizedText = RecognizedText(
    width = width,
    height = height,
    lines = textBlocks.flatMap { block ->
        block.lines.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            TextLine(
                text = line.text,
                box = box.toBox(),
                confidence = line.confidence.toConfidence(),
                angle = line.angle.toDouble(),
                elements = line.elements.mapNotNull { e ->
                    e.boundingBox?.let { TextElement(e.text, it.toBox(), e.confidence.toConfidence()) }
                },
            )
        }
    },
)

private fun Rect.toBox() = Box(left.toDouble(), top.toDouble(), right.toDouble(), bottom.toDouble())

/** ML Kit reports 0 when it has no confidence value: treat it as unknown. */
private fun Float.toConfidence(): Double? = toDouble().takeIf { it > 0.0 && it <= 1.0 }
