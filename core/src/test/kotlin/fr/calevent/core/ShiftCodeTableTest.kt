package fr.calevent.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShiftCodeTableTest {
    private val table = sampleCodeTable()
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
        for (code in listOf("AB", "AM", "MAL")) {
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
        assertEquals(listOf("M06", "M07", "S14", "J08", "N08", "RH", "CA", "AB", "MAL"), table.codes.map { it.code })
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

    private val fx = ShiftCode("FX", "Formation", start = t(9), end = t(17))

    @Test
    fun `with adds then replaces in place and keeps the order`() {
        val added = table.with(fx)
        assertEquals("FX", added.codes.last().code)
        val replaced = added.with(fx.copy(label = "Stage"))
        assertEquals(added.size, replaced.size)
        assertEquals("Stage", replaced.find("fx")?.label)
        assertEquals(added.codes.map { it.code }, replaced.codes.map { it.code })
        assertEquals(table, replaced.without("FX"))
    }

    @Test
    fun `absence is normalised to a bare all-day Absence`() {
        val t2 = table.with(ShiftCode("ZZ", "Arrêt maladie grippe", ShiftType.ABSENCE, start = t(8), end = t(9)))
        val e = assertNotNull(t2.find("ZZ"))
        assertEquals("Absence", e.label)
        assertTrue(e.allDay)
        assertNull(e.start)
        assertEquals("Absence", t2.toSlot("ZZ", day).title)
    }

    @Test
    fun `serialisation round-trips the default table and edits`() {
        val edited = table.with(fx).with(ShiftCode("NX", "Nuit longue", start = t(20, 30), end = t(8), endsNextDay = true)).without("CA")
        assertEquals(edited, ShiftCodeTable.parse(edited.serialize()))
        assertEquals(table, ShiftCodeTable.parse(table.serialize()))
        assertEquals(ShiftCodeTable(), ShiftCodeTable.parse(ShiftCodeTable().serialize()))
    }

    @Test
    fun `invalid input is ignored safely`() {
        assertEquals(0, ShiftCodeTable.parse(null).size)
        assertEquals(0, ShiftCodeTable.parse("garbage\n\n\t\t").size)
        val text = "M06\tMatin\tWORK\t06:00\t14:00\t0\t0\n" +
            "BAD\tNo times\tWORK\t\t\t0\t0\n" +
            "BAD2\tBad time\tWORK\t25:99\t14:00\t0\t0\n" +
            "BAD3\tBad type\tFOO\t\t\t1\t0\n" +
            "m 06\tDup\tWORK\t01:00\t02:00\t0\t0\n"
        val parsed = ShiftCodeTable.parse(text)
        assertEquals(listOf("M06"), parsed.codes.map { it.code })
    }

    @Test
    fun `fields with tabs and line breaks survive serialisation`() {
        val odd = table.with(ShiftCode("TB", "A\tB\nC", start = t(1), end = t(2)))
        assertEquals("A B C", ShiftCodeTable.parse(odd.serialize()).find("TB")?.label)
    }

    @Test
    fun `defining an unknown code recomputes its slots and keeps decisions`() {
        val a = table.toSlot("FX", d(2026, 11, 16), "Moi", confidence = 0.95, id = "a").keep()
        val b = table.toSlot("fx", d(2026, 11, 17), "Moi", confidence = 0.95, id = "b").copy(region = Region(0.1, 0.1, 0.2, 0.2))
        val other = table.toSlot("QQ", d(2026, 11, 18), "Moi", confidence = 0.95, id = "c")
        val known = table.toSlot("M06", d(2026, 11, 19), "Moi", id = "d").ignore()
        val slots = listOf(a, b, other, known)
        assertEquals(SlotStatus.TO_REVIEW, b.status)
        assertEquals(listOf("FX", "QQ"), unknownCodesOf(slots, table))

        val out = table.with(fx).resolveUnknownCodes(slots)
        assertEquals("Formation (FX)", out[0].title)
        assertEquals(t(9), out[0].start)
        assertEquals(UserDecision.KEEP, out[0].decision)
        assertEquals(SlotStatus.KEPT, out[1].status)
        assertNull(out[1].reviewReason)
        assertEquals(Region(0.1, 0.1, 0.2, 0.2), out[1].region)
        assertEquals(other, out[2])
        assertEquals(known, out[3])
        assertEquals(listOf("QQ"), unknownCodesOf(out, table.with(fx)))
    }
}
