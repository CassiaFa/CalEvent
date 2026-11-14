package fr.calevent.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DuplicatesTest {
    private val a = slot("a", title = "Matin (M06)", date = d(2026, 11, 16))
    private val b = slot("b", title = "Soir (S14)", date = d(2026, 11, 17), start = t(14), end = t(22))
    private val c = slot("c", title = "Congés", date = d(2026, 11, 18), start = null, end = null, allDay = true)

    @Test
    fun `no duplicate`() {
        val r = splitDuplicates(listOf(a, b), emptyList())
        assertEquals(listOf(a, b), r.toCreate)
        assertNull(r.label)
        assertFalse(r.allPresent)
        assertNull(r.allPresentLabel)
    }

    @Test
    fun `some duplicates with different case, accents and spaces`() {
        val existing = listOf(a.toCalendarEvent().copy(title = "  MATIN  (m06) "), c.toCalendarEvent().copy(title = "CONGES"))
        val r = splitDuplicates(listOf(a, b, c), existing)
        assertEquals(listOf(b), r.toCreate)
        assertEquals(listOf(a, c), r.alreadyPresent)
        assertEquals("2 créneaux déjà présents, ignorés", r.label)
    }

    @Test
    fun `singular label`() {
        val r = splitDuplicates(listOf(a, b), listOf(a.toCalendarEvent()))
        assertEquals("1 créneau déjà présent, ignoré", r.label)
    }

    @Test
    fun `everything already present`() {
        val r = splitDuplicates(listOf(a, b), listOf(a, b).map { it.toCalendarEvent() })
        assertTrue(r.allPresent)
        assertEquals("Tout est déjà dans l'agenda", r.allPresentLabel)
        assertTrue(r.toCreate.isEmpty())
    }

    @Test
    fun `no slot at all is not all present`() {
        assertFalse(splitDuplicates(emptyList(), emptyList()).allPresent)
    }

    @Test
    fun `different start, end or allDay is not a duplicate`() {
        val ev = a.toCalendarEvent()
        val others = listOf(
            ev.copy(start = ev.start.plusHours(1)),
            ev.copy(end = ev.end.plusHours(1)),
            ev.copy(allDay = true),
        )
        others.forEach { assertEquals(listOf(a), splitDuplicates(listOf(a), listOf(it)).toCreate) }
    }

    @Test
    fun `accented titles match across normalization forms`() {
        val s = slot("n", title = "Journée (J08)", start = t(8), end = t(20))
        val ev = s.toCalendarEvent().copy(title = "JOURNEE (j08)")
        assertTrue(splitDuplicates(listOf(s), listOf(ev)).allPresent)
    }
}
