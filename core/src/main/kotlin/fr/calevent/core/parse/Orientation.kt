package fr.calevent.core.parse

import fr.calevent.core.ShiftCodeTable
import java.time.LocalDate

/**
 * Chooses how to turn a photo upright (0, 90, 180 or 270 degrees clockwise) from quick readings of it in each
 * orientation, before the user crops it. Text read the wrong way round is gibberish to the engine: few times,
 * dates, day names or shift codes, and no slots for the parser. Pure computation, no Android dependency.
 */
object Orientation {
    /** A reading with fewer lines than this is not trusted as upright on its own. */
    const val MIN_LINES = 6

    /** Weight of one readable word against one slot found by the parser. */
    const val READABLE_WEIGHT = 0.1

    /**
     * Another orientation must beat the upright one by this much (three readable words) and by this ratio to be
     * chosen: text read the wrong way round scores near zero, so a real gain is large in ratio.
     */
    const val MIN_GAIN = 0.3
    const val MIN_RATIO = 2.0

    private val TIME = Regex("""^(?:[01]?\d|2[0-4])[hH:.][0-5]\d$""")
    private val DATE = Regex("""^\d{1,2}[/.\-]\d{1,2}(?:[/.\-]\d{2,4})?$""")
    private val CODE = Regex("""^[A-Z]{1,3}\d{1,2}$""")
    private val WORD = Regex("""^\p{L}{3,}$""")

    /**
     * How much [text] looks like a schedule read the right way up: slots and team cells found by the parser
     * (nothing when the parser itself finds the text turned), plus [READABLE_WEIGHT] per readable word.
     */
    fun score(text: RecognizedText, today: LocalDate, codes: ShiftCodeTable = ShiftCodeTable()): Double {
        val r = ScheduleParser.parse(text, today, codes)
        val parsed = if (r.rotationDegrees != 0) 0.0 else r.slots.size + (r.team?.cells?.size ?: 0) + r.kindScore
        return parsed + READABLE_WEIGHT * readableWords(text)
    }

    /**
     * True when the upright reading [text] is clearly right, so the other orientations need not be read: the
     * parser finds slots without turning it and enough lines were read.
     */
    fun clearlyUpright(text: RecognizedText, today: LocalDate, codes: ShiftCodeTable = ShiftCodeTable()): Boolean {
        if (text.lines.size < MIN_LINES) return false
        val r = ScheduleParser.parse(text, today, codes)
        return r.rotationDegrees == 0 && r.slots.isNotEmpty() &&
            ParseWarning.NO_SLOTS !in r.warnings && ParseWarning.UPSIDE_DOWN !in r.warnings &&
            ParseWarning.ROTATED_QUARTER_TURN !in r.warnings
    }

    /**
     * Clockwise rotation (0, 90, 180, 270) that turns the photo upright. [upright] is a quick reading of the photo
     * as it is; when it is not [clearlyUpright], [read] gives a reading of the photo (or of its central part, the
     * same part every time) turned by the given degrees, 0 included, or null when it is skipped (time budget):
     * the orientations read are scored ([score]) and compared ([choose]).
     */
    inline fun detect(upright: RecognizedText, today: LocalDate, codes: ShiftCodeTable, read: (Int) -> RecognizedText?): Int {
        if (clearlyUpright(upright, today, codes)) return 0
        val scores = HashMap<Int, Double>()
        // Upside down first: the engine reports quarter turns itself more often than half turns.
        for (degrees in intArrayOf(0, 180, 90, 270)) {
            val text = read(degrees) ?: break
            scores[degrees] = score(text, today, codes)
        }
        return choose(scores)
    }

    /** The orientation to apply given the [scores] per clockwise rotation; ties and small gains keep 0. */
    fun choose(scores: Map<Int, Double>): Int {
        val upright = scores[0] ?: 0.0
        val best = scores.filterKeys { it != 0 }.maxByOrNull { it.value } ?: return 0
        return if (best.value >= upright + MIN_GAIN && best.value >= upright * MIN_RATIO) best.key else 0
    }

    /** Words that read as times, dates, day or month names, shift codes, or confident words, weighted by confidence. */
    fun readableWords(text: RecognizedText): Double = text.lines.sumOf { line ->
        val words = line.elements.ifEmpty { line.text.split(' ').filter { it.isNotBlank() }.map { TextElement(it, line.box, line.confidence) } }
        words.sumOf { e ->
            val w = e.text.trim().trimEnd('.', ',', ';', ':')
            val conf = (e.confidence ?: line.confidence ?: 1.0).coerceIn(0.0, 1.0)
            val norm = normWord(w)
            val weight = when {
                TIME.matches(w) || DATE.matches(w) -> 1.0
                norm in WEEKDAY_WORDS || norm in MONTH_WORDS -> 1.0
                CODE.matches(w) -> 0.5
                WORD.matches(w) && conf >= 0.8 -> 0.3
                else -> 0.0
            }
            weight * conf
        }
    }
}
