package fr.calevent.app.calendar

import fr.calevent.app.data.DeviceCalendar
import fr.calevent.app.data.FakeData
import fr.calevent.core.CalendarEvent
import java.time.LocalDateTime

/** In-memory repository for `@Preview`s and tests: no provider, no permission needed. */
class FakeCalendarRepository : CalendarRepository {
    override fun hasPermission(): Boolean = true
    override suspend fun listWritableCalendars(): List<DeviceCalendar> = FakeData.calendars
    override suspend fun existingEvents(calendarId: Long, from: LocalDateTime, to: LocalDateTime): List<CalendarEvent> =
        FakeData.existingEvents
    override suspend fun insert(calendarId: Long, events: List<CalendarEvent>) =
        InsertResult(events.indices.map { it + 1L }, emptyList())
    override suspend fun delete(eventIds: List<Long>) = Unit
}
