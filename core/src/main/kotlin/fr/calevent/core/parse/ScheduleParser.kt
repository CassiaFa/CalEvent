package fr.calevent.core.parse

import fr.calevent.core.ShiftCodeTable
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

/**
 * Turns the text recognized on a photo of a paper schedule into slots.
 *
 * Steps: orientation (quarter / half turn) and tilt correction, then each layout interpretation
 * (month grid, dated columns with or without a name column) is scored and the best one wins.
 * Pure and fast: a 31-day × 12-person team schedule takes a few milliseconds.
 */
object ScheduleParser {

    /** Below this score the layout is not trusted and the result is [ScheduleKind.UNKNOWN]. */
    const val MIN_KIND_SCORE = 0.35

    /** Tilt above which the user is told to retake the photo, in degrees. */
    const val MAX_TILT_DEGREES = 8.0

    private const val MIN_TOKENS = 6
    private const val MIN_MEDIAN_CONFIDENCE = 0.6

    /**
     * Parses [text] (pixel coordinates of the photo). [today] anchors years and months that are not
     * written; [codes] tells shift codes apart from other text.
     */
    fun parse(text: RecognizedText, today: LocalDate, codes: ShiftCodeTable = ShiftCodeTable.default()): ParseResult {
        val attempts = candidateRotations(text).map { analyze(text, it, today, codes) }
        val best = attempts.maxWith(compareBy<Attempt>({ it.analysis.score }, { it.analysis.size }))

        val warnings = ArrayList<ParseWarning>()
        when (best.rotation) {
            90, 270 -> warnings += ParseWarning.ROTATED_QUARTER_TURN
            180 -> warnings += ParseWarning.UPSIDE_DOWN
        }
        if (max(abs(best.skew), ocrTiltDegrees(text)) > MAX_TILT_DEGREES) warnings += ParseWarning.TILTED
        if (best.tokenCount < MIN_TOKENS || best.medianConfidence < MIN_MEDIAN_CONFIDENCE) warnings += ParseWarning.LOW_TEXT
        val a = best.analysis
        if (a.size == 0) warnings += ParseWarning.NO_SLOTS

        return ParseResult(
            kind = a.kind, kindScore = a.score, slots = a.slots, team = a.team, people = a.people,
            warnings = warnings, rotationDegrees = best.rotation, skewDegrees = best.skew, cellDetails = a.details,
        )
    }

    private class Attempt(
        val rotation: Int, val skew: Double, val tokenCount: Int, val medianConfidence: Double, val analysis: Analysis,
    )

    private fun analyze(text: RecognizedText, rotation: Int, today: LocalDate, codes: ShiftCodeTable): Attempt {
        val (w, h) = rotatedSize(text, rotation)
        val raw = tokenize(text, rotation)
        val skew = estimateSkewDegrees(raw, w)
        val tokens = deskew(raw, skew, w / 2, h / 2)
        val ctx = Context(text, today, codes, tokens.map { it.box.height }.median().coerceAtLeast(1.0))
        val candidates = listOfNotNull(analyzeWallCalendar(tokens, ctx), analyzeDateGrid(tokens, ctx))
        val best = candidates.maxByOrNull { it.score }?.takeIf { it.score >= MIN_KIND_SCORE }
            ?: Analysis(ScheduleKind.UNKNOWN, candidates.maxOfOrNull { it.score } ?: 0.0)
        return Attempt(rotation, skew, tokens.size, tokens.map { it.confidence }.median(), best)
    }
}
