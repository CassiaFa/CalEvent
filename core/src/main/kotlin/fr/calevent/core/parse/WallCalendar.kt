package fr.calevent.core.parse

import fr.calevent.core.Slot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.min

/** A day number of a month grid, with its week row and day column. */
private data class DayNumber(val token: Token, val value: Int, val row: Int, val column: Int)

/**
 * Month grid: weekday names on top ("Lun" … "Dim"), a day number in each cell, events written in
 * the cells ("17h Séminaire", "Réunion équipe"). Month and year come from the title
 * ("Septembre 2025") or are inferred from where the day numbers fall in the week.
 */
internal fun analyzeWallCalendar(tokens: List<Token>, ctx: Context): Analysis? {
    val h = ctx.lineHeight
    val weekdayTokens = tokens.mapNotNull { t ->
        val p = parseHeaderDate(t.text)
        if (p.strong && p.day == null && p.weekdays.size == 1) t to p.weekdays.first() else null
    }
    val header = clusterBy(weekdayTokens, 0.8 * h) { it.first.box.centerY }
        .maxByOrNull { row -> row.map { it.second }.distinct().size } ?: return null
    val found = header.distinctBy { it.second }
    if (found.size < 4) return null

    // Columns: week starts on Monday unless "Dim" is left of "Lun".
    val xOf = found.associate { it.second to it.first.box.centerX }
    val start = if ((xOf[DayOfWeek.SUNDAY] ?: Double.MAX_VALUE) < (xOf[DayOfWeek.MONDAY] ?: Double.MAX_VALUE)) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
    fun indexOf(d: DayOfWeek) = (d.value - start.value + 7) % 7
    val (a, b) = fitLine(found.map { indexOf(it.second).toDouble() to it.first.box.centerX })
    if (b <= 0) return null
    val colX = (0..6).map { k -> found.firstOrNull { indexOf(it.second) == k }?.first?.box?.centerX ?: (a + b * k) }
    val pitch = b
    val headerTop = header.minOf { it.first.box.top }
    val headerBottom = header.maxOf { it.first.box.bottom }

    val numberTokens = tokens.filter { t ->
        val v = fixNumber(t.text)
        t.box.centerY > headerBottom && v != null && v in 1..31 && t.text.trim().length <= 2 &&
            t.box.centerX > colX.first() - 0.6 * pitch && t.box.centerX < colX.last() + 0.6 * pitch
    }
    if (numberTokens.size < 8) return null
    // Day numbers sit in a corner of their cell: measure that offset before choosing columns.
    var offset = 0.0
    repeat(2) {
        offset = numberTokens.map { t -> t.box.centerX - colX[nearestIndex(colX, t.box.centerX - offset)] }.median()
    }
    val rows = clusterBy(numberTokens, h) { it.box.centerY }
    val numbers = rows.flatMapIndexed { r, row ->
        row.map { t -> DayNumber(t, fixNumber(t.text)!!, r, nearestIndex(colX, t.box.centerX - offset)) }
    }
    val rowY = rows.map { row -> row.minOf { it.box.top } }
    val rowPitch = rowY.zipWithNext { p, q -> q - p }.median().takeIf { it > 0 } ?: (6 * h)

    val titleLines = tokens.filter { it.box.bottom < headerTop }.sortedBy { it.box.centerY }.map { it.text }
    val title = parseTitle(titleLines)
    val weekdayOfColumn = (0..6).map { start.plus(it.toLong()) }

    fun dateOf(n: DayNumber, ym: YearMonth): LocalDate? {
        val shift = when {
            n.row == 0 && n.value > 20 -> -1L
            n.row == rows.lastIndex && n.row > 0 && n.value < 15 -> 1L
            else -> 0L
        }
        val m = ym.plusMonths(shift)
        return if (m.isValidDay(n.value)) m.atDay(n.value) else null
    }

    val todayYm = YearMonth.from(ctx.today)
    val titleMonth = title.months.firstOrNull()
    val month = (-24L..24L).map { todayYm.plusMonths(it) }.maxBy { ym ->
        var s = numbers.sumOf { n ->
            val d = dateOf(n, ym)
            when {
                d == null -> -5.0
                d.dayOfWeek == weekdayOfColumn[n.column] -> 10.0
                else -> -10.0
            }
        }
        if (titleMonth != null) {
            if (titleMonth.first == ym.monthValue) s += 60
            if (titleMonth.second != null) s += if (titleMonth.second == ym.year) 60 else -60
        }
        if (title.years.isNotEmpty() && titleMonth?.second == null && ym.year in title.years) s += 60
        val dist = ChronoUnit.MONTHS.between(todayYm, ym)
        s - (if (dist >= 0) dist * 0.5 else -dist * 0.8)
    }

    // Each week row starts on the date voted by its readable day numbers (OCR misreads are outvoted).
    val matched = numbers.filter { n -> dateOf(n, month)?.dayOfWeek == weekdayOfColumn[n.column] }
    val rowStart: List<LocalDate?> = rows.indices.map { r ->
        matched.filter { it.row == r }
            .map { dateOf(it, month)!!.minusDays(it.column.toLong()) }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }
    val monthWritten = titleMonth != null
    val uncertain = !monthWritten && matched.size < 7
    val date = { r: Int, k: Int -> rowStart[r]?.plusDays(k.toLong()) }

    val numberSet = numberTokens.toSet()
    val lastRowBottom = rowY.last() + rowPitch
    val slots = ArrayList<Slot>()
    var index = 0
    for (t in tokens.sortedWith(compareBy({ it.box.centerY }, { it.box.centerX }))) {
        if (t in numberSet || t.box.centerY <= headerBottom || t.box.centerY > lastRowBottom) continue
        val k = nearestIndex(colX, t.box.centerX)
        if (abs(colX[k] - t.box.centerX) > 0.6 * pitch) continue
        val r = rowY.indices.filter { rowY[it] < t.box.centerY }.maxByOrNull { rowY[it] } ?: continue
        if (t.box.centerY - rowY[r] > 0.95 * rowPitch) continue // below the cell: footer
        val d = date(r, k) ?: continue
        val col = ColumnDate(d, uncertain, REASON_DATE_GUESSED.takeIf { uncertain })
        slots += buildSlots("cal|$d|${index++}", t.text, listOf(t), col, ctx)
    }
    val score = 0.3 * min(1.0, found.size / 7.0) + 0.4 * min(1.0, numbers.size / 20.0) +
        0.3 * matched.size / numbers.size
    return Analysis(ScheduleKind.WALL_CALENDAR, score, slots.sortedWith(compareBy({ it.date }, { it.start })))
}

/** Least-squares line y = a + b·x. */
private fun fitLine(points: List<Pair<Double, Double>>): Pair<Double, Double> {
    val n = points.size
    val mx = points.sumOf { it.first } / n
    val my = points.sumOf { it.second } / n
    val sxx = points.sumOf { (it.first - mx) * (it.first - mx) }
    if (sxx == 0.0) return my to 0.0
    val b = points.sumOf { (it.first - mx) * (it.second - my) } / sxx
    return (my - b * mx) to b
}
