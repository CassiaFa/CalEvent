package fr.calevent.app.calendar

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import fr.calevent.app.R
import fr.calevent.app.data.DeviceCalendar
import fr.calevent.core.CalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Outcome of [CalendarRepository.insert]: ids of the created events and the events that failed. */
data class InsertResult(val createdIds: List<Long>, val failed: List<CalendarEvent>)

/** The phone's calendar provider. Never touches the network: sync adapters (e.g. DAVx5) do the rest. */
interface CalendarRepository {
    /** True when both READ_CALENDAR and WRITE_CALENDAR are granted. */
    fun hasPermission(): Boolean

    /** Visible calendars the user can add events to. Requires the permission. */
    suspend fun listWritableCalendars(): List<DeviceCalendar>

    /** Events (recurrences expanded) overlapping [from]..[to] in calendar [calendarId]; nothing outside that range is read. */
    suspend fun existingEvents(calendarId: Long, from: LocalDateTime, to: LocalDateTime): List<CalendarEvent>

    /** Inserts [events] in the phone's time zone (all-day events in UTC, as CalendarContract requires). */
    suspend fun insert(calendarId: Long, events: List<CalendarEvent>): InsertResult

    /** Deletes events previously created by [insert]. */
    suspend fun delete(eventIds: List<Long>)
}

class ContentResolverCalendarRepository(context: Context) : CalendarRepository {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override fun hasPermission(): Boolean =
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all {
            ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }

    override suspend fun listWritableCalendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.OWNER_ACCOUNT,
        )
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        val rows = ArrayList<Pair<Boolean, DeviceCalendar>>()
        resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null)?.use { c ->
            while (c.moveToNext()) {
                val accountName = c.getString(2).orEmpty()
                val primary = accountName.isNotEmpty() && accountName == c.getString(5)
                val color = c.getInt(4).toLong() and 0xFFFFFFFFL or 0xFF000000L
                rows += primary to DeviceCalendar(
                    id = c.getLong(0),
                    name = c.getString(1).orEmpty().ifBlank { accountName },
                    account = accountLabel(c.getString(3).orEmpty(), accountName),
                    colorArgb = color,
                )
            }
        }
        rows.sortedWith(compareByDescending<Pair<Boolean, DeviceCalendar>> { it.first }.thenBy { it.second.account }.thenBy { it.second.name })
            .map { it.second }
    }

    private fun accountLabel(type: String, name: String): String = when (type) {
        "bitfire.at.davdroid" -> appContext.getString(R.string.send_account_davx, name)
        "com.google" -> appContext.getString(R.string.send_account_google, name)
        CalendarContract.ACCOUNT_TYPE_LOCAL -> appContext.getString(R.string.send_account_local)
        else -> name.ifBlank { type }
    }

    override suspend fun existingEvents(calendarId: Long, from: LocalDateTime, to: LocalDateTime): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            // One day of margin each side: all-day instances are stored in UTC.
            val begin = from.minusDays(1).atZone(zone).toInstant().toEpochMilli()
            val end = to.plusDays(1).atZone(zone).toInstant().toEpochMilli()
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, begin)
                ContentUris.appendId(it, end)
            }.build()
            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION,
            )
            val out = ArrayList<CalendarEvent>()
            resolver.query(uri, projection, "${CalendarContract.Instances.CALENDAR_ID} = ?", arrayOf(calendarId.toString()), null)
                ?.use { c -> while (c.moveToNext()) out += c.toEvent(zone) }
            out
        }

    private fun Cursor.toEvent(zone: ZoneId): CalendarEvent {
        val allDay = getInt(3) == 1
        val tz: ZoneId = if (allDay) ZoneOffset.UTC else zone
        return CalendarEvent(
            title = getString(0).orEmpty(),
            start = LocalDateTime.ofInstant(Instant.ofEpochMilli(getLong(1)), tz),
            end = LocalDateTime.ofInstant(Instant.ofEpochMilli(getLong(2)), tz),
            allDay = allDay,
            location = getString(4),
        )
    }

    override suspend fun insert(calendarId: Long, events: List<CalendarEvent>): InsertResult = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val created = ArrayList<Long>()
        val failed = ArrayList<CalendarEvent>()
        for (chunk in events.chunked(BATCH_SIZE)) {
            val ids = runCatching { applyChunk(calendarId, chunk, zone) }.getOrNull()
            if (ids != null && ids.size == chunk.size) {
                created += ids
            } else {
                // The batch failed (CalendarProvider batches are transactional): retry one by one to isolate the culprit.
                chunk.forEach { e ->
                    val id = runCatching { applyChunk(calendarId, listOf(e), zone).singleOrNull() }.getOrNull()
                    if (id != null) created += id else failed += e
                }
            }
        }
        InsertResult(created, failed)
    }

    private fun applyChunk(calendarId: Long, chunk: List<CalendarEvent>, zone: ZoneId): List<Long> {
        val ops = chunk.map { ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI).withValues(values(calendarId, it, zone)).build() }
        val results = resolver.applyBatch(CalendarContract.AUTHORITY, ArrayList(ops))
        return results.mapNotNull { it.uri?.let(ContentUris::parseId) }
    }

    private fun values(calendarId: Long, e: CalendarEvent, zone: ZoneId) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.TITLE, e.title)
        if (e.allDay) {
            put(CalendarContract.Events.ALL_DAY, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            put(CalendarContract.Events.DTSTART, e.start.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
            put(CalendarContract.Events.DTEND, e.end.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        } else {
            put(CalendarContract.Events.ALL_DAY, 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            put(CalendarContract.Events.DTSTART, e.start.atZone(zone).toInstant().toEpochMilli())
            put(CalendarContract.Events.DTEND, e.end.atZone(zone).toInstant().toEpochMilli())
        }
        e.location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
    }

    override suspend fun delete(eventIds: List<Long>): Unit = withContext(Dispatchers.IO) {
        eventIds.chunked(BATCH_SIZE).forEach { chunk ->
            val ops = chunk.map {
                ContentProviderOperation.newDelete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, it)).build()
            }
            resolver.applyBatch(CalendarContract.AUTHORITY, ArrayList(ops))
        }
    }

    private companion object {
        const val BATCH_SIZE = 50
    }
}
