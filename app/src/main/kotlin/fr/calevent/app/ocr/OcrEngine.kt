package fr.calevent.app.ocr

import android.graphics.Bitmap
import fr.calevent.core.parse.RecognizedText

/**
 * On-device text recognition, independent of the library. The implementation is chosen by the
 * build flavor (`play`: ML Kit, `foss`: Tesseract) through [createOcrEngine]. Nothing leaves the phone.
 * One instance can process several images; [close] it when done.
 */
interface OcrEngine : AutoCloseable {
    /**
     * Recognizes the text of [bitmap]. [rotationDegrees] (0, 90, 180 or 270) is the clockwise
     * rotation that makes the image upright, e.g. CameraX `imageInfo.rotationDegrees`. Returned
     * coordinates are pixels of the upright image (full size of [bitmap] once rotated).
     */
    suspend fun recognize(bitmap: Bitmap, rotationDegrees: Int = 0): RecognizedText
}
