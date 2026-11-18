package fr.calevent.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlotMemoryTest {
    private val a = slot(id = "Ana|2026-11-16|M06")
    private val b = slot(id = "Ana|2026-11-17|N08", confidence = 0.6)
    private val baseline = listOf(a, b)

    @Test fun `decisions are restored after a rebuild`() {
        val current = listOf(a.ignore(), b.keep())
        val memory = SlotMemory().capture(baseline, current)
        assertEquals(current, memory.restore(baseline))
    }

    @Test fun `untouched slots leave no trace`() {
        val memory = SlotMemory().capture(baseline, baseline)
        assertEquals(0, memory.size)
        assertEquals(baseline, memory.restore(baseline))
    }

    @Test fun `edits are restored with their decision`() {
        val edited = a.copy(title = "Matin décalé", start = t(7)).keep()
        val memory = SlotMemory().capture(baseline, listOf(edited, b))
        assertEquals(listOf(edited, b), memory.restore(baseline))
    }

    @Test fun `a decision only change follows a refreshed baseline`() {
        val memory = SlotMemory().capture(baseline, listOf(a.ignore(), b))
        val refreshed = a.copy(title = "Matin (M06) mis à jour")
        assertEquals(refreshed.ignore(), memory.restore(listOf(refreshed)).single())
    }

    @Test fun `other people are kept when capturing one`() {
        val c = slot(id = "Bob|2026-11-16|M06")
        val memory = SlotMemory().capture(listOf(c), listOf(c.ignore())).capture(baseline, listOf(a.keep(), b))
        assertEquals(c.ignore(), memory.restore(listOf(c)).single())
        assertEquals(a.keep(), memory.restore(baseline).first())
    }

    @Test fun `clearing a decision forgets it`() {
        val memory = SlotMemory().capture(baseline, listOf(a.ignore(), b)).capture(baseline, baseline)
        assertEquals(baseline, memory.restore(baseline))
        assertEquals(0, memory.size)
    }

    @Test fun `names match ignoring case and accents`() {
        assertTrue(sameName("Élodie  M.", "elodie m."))
        assertEquals("Élodie M.", findPerson(listOf("Sam", "Élodie M."), "elodie m."))
        assertNull(findPerson(listOf("Sam"), null))
    }
}
