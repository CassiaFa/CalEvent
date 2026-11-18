package fr.calevent.core.parse

import fr.calevent.core.Region
import fr.calevent.core.ShiftCodeTable
import fr.calevent.core.Slot
import fr.calevent.core.SlotStatus
import fr.calevent.core.TeamSchedule
import fr.calevent.core.searchKey
import java.time.LocalDate

/** Kind of paper schedule recognized on the photo. [label] is shown to the user. */
enum class ScheduleKind(val label: String) {
    WALL_CALENDAR("Calendrier mural"),
    WEEKLY("Planning de la semaine"),
    TEAM_CODES("Planning d'équipe à codes"),
    TEAM_HOURS("Planning d'équipe horaire"),
    UNKNOWN("Planning non reconnu");

    val isTeam: Boolean get() = this == TEAM_CODES || this == TEAM_HOURS
}

/** Problem noticed while reading the photo; [message] is shown to the user. */
enum class ParseWarning(val message: String) {
    ROTATED_QUARTER_TURN("La photo semble tournée d'un quart de tour"),
    UPSIDE_DOWN("La photo semble à l'envers"),
    TILTED("La photo est très inclinée : reprenez-la bien en face"),
    LOW_TEXT("Peu de texte lisible : la photo est peut-être floue"),
    NO_SLOTS("Aucun créneau détecté"),
}

/** Extra data of one team cell that [TeamSchedule] cannot hold. */
internal data class CellDetail(val region: Region?, val dateUncertain: Boolean, val reason: String?)

/**
 * Result of [ScheduleParser.parse].
 * - Team schedules ([ScheduleKind.isTeam]): nothing is selected until a person is chosen; use
 *   [slotsFor]. For [ScheduleKind.TEAM_CODES], [team] holds the raw codes.
 * - Other kinds: [slots] holds every slot found.
 */
class ParseResult internal constructor(
    val kind: ScheduleKind,
    /** How strongly the layout matches [kind], 0..1. */
    val kindScore: Double,
    /** Slots of a calendar or weekly schedule; for [ScheduleKind.TEAM_HOURS], everyone's slots. */
    val slots: List<Slot>,
    /** Raw codes of a [ScheduleKind.TEAM_CODES] schedule, null otherwise. */
    val team: TeamSchedule?,
    /** People of a team schedule, in the order of the photo. */
    val people: List<String>,
    val warnings: List<ParseWarning>,
    /** Clockwise rotation (0, 90, 180, 270) that makes the photo upright. */
    val rotationDegrees: Int,
    /** Remaining tilt of the text rows, in degrees. */
    val skewDegrees: Double,
    private val cellDetails: Map<String, CellDetail> = emptyMap(),
) {
    /** First and last dates found, or null. */
    val period: ClosedRange<LocalDate>? by lazy {
        val dates = slots.map { it.date } + team?.cells?.map { it.date }.orEmpty()
        if (dates.isEmpty()) null else dates.min()..dates.max()
    }

    /**
     * Slots shown for [person]: for a team schedule, only that person's (none when [person] is
     * null); otherwise all [slots]. Team-code slots get their photo region and date doubts here.
     */
    fun slotsFor(person: String?, codes: ShiftCodeTable): List<Slot> = when (kind) {
        ScheduleKind.TEAM_CODES -> {
            if (person == null || team == null) emptyList()
            else {
                val key = person.searchKey()
                team.cells.filter { it.person.searchKey() == key && it.rawCode.isNotBlank() }
                    .sortedBy { it.date }
                    .map { cell ->
                        val slot = codes.toSlot(cell.rawCode, cell.date, cell.person, cell.confidence)
                        val detail = cellDetails[cellKey(cell.person, cell.date, cell.rawCode)]
                        if (detail == null) slot else {
                            val enriched = slot.copy(region = detail.region, dateUncertain = detail.dateUncertain)
                            val reason = enriched.reviewReason ?: detail.reason
                            enriched.copy(reviewReason = reason.takeIf { enriched.status != SlotStatus.KEPT })
                        }
                    }
            }
        }
        ScheduleKind.TEAM_HOURS ->
            if (person == null) emptyList()
            else person.searchKey().let { key -> slots.filter { it.person?.searchKey() == key } }
        else -> slots
    }

    override fun toString(): String =
        "ParseResult(kind=$kind, score=${"%.2f".format(kindScore)}, slots=${slots.size}, " +
            "cells=${team?.cells?.size ?: 0}, people=${people.size}, warnings=$warnings, rotation=$rotationDegrees)"

    internal companion object {
        fun cellKey(person: String, date: LocalDate, rawCode: String) = "${person.searchKey()}|$date|${rawCode.trim()}"
    }
}
