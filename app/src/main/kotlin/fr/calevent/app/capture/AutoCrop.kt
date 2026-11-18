package fr.calevent.app.capture

import android.content.Context
import fr.calevent.app.ocr.TextRecognizerEngine
import fr.calevent.core.crop.TextBounds
import kotlinx.coroutines.CancellationException

/**
 * Suggests a crop of the current photo from a quick on-device OCR pass on a reduced bitmap.
 * Nothing leaves the phone; any failure just means "no suggestion".
 */
object AutoCrop {
    private const val MAX_SIDE = 1024

    /** Suggested crop in displayed coordinates for the current photo, or null (none found or error). */
    suspend fun suggest(context: Context): CropRect? {
        return try {
            val bitmap = CapturedPhotoStore.loadBitmap(context, maxSide = MAX_SIDE, applyCrop = false)
            try {
                val text = TextRecognizerEngine().use { it.recognize(bitmap) }
                TextBounds.suggestCrop(text)?.let { CropRect(it.left, it.top, it.right, it.bottom) }
            } finally {
                bitmap.recycle()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
