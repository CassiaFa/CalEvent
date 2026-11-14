package fr.calevent.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarEventTest {
    @Test
    fun `simple slot keeps its times`() {
        val e = slot().toCalendarEvent()
        assertEquals(d(2026, 11, 16).atTime(6, 0), e.start)
        assertEquals(d(2026, 11, 16).atTime(14, 0), e.end)
        assertEquals("Matin (M06)", e.title)
    }

    @Test
    fun `night flag ends the next day`() {
        val e = slot(start = t(21), end = t(7), endsNextDay = true).toCalendarEvent()
        assertEquals(d(2026, 11, 17).atTime(7, 0), e.end)
    }

    @Test
    fun `night on December 31 ends on January 1`() {
        val e = slot(date = d(2026, 12, 31), start = t(21), end = t(7), endsNextDay = true).toCalendarEvent()
        assertEquals(d(2026, 12, 31).atTime(21, 0), e.start)
        assertEquals(d(2027, 1, 1).atTime(7, 0), e.end)
    }

    @Test
    fun `end before start is deduced as next day even without the flag`() {
        val e = slot(date = d(2026, 11, 30), start = t(22), end = t(6)).toCalendarEvent()
        assertEquals(d(2026, 12, 1).atTime(6, 0), e.end)
    }

    @Test
    fun `all-day goes from midnight to next midnight`() {
        val e = slot(title = "Repos", allDay = true, start = null, end = null, date = d(2026, 12, 31)).toCalendarEvent()
        assertTrue(e.allDay)
        assertEquals(d(2026, 12, 31).atStartOfDay(), e.start)
        assertEquals(d(2027, 1, 1).atStartOfDay(), e.end)
    }

    @Test
    fun `no end time lasts one hour`() {
        val e = slot(start = t(9), end = null).toCalendarEvent()
        assertEquals(d(2026, 11, 16).atTime(10, 0), e.end)
    }

    @Test
    fun `no times at all is treated as all day`() {
        assertTrue(slot(start = null, end = null).toCalendarEvent().allDay)
    }

    @Test
    fun `location is carried over`() {
        assertEquals("USC 3e", slot().copy(location = "USC 3e").toCalendarEvent().location)
    }
}
