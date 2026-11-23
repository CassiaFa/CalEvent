package fr.calevent.app.capture

import android.content.Context
import android.graphics.Bitmap
import fr.calevent.app.ocr.createOcrEngine
import fr.calevent.core.crop.CropSuggestion
import fr.calevent.core.crop.NormalizedRect
import fr.calevent.core.crop.TextBounds
import fr.calevent.core.image.OcrPreprocess
import fr.calevent.core.image.TableGrid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Suggests a crop of a photo, independently of the OCR engine: the ruled table of the schedule is looked for first
 * (same grid detector as the Tesseract pipeline, on a reduced gray image); its outer bounds plus a margin are the
 * frame, extended up to the title when text is recognised there. Without a grid, the main block of recognised text
 * is used. Nothing leaves the phone; any failure just means "no suggestion".
 */
object AutoCrop {
    /** Long side of the image the grid is searched on (the detector's lengths are ratios of it). */
    private const val GRID_SIDE = 1600

    /** Long side of the reduced bitmap given to the quick OCR pass. */
    private const val OCR_SIDE = 1024

    /** Suggested crop in displayed coordinates for [photo] (decoded without its crop), or null. */
    suspend fun suggest(context: Context, photo: CapturedPhoto): CropRect? {
        return try {
            val bitmap = CapturedPhotoStore.loadBitmapOf(photo, maxSide = GRID_SIDE, applyCrop = false)
            try {
                val grid = withContext(Dispatchers.Default) { findGrid(bitmap) }
                val text = recognizeBounds(context, bitmap)
                CropSuggestion.suggest(grid, bitmap.width, bitmap.height, text)?.let { CropRect(it.left, it.top, it.right, it.bottom) }
            } finally {
                bitmap.recycle()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Ruled table of the photo, or null (none found, or error). */
    private fun findGrid(bitmap: Bitmap): TableGrid? = try {
        val w = bitmap.width
        val h = bitmap.height
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)
        val gray = IntArray(argb.size) { i ->
            val p = argb[i]
            (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        }
        OcrPreprocess.prepare(gray, w, h).grid
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Text bounds from a quick OCR pass on a reduced copy; null when the engine fails or finds nothing. */
    private suspend fun recognizeBounds(context: Context, bitmap: Bitmap): NormalizedRect? = try {
        val scale = minOf(1.0, OCR_SIDE.toDouble() / max(bitmap.width, bitmap.height))
        val small = if (scale < 1.0) {
            Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * scale).roundToInt()), max(1, (bitmap.height * scale).roundToInt()), true)
        } else bitmap
        try {
            val text = createOcrEngine(context).use { it.recognize(small) }
            TextBounds.suggestCrop(text)
        } finally {
            if (small !== bitmap) small.recycle()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
