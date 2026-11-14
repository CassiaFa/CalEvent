package fr.calevent.core

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** An event as written to the phone calendar. All-day events end at the next midnight (exclusive). */
data class CalendarEvent(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val location: String? = null,
)

/** Duration given to a slot that has a start time but no end time. */
val DEFAULT_SLOT_DURATION: Duration = Duration.ofHours(1)

/**
 * Converts a slot into a calendar event.
 * - all-day (or no start time at all): midnight to next midnight
 * - night ([Slot.endsNextDay]) or end <= start: ends the day after
 * - start without end: lasts [DEFAULT_SLOT_DURATION] (1 hour)
 */
fun Slot.toCalendarEvent(): CalendarEvent {
    val midnight = date.atStartOfDay()
    if (allDay || start == null) {
        return CalendarEvent(title, midnight, midnight.plusDays(1), true, location)
    }
    val startAt = date.atTime(start)
    val endAt = when {
        end == null -> startAt.plus(DEFAULT_SLOT_DURATION)
        endsNextDay || !end.isAfter(start) -> date.plusDays(1).atTime(end)
        else -> date.atTime(end)
    }
    return CalendarEvent(title, startAt, endAt, false, location)
}
