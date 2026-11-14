package fr.calevent.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShiftCodeTableTest {
    private val table = ShiftCodeTable.default()
    private val day = d(2026, 11, 16)

    @Test
    fun `work code gives label with code and times`() {
        val s = table.toSlot("M06", day)
        assertEquals("Matin (M06)", s.title)
        assertEquals(t(6), s.start)
        assertEquals(t(14), s.end)
        assertFalse(s.allDay)
        assertFalse(s.endsNextDay)
        assertEquals(SlotStatus.KEPT, s.status)
    }

    @Test
    fun `night code ends the next day`() {
        val s = table.toSlot("N08", day)
        assertEquals("Nuit (N08)", s.title)
        assertEquals(t(21), s.start)
        assertEquals(t(7), s.end)
        assertTrue(s.endsNextDay)
    }

    @Test
    fun `rest is an all-day Repos`() {
        val s = table.toSlot("RH", day)
        assertEquals("Repos", s.title)
        assertTrue(s.allDay)
        assertNull(s.start)
    }

    @Test
    fun `absence is just Absence with no detail for any absence code`() {
        val custom = table.add(ShiftCode("AM", "Arrêt maladie", ShiftType.ABSENCE, allDay = true))
        for (code in listOf("AB", "AM")) {
            val s = custom.toSlot(code, day, person = "Durand C.")
            assertEquals("Absence", s.title)
            assertTrue(s.allDay)
            assertEquals("", s.sourceText)
            assertNull(s.location)
            assertFalse(s.title.contains(code))
        }
    }

    @Test
    fun `leave keeps its own label and is all day`() {
        val s = table.toSlot("CA", day)
        assertEquals("Congés", s.title)
        assertTrue(s.allDay)
    }

    @Test
    fun `unknown code is to review with raw code as title and a reason`() {
        val s = table.toSlot(" FX ", day, confidence = 0.99)
        assertEquals("FX", s.title)
        assertEquals(SlotStatus.TO_REVIEW, s.status)
        assertEquals("Code inconnu : FX", s.reviewReason)
        assertFalse(s.isSelected)
    }

    @Test
    fun `unknown code with very low confidence stays ignored`() {
        assertEquals(SlotStatus.IGNORED, table.toSlot("FX", day, confidence = 0.3).status)
    }

    @Test
    fun `lookup ignores case and spaces`() {
        assertEquals("M06", table.find("m 06")?.code)
        assertEquals("Matin (M06)", table.toSlot(" m06 ", day).title)
    }

    @Test
    fun `default table matches the mockup`() {
        assertEquals(listOf("M06", "M07", "S14", "J08", "N08", "RH", "CA", "AB"), table.codes.map { it.code })
    }

    @Test
    fun `add update and remove return new tables`() {
        val fx = ShiftCode("FX", "Formation", start = t(9), end = t(17))
        val added = table.add(fx)
        assertNull(table.find("FX"))
        assertNotNull(added.find("fx"))
        assertEquals("Formation (FX)", added.toSlot("FX", day).title)

        val updated = added.update("FX", fx.copy(label = "Stage"))
        assertEquals("Stage (FX)", updated.toSlot("FX", day).title)
        assertEquals(added.codes.size, updated.codes.size)

        val removed = updated.remove("fx")
        assertNull(removed.find("FX"))
        assertEquals(table.codes, removed.codes)
    }

    @Test
    fun `adding an existing code or updating an unknown one fails`() {
        assertFailsWith<IllegalArgumentException> { table.add(ShiftCode("m06", "X", start = t(1), end = t(2))) }
        assertFailsWith<IllegalArgumentException> { table.update("ZZ", ShiftCode("ZZ", "X", start = t(1), end = t(2))) }
    }

    @Test
    fun `work code without times is rejected`() {
        assertFailsWith<IllegalArgumentException> { ShiftCode("XX", "Bad") }
    }

    @Test
    fun `night slot of the last day of the month ends on the first of next month`() {
        val event = table.toSlot("N08", d(2026, 11, 30)).toCalendarEvent()
        assertEquals(d(2026, 11, 30).atTime(21, 0), event.start)
        assertEquals(d(2026, 12, 1).atTime(7, 0), event.end)
    }
}
