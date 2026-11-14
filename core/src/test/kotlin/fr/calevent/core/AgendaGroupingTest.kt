package fr.calevent.core

import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class AgendaGroupingTest {
    private fun on(id: String, date: java.time.LocalDate) = slot(id, date = date)

    @Test
    fun `schedule over two months is split by month`() {
        val slots = listOf(on("c", d(2026, 12, 2)), on("a", d(2026, 11, 27)), on("b", d(2026, 11, 30)))
        val groups = groupByMonth(slots)
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 12)), groups.map { it.month })
        assertEquals(listOf("a", "b"), groups[0].slots.map { it.id })
        assertEquals(d(2026, 11, 27), groups[0].from)
        assertEquals(d(2026, 11, 30), groups[0].to)
        assertEquals(d(2026, 12, 2), groups[1].from)
        assertEquals(d(2026, 12, 2), groups[1].to)
    }

    @Test
    fun `december to january crosses the year`() {
        val groups = groupByMonth(listOf(on("b", d(2027, 1, 2)), on("a", d(2026, 12, 31))))
        assertEquals(listOf(YearMonth.of(2026, 12), YearMonth.of(2027, 1)), groups.map { it.month })
    }

    @Test
    fun `weeks start on Monday`() {
        val slots = (16..22).map { on("n$it", d(2026, 11, it)) } + on("next", d(2026, 11, 23))
        val groups = groupByWeek(slots)
        assertEquals(2, groups.size)
        assertEquals(d(2026, 11, 16), groups[0].weekStart)
        assertEquals(7, groups[0].slots.size)
        assertEquals(d(2026, 11, 22), groups[0].weekEnd)
        assertEquals(d(2026, 11, 23), groups[1].weekStart)
        assertEquals("16 – 22 nov.", groups[0].label)
    }

    @Test
    fun `week straddling two months stays one group`() {
        // Mon 2026-11-30 .. Sun 2026-12-06
        val slots = listOf(on("a", d(2026, 11, 30)), on("b", d(2026, 12, 3)))
        val weeks = groupByWeek(slots)
        assertEquals(1, weeks.size)
        assertEquals(d(2026, 11, 30), weeks[0].from)
        assertEquals(d(2026, 12, 3), weeks[0].to)
        assertEquals("30 nov. – 6 déc.", weeks[0].label)
        assertEquals(2, groupByMonth(slots).size)
    }

    @Test
    fun `week straddling two years`() {
        // Mon 2026-12-28 .. Sun 2027-01-03
        val weeks = groupByWeek(listOf(on("a", d(2026, 12, 31)), on("b", d(2027, 1, 1))))
        assertEquals(1, weeks.size)
        assertEquals(d(2026, 12, 28), weeks[0].weekStart)
        assertEquals("28 déc. – 3 janv.", weeks[0].label)
    }

    @Test
    fun `empty input gives no group`() {
        assertEquals(emptyList(), groupByMonth(emptyList()))
        assertEquals(emptyList(), groupByWeek(emptyList()))
    }
}
