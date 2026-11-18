package fr.calevent.core.parse

import fr.calevent.core.ShiftCodeTable
import fr.calevent.core.Slot
import fr.calevent.core.SlotStatus
import fr.calevent.core.TeamSchedule

internal const val REASON_LOW_OCR = "Texte peu lisible"
internal const val REASON_TIME_CORRECTED = "Heure peut-être mal lue"
internal const val REASON_END_UNCERTAIN = "Heure de fin incertaine"
internal const val REASON_NO_TIME = "Aucune heure lue : journée entière"
internal const val REASON_NO_TITLE = "Intitulé illisible"
internal const val REASON_POSITION = "Position dans le tableau incertaine"
internal const val REASON_AMBIGUOUS_CELL = "Plusieurs textes dans la même case"
internal const val REASON_CODE_CORRECTED = "Code peut-être mal lu"

/** Title of a team-hours slot whose cell only holds times. */
internal const val DEFAULT_WORK_TITLE = "Travail"
internal const val DEFAULT_EVENT_TITLE = "Événement"

/**
 * Explainable confidence: OCR confidence multiplied by one factor per doubt, each with its
 * French reason. The first reason is the one shown to the user.
 */
internal class Evidence(private val ocr: Double) {
    private var factor = 1.0
    private val reasons = ArrayList<String>()

    fun penalize(f: Double, reason: String): Evidence {
        factor *= f
        if (reason !in reasons) reasons += reason
        return this
    }

    val confidence: Double get() = (ocr * factor).coerceIn(0.0, 1.0)

    fun reason(dateReason: String?): String? =
        dateReason ?: reasons.firstOrNull() ?: REASON_LOW_OCR.takeIf { ocr < SlotStatus.KEPT_THRESHOLD }
}

/** Result of analysing the layout with one interpretation. */
internal class Analysis(
    val kind: ScheduleKind,
    val score: Double,
    val slots: List<Slot> = emptyList(),
    val team: TeamSchedule? = null,
    val people: List<String> = emptyList(),
    val details: Map<String, CellDetail> = emptyMap(),
) {
    val size: Int get() = slots.size + (team?.cells?.size ?: 0)
}

/** Image and settings shared by the analyzers. */
internal class Context(
    val image: RecognizedText,
    val today: java.time.LocalDate,
    val codes: ShiftCodeTable,
    /** Median token height in the working frame. */
    val lineHeight: Double,
)

/**
 * Builds the slots of one cell or event text: one slot per time range, or an all-day slot when no
 * time is read. [team]: a team-hours cell (the text is a place, every shift should have an end).
 */
internal fun buildSlots(
    id: String,
    text: String,
    tokens: List<Token>,
    date: ColumnDate,
    ctx: Context,
    person: String? = null,
    team: Boolean = false,
    penalties: List<Pair<Double, String>> = emptyList(),
): List<Slot> {
    val day = date.date ?: return emptyList()
    val timed = parseTimes(text)
    val ocr = tokens.minOfOrNull { it.confidence } ?: DEFAULT_OCR_CONFIDENCE
    val region = regionOf(tokens, ctx.image.width, ctx.image.height)
    val rest = timed.rest
    val title = rest.ifEmpty { if (team) DEFAULT_WORK_TITLE else DEFAULT_EVENT_TITLE }

    fun make(index: Int, range: TimeRange?): Slot {
        val ev = Evidence(ocr)
        penalties.forEach { (f, r) -> ev.penalize(f, r) }
        if (timed.corrected) ev.penalize(0.9, REASON_TIME_CORRECTED)
        if (range == null) ev.penalize(0.75, REASON_NO_TIME)
        else if (range.end == null && (timed.missingEnd || team)) ev.penalize(0.75, REASON_END_UNCERTAIN)
        if (rest.isEmpty() && !team) ev.penalize(0.85, REASON_NO_TITLE)
        val slot = Slot(
            id = "$id#$index",
            title = title,
            date = day,
            start = range?.start,
            end = range?.end,
            allDay = range == null,
            endsNextDay = range?.endsNextDay ?: false,
            location = rest.takeIf { team && it.isNotEmpty() },
            person = person,
            sourceText = text,
            confidence = ev.confidence,
            dateUncertain = date.uncertain,
            region = region,
        )
        return slot.copy(reviewReason = ev.reason(date.reason.takeIf { date.uncertain }).takeIf { slot.status != SlotStatus.KEPT })
    }
    return if (timed.ranges.isEmpty()) listOf(make(0, null)) else timed.ranges.mapIndexed(::make)
}

private val CODE_SHAPE = Regex("""^[A-Z]{1,4}\d{0,2}$""")
private val LETTER_DIGITS = Regex("""^([A-Za-z])([0-9OolI]{2})$""")

/** Normalizes a code read by OCR ("MO6" -> "M06"); second: whether a correction was made. */
internal fun fixCode(raw: String, codes: ShiftCodeTable): Pair<String, Boolean> {
    val t = raw.trim().replace(Regex("\\s+"), "")
    if (codes.find(t) != null) return t.uppercase() to false
    val m = LETTER_DIGITS.matchEntire(t)
    if (m != null && m.groupValues[2].any { it.isDigit() }) {
        val fixed = m.groupValues[1].uppercase() + fixNumber(m.groupValues[2])!!.toString().padStart(2, '0')
        return fixed to (fixed != t)
    }
    return (if (t.length <= 5) t.uppercase() else raw.trim()) to false
}

/** True when the text looks like a shift code (known in the table, or short upper-case letters + digits). */
internal fun looksLikeCode(text: String, codes: ShiftCodeTable): Boolean {
    val (code, _) = fixCode(text, codes)
    return codes.find(code) != null || (CODE_SHAPE.matches(code) && text.trim().length <= 5 && text.trim() == text.trim().uppercase())
}

/** Index of the value of [xs] closest to [x]. */
internal fun nearestIndex(xs: List<Double>, x: Double): Int = xs.indices.minBy { kotlin.math.abs(xs[it] - x) }
