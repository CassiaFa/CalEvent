package fr.calevent.core.parse

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import kotlin.math.abs

/** Date of one column. [reason] (French) explains why it is [uncertain]. */
internal data class ColumnDate(val date: LocalDate?, val uncertain: Boolean = false, val reason: String? = null)

internal const val REASON_DATE_UNREADABLE = "Date illisible"
internal const val REASON_DATE_GUESSED = "Date déduite, à vérifier"
internal const val REASON_WEEKDAY_MISMATCH = "Date incohérente avec le jour de la semaine"
internal const val REASON_NO_DATE = "Pas de date sur le planning : semaine à venir supposée"

/** Dates inferred without a written year are trusted only within this window around today. */
private const val WINDOW_BEFORE_DAYS = 90L
private const val WINDOW_AFTER_DAYS = 300L

private fun inWindow(date: LocalDate, today: LocalDate) =
    !date.isBefore(today.minusDays(WINDOW_BEFORE_DAYS)) && !date.isAfter(today.plusDays(WINDOW_AFTER_DAYS))

private fun safeDate(ym: YearMonth, day: Int): LocalDate? = if (ym.isValidDay(day)) ym.atDay(day) else null

/**
 * Resolves the date of every column of a schedule. Day numbers come from the headers; month and
 * year come from the headers, the title, or are inferred: candidate months around [today] are
 * scored on agreement with written months/years, weekday consistency and closeness to today.
 * A schedule may span two months (including December to January).
 */
internal fun resolveColumnDates(headers: List<HeaderDate>, title: TitleInfo, today: LocalDate): List<ColumnDate> {
    if (headers.all { it.day == null }) return resolveWithoutDays(headers, title, today)
    val first = headers.indexOfFirst { it.day != null }

    val todayYm = YearMonth.from(today)
    val best = (-24L..24L).map { todayYm.plusMonths(it) }
        .maxBy { scoreCandidate(headers, first, it, title, todayYm) }
    val yms = deriveMonths(headers, first, best)

    val monthWritten = headers.any { it.month != null } || title.months.isNotEmpty() || title.firstDay != null
    val yearWritten = headers.any { it.year != null } || title.years.isNotEmpty() ||
        title.months.any { it.second != null } || title.firstDay?.third != null
    val weekdayEvidence = headers.any { it.weekdays.isNotEmpty() }

    val dates = headers.indices.map { i -> yms[i]?.let { ym -> headers[i].day?.let { safeDate(ym, it) } } }.toMutableList()
    // Columns whose day number was not read: deduce from the nearest dated neighbour.
    val filled = BooleanArray(headers.size)
    for (i in headers.indices) {
        if (dates[i] != null || headers[i].day != null) continue
        val j = headers.indices.filter { dates[it] != null && headers[it].day != null }.minByOrNull { abs(it - i) } ?: continue
        var candidate = dates[j]!!.plusDays((i - j).toLong())
        val wd = headers[i].weekdays
        if (wd.isNotEmpty() && candidate.dayOfWeek !in wd) {
            candidate = (0L..6L).map { candidate.plusDays(if (i > j) it else -it) }.firstOrNull { it.dayOfWeek in wd } ?: candidate
        }
        dates[i] = candidate
        filled[i] = wd.isEmpty()
    }

    return headers.indices.map { i ->
        val date = dates[i] ?: return@map ColumnDate(null, true, REASON_DATE_UNREADABLE)
        val prev = (i - 1 downTo 0).firstNotNullOfOrNull { dates[it] }
        when {
            headers[i].weekdays.isNotEmpty() && date.dayOfWeek !in headers[i].weekdays ->
                ColumnDate(date, true, REASON_WEEKDAY_MISMATCH)
            prev != null && !date.isAfter(prev) -> ColumnDate(date, true, REASON_DATE_UNREADABLE)
            filled[i] -> ColumnDate(date, true, REASON_DATE_GUESSED)
            !monthWritten && !weekdayEvidence -> ColumnDate(date, true, REASON_DATE_GUESSED)
            !yearWritten && !inWindow(date, today) -> ColumnDate(date, true, REASON_DATE_GUESSED)
            else -> ColumnDate(date)
        }
    }
}

