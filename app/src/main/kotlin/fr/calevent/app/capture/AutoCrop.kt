package fr.calevent.app.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import fr.calevent.app.ocr.OcrEngine
import fr.calevent.app.ocr.createOcrEngine
import fr.calevent.core.ShiftCodeTable
import fr.calevent.core.crop.CropSuggestion
import fr.calevent.core.crop.TableFrame
import fr.calevent.core.parse.Box
import fr.calevent.core.parse.Orientation
import fr.calevent.core.parse.RecognizedText
import fr.calevent.core.parse.TextElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Prepares a new photo on the preview, independently of the OCR engine and on the phone only:
 *
 * 1. Orientation (optional): a quick reading of the photo as it is; unless it clearly reads as a schedule, the
 *    central part of the photo is read turned by 0, 180, 90 and 270 degrees, within [ORIENT_BUDGET_MS], and the
 *    orientation where the text reads best wins ([Orientation]).
 * 2. Crop: the ruled table of the schedule is looked for ([TableFrame], on a reduced gray image, checked against the
 *    recognised text); its outer bounds plus a margin are the frame, extended up to the title when text is recognised
 *    just above. Without a credible table, the main block of recognised text is used.
 *
 * Any failure just means "no suggestion".
 */
object AutoCrop {
    /** Long side of the decoded photo the table is searched on ([TableFrame] reduces it to its own work size). */
    private const val GRID_SIDE = 1600

    /** Long side of the reduced bitmap given to the quick OCR pass. */
    private const val OCR_SIDE = 1024

    /** Share of each side of the photo read to compare orientations (its centre, taken from the 1600 px photo). */
    private const val ORIENT_REGION = 0.7f

    /** Time allowed for the orientation readings; the remaining ones are skipped beyond it (slow engines). */
    private const val ORIENT_BUDGET_MS = 1800L

    /**
     * [quarterTurns]: clockwise quarter turns the photo should have (absolute, 0..3), equal to the photo's own when
     * it was not turned. [crop]: frame in the coordinates of the photo turned that way, or null.
     */
    class Suggestion(val quarterTurns: Int, val crop: CropRect?)

    /**
     * Suggestion for [photo] (decoded without its crop); with [orient], the photo may also be turned. Null on error.
     */
    suspend fun suggest(context: Context, photo: CapturedPhoto, orient: Boolean): Suggestion? {
        return try {
            var bitmap = CapturedPhotoStore.loadBitmapOf(photo, maxSide = GRID_SIDE, applyCrop = false)
            try {
                createOcrEngine(context).use { engine ->
                    val started = System.currentTimeMillis()
                    var text = recognize(engine, bitmap)
                    val wholeMs = System.currentTimeMillis() - started
                    var turns = 0
                    if (orient && text != null) {
                        val upright = text
                        val readings = HashMap<Int, RecognizedText>()
                        // The central part has about as many pixels as the reduced photo: same cost to start with.
                        val budget = Budget(wholeMs)
                        turns = Orientation.detect(upright, LocalDate.now(), ShiftCodeTable()) { degrees ->
                            budget.next { readCentre(engine, bitmap, degrees) }?.also { readings[degrees] = it }
                        } / 90
                        if (turns != 0) {
                            val turned = rotated(bitmap, turns * 90)
                            bitmap.recycle()
                            bitmap = turned
                            // The reading that won, placed in the turned photo: enough to check the table and find
                            // text around it; a new full reading only when there is time left.
                            text = if (System.currentTimeMillis() - started + wholeMs <= ORIENT_BUDGET_MS) recognize(engine, bitmap)
                            else readings[turns * 90]?.let { centreInPhoto(it, bitmap.width, bitmap.height) }
                        }
                    }
                    val candidates = withContext(Dispatchers.Default) { findTables(bitmap) }
                    val table = TableFrame.choose(candidates, text)
                    val crop = CropSuggestion.suggest(table, text)?.let { CropRect(it.left, it.top, it.right, it.bottom) }
                    Suggestion((photo.quarterTurns + turns) % 4, crop)
                }
            } finally {
                bitmap.recycle()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Hands out the orientation readings while they fit in [ORIENT_BUDGET_MS] (estimated from the last one). */
    private class Budget(firstEstimateMs: Long) {
        private val start = System.currentTimeMillis()
        private var estimate = firstEstimateMs

        inline fun <T> next(read: () -> T): T? {
            if (System.currentTimeMillis() - start + estimate > ORIENT_BUDGET_MS) return null
            val t0 = System.currentTimeMillis()
            return read().also { estimate = System.currentTimeMillis() - t0 }
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

    /** Quick reading of a reduced copy of [bitmap] (positions are pixels of that copy); null when the engine fails. */
    private suspend fun recognize(engine: OcrEngine, bitmap: Bitmap): RecognizedText? = try {
        val scale = minOf(1.0, OCR_SIDE.toDouble() / max(bitmap.width, bitmap.height))
        val small = if (scale < 1.0) {
            Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * scale).roundToInt()), max(1, (bitmap.height * scale).roundToInt()), true)
        } else bitmap
        try {
            engine.recognizeQuick(small)
        } finally {
            if (small !== bitmap) small.recycle()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Quick reading of the central part of [bitmap] turned clockwise by [degrees]; empty when the engine fails. */
    private suspend fun readCentre(engine: OcrEngine, bitmap: Bitmap, degrees: Int): RecognizedText {
        val w = (bitmap.width * ORIENT_REGION).roundToInt()
        val h = (bitmap.height * ORIENT_REGION).roundToInt()
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val centre = Bitmap.createBitmap(bitmap, (bitmap.width - w) / 2, (bitmap.height - h) / 2, w, h, matrix, true)
        return try {
            engine.recognizeQuick(centre)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            RecognizedText(centre.width, centre.height, emptyList())
        } finally {
            centre.recycle()
        }
    }

    /** [centre] (a reading of the central part of a photo of [width] x [height]) in the photo's coordinates. */
    private fun centreInPhoto(centre: RecognizedText, width: Int, height: Int): RecognizedText {
        val dx = (width - centre.width) / 2.0
        val dy = (height - centre.height) / 2.0
        fun Box.moved() = Box(left + dx, top + dy, right + dx, bottom + dy)
        return RecognizedText(
            width, height,
            centre.lines.map { l -> l.copy(box = l.box.moved(), elements = l.elements.map { e: TextElement -> e.copy(box = e.box.moved()) }) },
        )
    }

    private fun rotated(src: Bitmap, degrees: Int): Bitmap =
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
}
