package fr.calevent.core

import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TeamScheduleTest {
    private val table = ShiftCodeTable.default()
    private val codes = listOf("M06", "S14", "N08", "RH", "AB", "J08")

    private fun bigSchedule(): TeamSchedule {
        val people = (1..12).map { "Personne $it" }
        val cells = people.flatMap { p -> (1..31).map { day -> ScheduleCell(p, d(2026, 12, day), codes[(day + p.length) % codes.size]) } }
        return TeamSchedule(people, cells)
    }

    @Test
    fun `no person chosen gives an empty list`() {
        assertEquals(emptyList(), bigSchedule().slotsFor(null, table))
        assertTrue(SendPlan.build(bigSchedule().slotsFor(null, table), emptyList()).canSend.not())
    }

    @Test
    fun `slots of one person are translated and sorted`() {
        val s = TeamSchedule(
            listOf("Durand C.", "Martin L."),
            listOf(
                ScheduleCell("Durand C.", d(2026, 11, 17), "RH"),
                ScheduleCell("Martin L.", d(2026, 11, 16), "M06"),
                ScheduleCell("Durand C.", d(2026, 11, 16), "N08"),
                ScheduleCell("Durand C.", d(2026, 11, 18), "  "),
            ),
        )
        val slots = s.slotsFor("Durand C.", table)
        assertEquals(listOf("Nuit (N08)", "Repos"), slots.map { it.title })
        assertTrue(slots.all { it.person == "Durand C." })
    }

    @Test
    fun `unknown code in a cell is to review`() {
        val s = TeamSchedule(listOf("A"), listOf(ScheduleCell("A", d(2026, 11, 27), "FX")))
        assertEquals(SlotStatus.TO_REVIEW, s.slotsFor("A", table).single().status)
    }

    @Test
    fun `person search ignores case and accents`() {
        val s = TeamSchedule(listOf("Hélène Durand", "Zoé Martin", "Jean Dupont"), emptyList())
        assertEquals(listOf("Hélène Durand"), s.findPeople("helene"))
        assertEquals(listOf("Zoé Martin"), s.findPeople("  ZOE  "))
        assertEquals(listOf("Hélène Durand", "Jean Dupont"), s.findPeople("DU"))
        assertEquals(3, s.findPeople("").size)
    }

    @Test
    fun `slotsFor finds a person regardless of case and accents`() {
        val s = TeamSchedule(listOf("Hélène"), listOf(ScheduleCell("Hélène", d(2026, 11, 16), "M06")))
        assertEquals(1, s.slotsFor("helene", table).size)
    }

    @Test
    fun `31 days by 12 people is correct and fast`() {
        val s = bigSchedule()
        var total = 0
        val ms = measureTimeMillis {
            s.people.forEach { p ->
                val slots = s.slotsFor(p, table)
                assertEquals(31, slots.size)
                assertEquals((1..31).map { d(2026, 12, it) }, slots.map { it.date })
                total += slots.size
            }
        }
        assertEquals(372, total)
        assertTrue(ms < 2000, "took $ms ms")
    }
}
