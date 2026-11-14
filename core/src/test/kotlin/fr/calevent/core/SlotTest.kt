package fr.calevent.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlotTest {
    @Test
    fun `status follows the exact thresholds`() {
        assertEquals(SlotStatus.KEPT, slot(confidence = 0.8).status)
        assertEquals(SlotStatus.TO_REVIEW, slot(confidence = 0.79).status)
        assertEquals(SlotStatus.TO_REVIEW, slot(confidence = 0.5).status)
        assertEquals(SlotStatus.IGNORED, slot(confidence = 0.49).status)
    }

    @Test
    fun `uncertain date needs review`() {
        assertEquals(SlotStatus.TO_REVIEW, slot(confidence = 0.95, dateUncertain = true).status)
    }

    @Test
    fun `only kept slots are selected by default`() {
        assertTrue(slot(confidence = 0.8).isSelected)
        assertFalse(slot(confidence = 0.6).isSelected)
        assertFalse(slot(confidence = 0.1).isSelected)
    }

    @Test
    fun `user decision overrides computed status`() {
        assertEquals(SlotStatus.KEPT, slot(confidence = 0.6).keep().status)
        assertEquals(SlotStatus.KEPT, slot(confidence = 0.2).keep().status)
        assertEquals(SlotStatus.IGNORED, slot(confidence = 0.99).ignore().status)
        assertEquals(SlotStatus.KEPT, slot(confidence = 0.99).ignore().keep().status)
    }

    @Test
    fun `keep and ignore return copies`() {
        val original = slot(confidence = 0.6)
        original.keep()
        assertEquals(SlotStatus.TO_REVIEW, original.status)
    }

    @Test
    fun `confidence out of range is rejected`() {
        assertFailsWith<IllegalArgumentException> { slot(confidence = 1.2) }
    }
}
