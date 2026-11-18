package fr.calevent.app.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import fr.calevent.app.capture.CapturedPhotoStore
import fr.calevent.app.ocr.OcrDebugDump
import fr.calevent.app.ocr.TextRecognizerEngine
import fr.calevent.core.ShiftCodeTable
import fr.calevent.core.parse.ParseResult
import fr.calevent.core.parse.ParseWarning
import fr.calevent.core.parse.RecognizedText
import fr.calevent.core.parse.ScheduleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * What the pipeline produced. [bitmap] is the image the text was read on: slot regions are fractions
 * of it, so it is the one to display (it may be the photo turned by a multiple of 90 degrees).
 */
class AnalysisOutput(val result: ParseResult, val bitmap: Bitmap, val lineCount: Int)

/** Pipeline steps reported to the UI. */
object AnalysisStep {
    const val DETECT = 0
    const val READ = 1
    const val STRUCTURE = 2
}

/**
 * Photo -> text -> slots, fully on the device. Detection (load the photo), Reading (ML Kit),
 * Structuring (the pure `:core` parser).
 *
 * Orientation: the text is first read upright (0 degrees). When the result looks wrong (the parser
 * reports a rotation, the text is scarce, or nothing was found) the image is turned by 90, 180 and
 * 270 degrees and read again; the attempt with the most slots (then the best layout score) wins.
 * Ties keep the earlier attempt, so an upright photo is never turned for nothing.
 */
class ScanAnalyzer {

    suspend fun analyze(
        context: Context,
        codes: ShiftCodeTable,
        today: LocalDate = LocalDate.now(),
        onStep: (Int) -> Unit,
    ): AnalysisOutput = withContext(Dispatchers.Default) {
        onStep(AnalysisStep.DETECT)
        val original = minimumStep { CapturedPhotoStore.loadBitmap(context) }

        onStep(AnalysisStep.READ)
        TextRecognizerEngine().use { engine ->
            val started = System.currentTimeMillis()
            var best = Attempt(0, original, engine.recognize(original), null)
            best = best.copy(result = ScheduleParser.parse(best.text, today, codes))
            if (needsOtherOrientations(best)) {
                for (degrees in intArrayOf(90, 180, 270)) {
                    currentCoroutineContext().ensureActive()
                    val turned = rotated(original, degrees)
                    val text = engine.recognize(turned)
                    val attempt = Attempt(degrees, turned, text, ScheduleParser.parse(text, today, codes))
                    if (attempt.score > best.score) {
                        if (best.bitmap !== original) best.bitmap.recycle()
                        best = attempt
                    } else turned.recycle()
                }
            }
            if (best.bitmap !== original) original.recycle()
            padTo(started)

            onStep(AnalysisStep.STRUCTURE)
            val structStart = System.currentTimeMillis()
            val result = best.result!!
            OcrDebugDump.write(context, best.text)
            padTo(structStart)
            AnalysisOutput(result, best.bitmap, best.text.lines.size)
        }
    }

    private class Attempt(val degrees: Int, val bitmap: Bitmap, val text: RecognizedText, val result: ParseResult?) {
        /** Slots found, plus the layout score (< 1) as a tie-breaker. */
        val score: Double get() = result?.let { (it.slots.size + (it.team?.cells?.size ?: 0)) + it.kindScore } ?: -1.0

        fun copy(result: ParseResult?) = Attempt(degrees, bitmap, text, result)
    }

    private fun needsOtherOrientations(a: Attempt): Boolean {
        val r = a.result ?: return true
        return r.rotationDegrees != 0 ||
            ParseWarning.ROTATED_QUARTER_TURN in r.warnings || ParseWarning.UPSIDE_DOWN in r.warnings ||
            ParseWarning.NO_SLOTS in r.warnings || a.text.lines.size < MIN_LINES
    }

    private fun rotated(src: Bitmap, degrees: Int): Bitmap =
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)

    /** Runs [block], keeping the step on screen at least [MIN_STEP_MILLIS] so it does not flash. */
    private suspend fun <T> minimumStep(block: suspend () -> T): T {
        val start = System.currentTimeMillis()
        val value = block()
        padTo(start)
        return value
    }

    private suspend fun padTo(start: Long) {
        val left = MIN_STEP_MILLIS - (System.currentTimeMillis() - start)
        if (left > 0) delay(left)
    }

    private companion object {
        const val MIN_STEP_MILLIS = 600L
        const val MIN_LINES = 6
    }
}
