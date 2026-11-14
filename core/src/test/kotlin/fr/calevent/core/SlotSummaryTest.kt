package fr.calevent.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SlotSummaryTest {
    @Test
    fun `counts each status`() {
        val s = SlotSummary.of(listOf(slot("a", confidence = 0.9), slot("b", confidence = 0.6), slot("c", confidence = 0.1), slot("d", confidence = 0.95)))
        assertEquals(SlotSummary(2, 1, 1), s)
    }

    @Test
    fun `user decisions are counted`() {
        val s = SlotSummary.of(listOf(slot("a", confidence = 0.6).keep(), slot("b", confidence = 0.9).ignore()))
        assertEquals(SlotSummary(1, 0, 1), s)
    }

    @Test
    fun `labels handle plural, singular and zero`() {
        assertEquals("12 retenus · 3 à revoir", SlotSummary(12, 3, 0).label)
        assertEquals("1 retenu", SlotSummary(1, 0, 4).label)
        assertEquals("0 retenu", SlotSummary(0, 0, 0).label)
        assertEquals("1 retenu · 1 à revoir", SlotSummary(1, 1, 0).label)
        assertEquals("0 retenu · 2 à revoir", SlotSummary(0, 2, 0).label)
        assertEquals("21 retenus", SlotSummary(21, 0, 0).label)
    }

    @Test
    fun `slots to review come first then chronological`() {
        val kept1 = slot("k1", date = d(2026, 11, 16))
        val kept2 = slot("k2", date = d(2026, 11, 17))
        val review = slot("r", date = d(2026, 11, 20), confidence = 0.6)
        assertEquals(listOf("r", "k1", "k2"), displaySlots(listOf(kept2, review, kept1)).map { it.id })
    }

    @Test
    fun `same day is ordered by start time`() {
        val late = slot("late", start = t(14), end = t(22))
        val early = slot("early", start = t(6))
        assertEquals(listOf("early", "late"), displaySlots(listOf(late, early)).map { it.id })
    }

    @Test
    fun `ignored slots are hidden unless requested`() {
        val ignored = slot("i", confidence = 0.2)
        val kept = slot("k")
        assertEquals(listOf("k"), displaySlots(listOf(ignored, kept)).map { it.id })
        assertEquals(listOf("k", "i").toSet(), displaySlots(listOf(ignored, kept), showIgnored = true).map { it.id }.toSet())
    }
}
