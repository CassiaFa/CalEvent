package fr.calevent.app.capture

import android.content.Context
import android.graphics.Bitmap
import fr.calevent.app.ocr.createOcrEngine
import fr.calevent.core.crop.CropSuggestion
import fr.calevent.core.crop.TableFrame
import fr.calevent.core.parse.RecognizedText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Suggests a crop of a photo, independently of the OCR engine: the ruled table of the schedule is looked for first
 * ([TableFrame], on a reduced gray image, checked against the text recognised by a quick OCR pass); its outer bounds
 * plus a margin are the frame, extended up to the title when text is recognised just above. Without a credible
 * table, the main block of recognised text is used. Nothing leaves the phone; any failure just means "no suggestion".
 */
object AutoCrop {
    /** Long side of the decoded photo the table is searched on ([TableFrame] reduces it to its own work size). */
    private const val GRID_SIDE = 1600

    /** Long side of the reduced bitmap given to the quick OCR pass. */
    private const val OCR_SIDE = 1024

    /** Suggested crop in displayed coordinates for [photo] (decoded without its crop), or null. */
    suspend fun suggest(context: Context, photo: CapturedPhoto): CropRect? {
        return try {
            val bitmap = CapturedPhotoStore.loadBitmapOf(photo, maxSide = GRID_SIDE, applyCrop = false)
            try {
                val candidates = withContext(Dispatchers.Default) { findTables(bitmap) }
                val text = recognize(context, bitmap)
                val table = TableFrame.choose(candidates, text)
                CropSuggestion.suggest(table, text)?.let { CropRect(it.left, it.top, it.right, it.bottom) }
            } finally {
                bitmap.recycle()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Candidate tables of the photo ([TableFrame.candidates]); empty on error. */
    private fun findTables(bitmap: Bitmap): List<TableFrame.Candidate> = try {
        val w = bitmap.width
        val h = bitmap.height
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)
        val gray = IntArray(argb.size) { i ->
            val p = argb[i]
            (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        }
        TableFrame.candidates(gray, w, h)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    /** Text of a quick OCR pass on a reduced copy (positions only are used); null when the engine fails. */
    private suspend fun recognize(context: Context, bitmap: Bitmap): RecognizedText? = try {
        val scale = minOf(1.0, OCR_SIDE.toDouble() / max(bitmap.width, bitmap.height))
        val small = if (scale < 1.0) {
            Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * scale).roundToInt()), max(1, (bitmap.height * scale).roundToInt()), true)
        } else bitmap
        try {
            createOcrEngine(context).use { it.recognize(small) }
        } finally {
            if (small !== bitmap) small.recycle()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