/** Month of every dated column if the first dated column falls in [base]; wraps when days go down. */
private fun deriveMonths(headers: List<HeaderDate>, first: Int, base: YearMonth): List<YearMonth?> {
    val yms = arrayOfNulls<YearMonth>(headers.size)
    yms[first] = base
    var last = first
    for (i in first + 1 until headers.size) {
        val d = headers[i].day ?: continue
        yms[i] = if (d >= headers[last].day!!) yms[last] else yms[last]!!.plusMonths(1)
        last = i
    }
    var next = first
    for (i in first - 1 downTo 0) {
        val d = headers[i].day ?: continue
        yms[i] = if (d <= headers[next].day!!) yms[next] else yms[next]!!.minusMonths(1)
        next = i
    }
    return yms.toList()
}

private fun scoreCandidate(
    headers: List<HeaderDate>, first: Int, base: YearMonth, title: TitleInfo, today: YearMonth,
): Double {
    val yms = deriveMonths(headers, first, base)
    var score = 0.0
    for (i in headers.indices) {
        val ym = yms[i] ?: continue
        val h = headers[i]
        h.month?.let { score += if (it == ym.monthValue) 100 else -100 }
        h.year?.let { score += if (it == ym.year) 100 else -100 }
        val date = safeDate(ym, h.day!!)
        if (date == null) score -= 50
        else if (h.weekdays.isNotEmpty()) score += if (date.dayOfWeek in h.weekdays) 10 else -10
    }
    val firstYm = yms[first]!!
    val titleMonth = title.firstDay?.let { it.second to it.third } ?: title.months.firstOrNull()
    if (titleMonth != null) {
        if (titleMonth.first == firstYm.monthValue) score += 60
        if (titleMonth.second != null) score += if (titleMonth.second == firstYm.year) 60 else -60
    }
    if (title.years.isNotEmpty() && titleMonth?.second == null) {
        if (yms.filterNotNull().any { it.year in title.years }) score += 60
    }
    // Prefer the candidate closest to today, slightly favouring the coming months.
    val distance = ChronoUnit.MONTHS.between(today, firstYm)
    score -= if (distance >= 0) distance * 0.5 else -distance * 0.8
    return score
}

/** Headers with weekdays but no day number: use the title (start day or week number), else the coming week. */
private fun resolveWithoutDays(headers: List<HeaderDate>, title: TitleInfo, today: LocalDate): List<ColumnDate> {
    val weekdays = headers.map { it.weekdays.singleOrNull() }
    // Each column is the next day carrying its weekday (columns may skip days, e.g. weekends).
    fun spread(firstDate: LocalDate, uncertain: Boolean, reason: String?): List<ColumnDate> {
        val result = ArrayList<ColumnDate>()
        var date = firstDate
        for (i in headers.indices) {
            if (i > 0) {
                date = date.plusDays(1)
                val wd = weekdays[i]
                if (wd != null) while (date.dayOfWeek != wd) date = date.plusDays(1)
            }
            result += ColumnDate(date, uncertain, reason.takeIf { uncertain })
        }
        return result
    }
    val firstWeekday = weekdays.firstOrNull { it != null }
    title.firstDay?.let { (d, m, y) ->
        val year = y ?: listOf(today.year - 1, today.year, today.year + 1)
            .filter { YearMonth.of(it, m).isValidDay(d) }
            .minBy { abs(ChronoUnit.DAYS.between(today, LocalDate.of(it, m, d))) }
        val date = safeDate(YearMonth.of(year, m), d) ?: return headers.map { ColumnDate(null, true, REASON_DATE_UNREADABLE) }
        return spread(date, y == null && !inWindow(date, today), REASON_DATE_GUESSED)
    }
    if (title.isoWeek != null && firstWeekday != null) {
        val years = title.years.ifEmpty { listOf(today.year - 1, today.year, today.year + 1) }
        val monday = years.map { isoMonday(it, title.isoWeek) }
            .minBy { abs(ChronoUnit.DAYS.between(today, it)) }
        val date = monday.plusDays((firstWeekday.value - 1).toLong())
        return spread(date, title.years.isEmpty() && !inWindow(date, today), REASON_DATE_GUESSED)
    }
    if (firstWeekday != null) {
        var date = today
        while (date.dayOfWeek != firstWeekday) date = date.plusDays(1)
        return spread(date, true, REASON_NO_DATE)
    }
    return headers.map { ColumnDate(null, true, REASON_DATE_UNREADABLE) }
}

private fun isoMonday(year: Int, week: Int): LocalDate =
    LocalDate.of(year, 1, 4).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong()).with(DayOfWeek.MONDAY)
