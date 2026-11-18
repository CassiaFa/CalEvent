package fr.calevent.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryTest {
    private fun entry(id: String, at: Long, thumb: Boolean = true) = HistoryEntry(
        id, at, null, false, null, 3, "Agenda", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), thumb, listOf(1L),
    )

    @Test
    fun `keeps the 20 newest, newest first`() {
        val all = (1L..25L).fold(emptyList<HistoryEntry>()) { acc, i -> History.add(acc, entry("e$i", i)) }
        assertEquals(20, all.size)
        assertEquals("e25", all.first().id)
        assertEquals("e6", all.last().id)
    }

    @Test
    fun `re-adding an id replaces it`() {
        val l = History.add(History.add(emptyList(), entry("a", 1)), entry("a", 5))
        assertEquals(listOf(5L), l.map { it.createdAtMillis })
    }

    @Test
    fun `date range labels`() {
        val d = { m: Int, day: Int -> LocalDate.of(2026, m, day) }
        assertEquals("16 nov.", History.dateRangeLabel(d(11, 16), d(11, 16)))
        assertEquals("16–29 nov.", History.dateRangeLabel(d(11, 16), d(11, 29)))
        assertEquals("28 nov. – 3 déc.", History.dateRangeLabel(d(11, 28), d(12, 3)))
        assertEquals("29 déc. 2026 – 2 janv. 2027", History.dateRangeLabel(d(12, 29), LocalDate.of(2027, 1, 2)))
    }

    @Test
    fun `expired thumbnails follow the retention`() {
        val day = 86_400_000L
        val now = 100 * day
        val list = listOf(entry("old", now - 40 * day), entry("new", now - day), entry("gone", now - 50 * day, thumb = false))
        assertEquals(listOf("old"), History.expiredThumbnails(list, PhotoRetention.DAYS_30, now).map { it.id })
        assertEquals(emptyList(), History.expiredThumbnails(list, PhotoRetention.ALWAYS, now))
    }
}
