package fr.calevent.core.parse

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY

/** What a column header says about its date; every part may be missing. */
internal data class HeaderDate(
    /** Possible weekdays: one for "lundi", two for the letter "M" (mardi or mercredi). */
    val weekdays: Set<DayOfWeek> = emptySet(),
    val day: Int? = null,
    val month: Int? = null,
    val year: Int? = null,
    val weekNumber: Int? = null,
    /** Weekday written as a word ("lun.", "LUNDI"), or day and month together: strong header evidence. */
    val strong: Boolean = false,
) {
    val isEmpty: Boolean get() = weekdays.isEmpty() && day == null && month == null && year == null
}

/** Month and year hints read in the title of the schedule. */
internal data class TitleInfo(
    /** Months named in the title, in order, with their year when written next to them. */
    val months: List<Pair<Int, Int?>> = emptyList(),
    val years: List<Int> = emptyList(),
    /** "Planning du 22 novembre 2027": day, month and optional year. */
    val firstDay: Triple<Int, Int, Int?>? = null,
    /** "Semaine 43". */
    val isoWeek: Int? = null,
)

private val COMBINING = Regex("\\p{M}+")

/** Lower-case, accent-free, without surrounding punctuation. */
internal fun normWord(w: String): String =
    Normalizer.normalize(w, Normalizer.Form.NFD).replace(COMBINING, "").lowercase().trim('.', ',', ';', ':', '(', ')')

internal val WEEKDAY_WORDS: Map<String, DayOfWeek> = mapOf(
    "lundi" to MONDAY, "lun" to MONDAY, "mardi" to TUESDAY, "mar" to TUESDAY,
    "mercredi" to WEDNESDAY, "mer" to WEDNESDAY, "jeudi" to THURSDAY, "jeu" to THURSDAY,
    "vendredi" to FRIDAY, "ven" to FRIDAY, "samedi" to SATURDAY, "sam" to SATURDAY,
    "dimanche" to SUNDAY, "dim" to SUNDAY,
)

/** Month words; "mar" is not here: alone it means mardi (only "22 mar." is March). */
internal val MONTH_WORDS: Map<String, Int> = mapOf(
    "janvier" to 1, "janv" to 1, "jan" to 1, "fevrier" to 2, "fevr" to 2, "fev" to 2, "mars" to 3,
    "avril" to 4, "avr" to 4, "mai" to 5, "juin" to 6, "juillet" to 7, "juil" to 7, "aout" to 8, "aou" to 8,
    "septembre" to 9, "sept" to 9, "sep" to 9, "octobre" to 10, "oct" to 10, "novembre" to 11, "nov" to 11,
    "decembre" to 12, "dec" to 12,
)

private val LETTER_WEEKDAYS: Map<Char, Set<DayOfWeek>> = mapOf(
    'L' to setOf(MONDAY), 'M' to setOf(TUESDAY, WEDNESDAY), 'J' to setOf(THURSDAY),
    'V' to setOf(FRIDAY), 'S' to setOf(SATURDAY), 'D' to setOf(SUNDAY),
)

private val LETTER_DAY = Regex("""^([LMJVSD])\s?([0-9OolI]{1,2})$""")
private val NUMERIC_DATE = Regex("""^([0-9OolI]{1,2})[/.\-]([0-9OolI]{1,2})(?:[/.\-](\d{2}|\d{4}))?$""")
private val YEAR = Regex("""^(19|20)\d{2}$""")

/**
 * Reads a column header: "LUNDI 19/10", "Lun 22 nov.", "L25 Jan S04" (letter + day, month,
 * week number), "lun. 16", "Mar". Returns what could be read; unknown words are ignored.
 */
