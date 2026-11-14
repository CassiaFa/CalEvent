package fr.calevent.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SendPlanTest {
    private val kept = slot("k", date = d(2026, 11, 16))
    private val kept2 = slot("k2", date = d(2026, 11, 17))
    private val review = slot("r", date = d(2026, 11, 18), confidence = 0.6)
    private val ignored = slot("i", date = d(2026, 11, 19), confidence = 0.1)

    @Test
    fun `only selected slots become events`() {
        val plan = SendPlan.build(listOf(kept, review, ignored, kept2), emptyList())
        assertEquals(listOf(kept.date, kept2.date), plan.events.map { it.start.toLocalDate() })
        assertTrue(plan.canSend)
        assertEquals("Ajouter 2 créneaux à l'agenda", plan.sendLabel)
    }

    @Test
    fun `reviewed slot kept by the user is sent`() {
        assertEquals(1, SendPlan.build(listOf(review.keep()), emptyList()).events.size)
    }

    @Test
    fun `nothing selected cannot be sent`() {
        val plan = SendPlan.build(listOf(review, ignored), emptyList())
        assertFalse(plan.canSend)
        assertFalse(SendPlan.build(emptyList(), emptyList()).canSend)
    }

    @Test
    fun `duplicates are skipped and reported`() {
        val plan = SendPlan.build(listOf(kept, kept2), listOf(kept.toCalendarEvent()))
        assertEquals(1, plan.events.size)
        assertEquals("1 créneau déjà présent, ignoré", plan.duplicates.label)
        assertTrue(plan.canSend)
        assertEquals("Ajouter 1 créneau à l'agenda", plan.sendLabel)
    }

    @Test
    fun `all selected already present cannot be sent`() {
        val plan = SendPlan.build(listOf(kept, kept2, review), listOf(kept, kept2).map { it.toCalendarEvent() })
        assertFalse(plan.canSend)
        assertTrue(plan.duplicates.allPresent)
    }

    @Test
    fun `unselected duplicates are not reported`() {
        val plan = SendPlan.build(listOf(kept, review), listOf(review.toCalendarEvent()))
        assertEquals(null, plan.duplicates.label)
        assertTrue(plan.canSend)
    }
}
