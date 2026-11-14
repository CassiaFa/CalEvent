package fr.calevent.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Slots of one month. [from]..[to] is the range of dates actually covered by the slots. */
data class MonthGroup(val month: YearMonth, val slots: List<Slot>, val from: LocalDate, val to: LocalDate)

/** Slots of one Monday-to-Sunday week; a week may straddle two months or two years. */
data class WeekGroup(val weekStart: LocalDate, val slots: List<Slot>, val from: LocalDate, val to: LocalDate) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)

    /** "16 – 22 nov." or "29 déc. – 4 janv." (full Monday-Sunday week). */
    val label: String get() = rangeLabel(weekStart, weekEnd)
}

private val MONTHS = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")

fun rangeLabel(from: LocalDate, to: LocalDate): String =
    if (from.month == to.month && from.year == to.year) "${from.dayOfMonth} – ${to.dayOfMonth} ${MONTHS[to.monthValue - 1]}"
    else "${from.dayOfMonth} ${MONTHS[from.monthValue - 1]} – ${to.dayOfMonth} ${MONTHS[to.monthValue - 1]}"

private fun List<Slot>.sortedChrono() = sortedWith(compareBy<Slot> { it.date }.thenBy { it.start ?: java.time.LocalTime.MIN }.thenBy { it.id })

fun groupByMonth(slots: List<Slot>): List<MonthGroup> =
    slots.sortedChrono().groupBy { YearMonth.from(it.date) }.map { (m, list) ->
        MonthGroup(m, list, list.first().date, list.last().date)
    }.sortedBy { it.month }

fun groupByWeek(slots: List<Slot>): List<WeekGroup> =
    slots.sortedChrono()
        .groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .map { (monday, list) -> WeekGroup(monday, list, list.first().date, list.last().date) }
        .sortedBy { it.weekStart }
