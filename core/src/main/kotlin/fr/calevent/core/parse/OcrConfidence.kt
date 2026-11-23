package fr.calevent.core.parse

import kotlin.math.min

/**
 * How the confidences of the tokens of one slot become the slot's OCR confidence (before the doubt
 * factors of [Evidence]).
 *
 * Calibrated on real photos (see the local `OcrConfidenceCalibration` test): word confidences of both engines
 * are conservative (ML Kit: median 0.79 on correct text, Tesseract similar), so a plain minimum put almost
 * every correct slot in "À revoir". The rule is:
 * - the mean of the token confidences, never more than [MAX_ABOVE_WEAKEST] above the weakest token (one very
 *   doubtful word is not drowned by its neighbours);
 * - multiplied by [SCALE], so a raw 0.67 reads as 0.8 ("Retenu") while clear misreads (raw 0.2 to 0.5) stay far
 *   below.
 */
internal object OcrConfidence {
    const val SCALE = 1.2
    const val MAX_ABOVE_WEAKEST = 0.10

    fun calibrate(confidences: List<Double>): Double =
        min(confidences.average(), confidences.min() + MAX_ABOVE_WEAKEST) * SCALE

    /** Replaced by the local calibration test to compare rules; production code never changes it. */
    var aggregate: (List<Double>) -> Double = ::calibrate

    fun of(tokens: List<Token>): Double =
        if (tokens.isEmpty()) DEFAULT_OCR_CONFIDENCE else aggregate(tokens.map { it.confidence }).coerceIn(0.0, 1.0)
}
