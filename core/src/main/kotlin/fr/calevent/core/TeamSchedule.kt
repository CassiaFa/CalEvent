package fr.calevent.core

import java.time.LocalDate

/** One cell of a team schedule: a raw code for a person on a day. */
data class ScheduleCell(
    val person: String,
    val date: LocalDate,
    val rawCode: String,
    val confidence: Double = 1.0,
)

class TeamSchedule(val people: List<String>, val cells: List<ScheduleCell>) {

    private val cellsByPerson: Map<String, List<ScheduleCell>> by lazy {
        cells.groupBy { it.person.searchKey() }
    }

    /** People whose name contains [query], ignoring case, accents and extra spaces. */
    fun findPeople(query: String): List<String> {
        val q = query.searchKey()
        return people.filter { it.searchKey().contains(q) }
    }

    /**
     * Slots of one person, sorted by date. No person chosen (null) gives an empty list:
     * nothing is selected by default, so nothing can be sent. Blank cells are skipped.
     */
    fun slotsFor(person: String?, table: ShiftCodeTable): List<Slot> {
        if (person == null) return emptyList()
        return cellsByPerson[person.searchKey()].orEmpty()
            .filter { it.rawCode.isNotBlank() }
            .sortedBy { it.date }
            .map { table.toSlot(it.rawCode, it.date, person, it.confidence) }
    }
}
