package fr.calevent.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SlotStatusTest {
    @Test
    fun `high confidence is kept`() {
        assertEquals(SlotStatus.KEPT, SlotStatus.from(0.8))
        assertEquals(SlotStatus.KEPT, SlotStatus.from(0.97))
    }

    @Test
    fun `medium confidence is to review`() {
        assertEquals(SlotStatus.TO_REVIEW, SlotStatus.from(0.5))
        assertEquals(SlotStatus.TO_REVIEW, SlotStatus.from(0.79))
    }

    @Test
    fun `uncertain date downgrades a confident slot to review`() {
        assertEquals(SlotStatus.TO_REVIEW, SlotStatus.from(0.95, dateUncertain = true))
    }

    @Test
    fun `low confidence is ignored even with an uncertain date`() {
        assertEquals(SlotStatus.IGNORED, SlotStatus.from(0.49))
        assertEquals(SlotStatus.IGNORED, SlotStatus.from(0.2, dateUncertain = true))
    }
}
