package fr.calevent.core.parse

import java.time.LocalTime

/** A time or time range read in a text. [end] is null when only a start time is written. */
internal data class TimeRange(val start: LocalTime, val end: LocalTime?, val endsNextDay: Boolean)

/**
 * Times found in a text and the remaining words (the title).
 * [corrected]: some letters had to be read as digits ("8h3O"). [missingEnd]: a range with no end ("8h à").
 */
internal data class TimedText(
    val ranges: List<TimeRange>,
    val rest: String,
    val corrected: Boolean,
    val missingEnd: Boolean,
)

/** Letters OCR commonly returns instead of digits. */
private val DIGIT_LOOKALIKES = mapOf('O' to '0', 'o' to '0', 'D' to '0', 'I' to '1', 'l' to '1', '|' to '1')

private val TIME_LIKE = Regex("""(?<![\p{L}\d])[0-9OolI|]{1,2}\s?[hH:]\s?[0-9OolI|]{0,2}(?![\p{L}\d])|(?<![\p{L}\d])[0-9OolI|]{1,2}[hH](?=\p{L})""")
private val TIME = Regex("""(?<![\p{L}\d])([01]?\d|2[0-4])\s?(?:[hH]|:(?=\s?\d))\s?([0-5]\d)?(?!\d)""")
private val RANGE_SEPARATOR = Regex("""^\s*(?:-|–|—|à|á|a|au|>|→|jusqu'à)\s*$""", RegexOption.IGNORE_CASE)
private val DANGLING_SEPARATOR = Regex("""^\s*(?:-|–|—|à|á|au|>|→)\s*$""", RegexOption.IGNORE_CASE)
private val LEADING_NOISE = Regex("""^[\s/,;:.\-–—]*(?:(?:de|et|puis)\s+)?[\s/,;:.\-–—]*""", RegexOption.IGNORE_CASE)
private val TRAILING_NOISE = Regex("""[\s/,;:.\-–—]*(?:\s(?:de|et|à|a))?[\s/,;:.\-–—]*$""", RegexOption.IGNORE_CASE)
private val SPACES = Regex("""\s+""")

/** "22h à6h", "22hà 6h", "8hà16h": the range word glued to a time; re-spaced so [TIME] sees both times. */
private val GLUED_RANGE = Regex("""(\d[hH:]\d{0,2})\s*([àá])\s*(?=[0-9OolI|]{1,2}\s?[hH:])""")

/** First digit of a range read as a letter ("Sh à 13h", "&h à 16h", "Bh30 à 17h30"): S→5, &→8, B→8. */
private val LETTER_HOUR = Regex("""(?<![\p{L}\d])([SB&])(?=[hH]\d{0,2}\s*(?:à|á|a|-|–)\s*\d)""")
private val LETTER_HOUR_DIGIT = mapOf("S" to "5", "B" to "8", "&" to "8")

/** Repairs OCR spacing and letter-for-digit slips around time ranges. Returns the text and whether a digit was guessed. */
internal fun repairTimeRanges(text: String): Pair<String, Boolean> {
    val spaced = GLUED_RANGE.replace(text) { m -> "${m.groupValues[1]} ${m.groupValues[2]} " }
    var guessed = false
    val fixed = LETTER_HOUR.replace(spaced) { m -> guessed = true; LETTER_HOUR_DIGIT.getValue(m.value) }
    return fixed to guessed
}

/** Replaces digit look-alikes inside time-shaped words ("l4h3O" -> "14h30"). Returns the text and whether it changed. */
internal fun fixTimeDigits(text: String): Pair<String, Boolean> {
    var changed = false
    val fixed = TIME_LIKE.replace(text) { m ->
        val v = m.value
        if (v.none { it.isDigit() }) return@replace v
        val out = buildString { v.forEach { append(DIGIT_LOOKALIKES[it] ?: it) } }
        if (out != v) changed = true
        out
    }
    return fixed to changed
}

/** Replaces digit look-alikes in a short number ("l8" -> "18", "O1" -> "01"); null if not a number. */
internal fun fixNumber(text: String): Int? {
    val t = text.trim().trimEnd('.', ',')
    if (t.isEmpty() || t.length > 2 || t.none { it.isDigit() }) return null
    val digits = buildString { t.forEach { append(DIGIT_LOOKALIKES[it] ?: it) } }
    return digits.toIntOrNull()
}

/**
 * Finds times and ranges: "6h", "06:00", "6h30-14h", "22h à 6h" (ends the next day),
 * "8h–12h / 14h–18h" (two ranges). The rest of the text is returned as the title.
 */
internal fun parseTimes(raw: String): TimedText {
    val (repaired, guessed) = repairTimeRanges(raw)
    val (text, fixedDigits) = fixTimeDigits(repaired)
    val corrected = guessed || fixedDigits
    val matches = TIME.findAll(text).mapNotNull { m ->
        val hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
        if (hour == 24 && minute != 0) null else Triple(m.range, hour, minute)
    }.toList()
    if (matches.isEmpty()) return TimedText(emptyList(), clean(text), false, false)

    val ranges = ArrayList<TimeRange>()
    val consumed = ArrayList<IntRange>()
    var missingEnd = false
    var i = 0
    while (i < matches.size) {
        val (r1, h1, m1) = matches[i]
        val next = matches.getOrNull(i + 1)
        val between = next?.let { text.substring(r1.last + 1, it.first.first) }
        val start = LocalTime.of(h1 % 24, m1)
        if (next != null && between != null && RANGE_SEPARATOR.matches(between)) {
            val (r2, h2, m2) = next
            val end = LocalTime.of(h2 % 24, m2)
            ranges += TimeRange(start, end, endsNextDay = h2 == 24 || !end.isAfter(start) && end != start)
            consumed += r1.first..r2.last
            i += 2
        } else {
            // "8h à" with nothing after: the end time was not read.
            val after = text.substring(r1.last + 1)
            val dangling = next == null && DANGLING_SEPARATOR.matches(after)
            if (dangling) missingEnd = true
            consumed += r1.first..(if (dangling) text.lastIndex else r1.last)
            ranges += TimeRange(start, null, false)
            i++
        }
    }
    val rest = StringBuilder()
    var pos = 0
    consumed.sortedBy { it.first }.forEach { r ->
        if (r.first > pos) rest.append(text, pos, r.first).append(' ')
        pos = maxOf(pos, r.last + 1)
    }
    if (pos < text.length) rest.append(text.substring(pos))
    return TimedText(ranges, clean(rest.toString()), corrected, missingEnd)
}

private fun clean(s: String): String {
    val parts = s.split('/', '|').map { it.replace(LEADING_NOISE, "").replace(TRAILING_NOISE, "").trim() }
    return parts.filter { it.isNotEmpty() }.joinToString(" ").replace(SPACES, " ").trim()
}