internal fun parseHeaderDate(text: String): HeaderDate {
    var weekdays = emptySet<DayOfWeek>()
    var day: Int? = null
    var month: Int? = null
    var year: Int? = null
    var week: Int? = null
    var strong = false
    val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
    for ((i, raw) in words.withIndex()) {
        val w = raw.trim('.', ',', ';', ':')
        val n = normWord(w)
        val prevIsNumber = i > 0 && fixNumber(words[i - 1]) != null
        val letter = LETTER_DAY.matchEntire(w)
        val numeric = NUMERIC_DATE.matchEntire(w)
        when {
            letter != null -> {
                val v = fixNumber(letter.groupValues[2]) ?: continue
                if (weekdays.isEmpty() && day == null && v in 1..31) {
                    weekdays = LETTER_WEEKDAYS.getValue(letter.groupValues[1][0]); day = v
                } else if (letter.groupValues[1] == "S" && v in 1..53) {
                    week = v
                }
            }
            numeric != null -> {
                val d = fixNumber(numeric.groupValues[1])
                val m = fixNumber(numeric.groupValues[2])
                if (d != null && m != null && d in 1..31 && m in 1..12) {
                    day = d; month = m; strong = true
                    numeric.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { year = if (it < 100) 2000 + it else it }
                }
            }
            YEAR.matches(w) -> year = w.toInt()
            n == "mar" && prevIsNumber -> { month = 3; strong = strong || day != null }
            n in WEEKDAY_WORDS && weekdays.isEmpty() -> { weekdays = setOf(WEEKDAY_WORDS.getValue(n)); strong = true }
            n in MONTH_WORDS -> { month = MONTH_WORDS.getValue(n); if (day != null) strong = true }
            n == "1er" -> if (day == null) day = 1
            else -> {
                val v = fixNumber(w)
                if (v != null && v in 1..31 && day == null) day = v
            }
        }
    }
    return HeaderDate(weekdays, day, month, year, week, strong)
}

/** True when [text] alone looks like a day header (weekday, letter + day, or day and month). */
internal fun isHeaderCandidate(text: String): Boolean {
    if (text.length > 24) return false
    val h = parseHeaderDate(text)
    return h.weekdays.isNotEmpty() || (h.day != null && h.month != null)
}

private val MONTH_ALT = MONTH_WORDS.keys.sortedByDescending { it.length }.joinToString("|")
private val TITLE_DAY = Regex("""\b(\d{1,2})(?:er)?\s+($MONTH_ALT)\.?(?:\s+(\d{4}))?\b""")
private val TITLE_MONTH = Regex("""\b($MONTH_ALT)\.?(?:\s+(\d{4}))?\b""")
private val TITLE_WEEK = Regex("""\bsemaine\s*(?:n\s?[°o]?\s*)?(\d{1,2})\b""")
private val TITLE_YEAR = Regex("""\b(20\d{2})\b""")

/** Reads month names, years, a starting day and a week number in title lines. */
internal fun parseTitle(lines: List<String>): TitleInfo {
    val text = normWord(lines.joinToString(" / "))
    val firstDay = TITLE_DAY.find(text)?.let { m ->
        Triple(m.groupValues[1].toInt(), MONTH_WORDS.getValue(m.groupValues[2]), m.groupValues[3].toIntOrNull())
    }?.takeIf { it.first in 1..31 }
    val months = TITLE_MONTH.findAll(text).mapNotNull { m ->
        val word = m.groupValues[1]
        val year = m.groupValues[2].toIntOrNull()
        // Short abbreviations alone are too ambiguous in free text; keep them only next to a year.
        if (word.length < 4 && year == null && firstDay?.second != MONTH_WORDS[word]) null
        else MONTH_WORDS.getValue(word) to year
    }.toList()
    return TitleInfo(
        months = months,
        years = TITLE_YEAR.findAll(text).map { it.groupValues[1].toInt() }.toList(),
        firstDay = firstDay,
        isoWeek = TITLE_WEEK.find(text)?.groupValues?.get(1)?.toInt()?.takeIf { it in 1..53 },
    )
}
