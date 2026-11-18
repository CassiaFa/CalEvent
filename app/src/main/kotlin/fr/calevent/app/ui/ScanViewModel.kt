package fr.calevent.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.calevent.app.calendar.CalendarRepository
import fr.calevent.app.calendar.ContentResolverCalendarRepository
import fr.calevent.app.calendar.FakeCalendarRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.ZoneId
import fr.calevent.app.data.DeviceCalendar
import fr.calevent.app.data.FakeData
import fr.calevent.app.data.RecentPlanning
import fr.calevent.core.CalendarEvent
import fr.calevent.core.SendPlan
import fr.calevent.core.Slot
import fr.calevent.core.SlotSummary
import fr.calevent.core.displaySlots
import fr.calevent.core.toCalendarEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** State of the calendar permission (asked when the send screen opens, never at launch). */
enum class CalendarAccess { UNKNOWN, GRANTED, DENIED, BLOCKED }

enum class SendError { LOAD_CALENDARS, INSERT }

/** What was written, kept for the success screen and for "Annuler l'ajout". */
data class SendResult(
    val createdIds: List<Long>,
    val calendarName: String,
    val duplicatesIgnored: Int,
    val failedCount: Int,
    /** Start of the first created event (epoch millis), to open the calendar app on that day. */
    val firstStartMillis: Long?,
    val undone: Boolean = false,
)

/** Everything the scan flow shows. Derived values are computed once per state. */
data class ScanUiState(
    /** All detected slots, whatever their status. */
    val slots: List<Slot>,
    /** Whether ignored slots appear in [visibleSlots]. */
    val showIgnored: Boolean = false,
    val calendars: List<DeviceCalendar>,
    val selectedCalendarId: Long? = null,
    /** Events already present in the target calendar (used to detect duplicates). */
    val existingEvents: List<CalendarEvent> = emptyList(),
    val recentPlannings: List<RecentPlanning> = emptyList(),
    val calendarAccess: CalendarAccess = CalendarAccess.GRANTED,
    /** True while calendars or existing events are being read. */
    val loading: Boolean = false,
    val sending: Boolean = false,
    val error: SendError? = null,
    /** Set once events were written; read by the success screen. */
    val sendResult: SendResult? = null,
) {
    /** Count per status ("12 retenus · 3 à revoir"). */
    val summary: SlotSummary = SlotSummary.of(slots)

    /** Slots in display order: to review first, ignored hidden unless [showIgnored]. */
    val visibleSlots: List<Slot> = displaySlots(slots, showIgnored)

    val selectedCalendar: DeviceCalendar? = calendars.firstOrNull { it.id == selectedCalendarId }

    /** What "send" will do for the current selection. */
    val sendPlan: SendPlan = SendPlan.build(slots, existingEvents)
}

/**
 * Single ViewModel shared by the whole flow (scoped to the activity, see AppNav).
 * Screens read [uiState] and call the functions below; they never mutate slots themselves.
 * Built without argument it runs on fake data (previews); [attach] switches it to the real calendar provider.
 */
class ScanViewModel : ViewModel() {
    private var repository: CalendarRepository = FakeCalendarRepository()
    private var prefs: android.content.SharedPreferences? = null
    private var existingJob: Job? = null

    private val _uiState = MutableStateFlow(
        ScanUiState(
            slots = FakeData.weeklySlots,
            calendars = FakeData.calendars,
            selectedCalendarId = FakeData.calendars.first().id,
            existingEvents = FakeData.existingEvents,
            recentPlannings = FakeData.recentPlannings,
        ),
    )
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /** Binds the real calendar provider (idempotent). Drops the fake calendars and existing events. */
    fun attach(context: Context) {
        if (repository is ContentResolverCalendarRepository) return
        repository = ContentResolverCalendarRepository(context)
        prefs = context.applicationContext.getSharedPreferences("calevent", Context.MODE_PRIVATE)
        _uiState.update {
            it.copy(calendars = emptyList(), selectedCalendarId = null, existingEvents = emptyList(), calendarAccess = CalendarAccess.UNKNOWN)
        }
    }

    /** Called when the send screen opens and each time the app resumes: re-checks the permission and reloads. */
    fun refreshAccess() {
        if (repository.hasPermission()) {
            if (_uiState.value.calendarAccess != CalendarAccess.GRANTED || _uiState.value.calendars.isEmpty()) {
                _uiState.update { it.copy(calendarAccess = CalendarAccess.GRANTED) }
                loadCalendars()
            } else if (!_uiState.value.sending && _uiState.value.sendResult == null) {
                loadExisting()
            }
        } else if (_uiState.value.calendarAccess == CalendarAccess.GRANTED) {
            _uiState.update { it.copy(calendarAccess = CalendarAccess.UNKNOWN, calendars = emptyList(), selectedCalendarId = null, existingEvents = emptyList()) }
        }
    }

    /** Result of the system permission dialog; [blocked] means "don't ask again". */
    fun onPermissionResult(granted: Boolean, blocked: Boolean) {
        if (granted) refreshAccess()
        else _uiState.update { it.copy(calendarAccess = if (blocked) CalendarAccess.BLOCKED else CalendarAccess.DENIED) }
    }

    private fun loadCalendars() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val list = runCatching { repository.listWritableCalendars() }.getOrNull()
            if (list == null) {
                _uiState.update { it.copy(loading = false, error = SendError.LOAD_CALENDARS) }
                return@launch
            }
            val last = prefs?.getLong(KEY_LAST_CALENDAR, -1L)?.takeIf { it >= 0 }
            val current = _uiState.value.selectedCalendarId
            val selected = listOf(current, last).firstOrNull { id -> id != null && list.any { it.id == id } }
            _uiState.update { it.copy(calendars = list, selectedCalendarId = selected, loading = false) }
            loadExisting()
        }
    }

    /** Reads the events already present in the selected calendar, only over the period of the selected slots. */
    private fun loadExisting() {
        existingJob?.cancel()
        val s = _uiState.value
        val calendarId = s.selectedCalendarId
        val events = s.slots.filter { it.isSelected }.map { it.toCalendarEvent() }
        if (calendarId == null || events.isEmpty()) {
            _uiState.update { it.copy(existingEvents = emptyList(), loading = false) }
            return
        }
        _uiState.update { it.copy(loading = true) }
        val from = events.minOf { it.start }
        val to = events.maxOf { it.end }
        existingJob = viewModelScope.launch {
            val existing = runCatching { repository.existingEvents(calendarId, from, to) }.getOrNull()
            _uiState.update {
                if (existing == null) it.copy(loading = false, error = SendError.LOAD_CALENDARS)
                else it.copy(existingEvents = existing, loading = false)
            }
        }
    }

    /** Writes the events of the current plan; [onDone] runs on success (some events may still have failed, see the result). */
    fun send(onDone: () -> Unit) {
        val s = _uiState.value
        val calendar = s.selectedCalendar ?: return
        val plan = s.sendPlan
        if (!plan.canSend || s.sending || s.loading) return
        _uiState.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { repository.insert(calendar.id, plan.events) }.getOrNull()
            if (result == null || result.createdIds.isEmpty()) {
                _uiState.update { it.copy(sending = false, error = SendError.INSERT) }
                return@launch
            }
            prefs?.edit()?.putLong(KEY_LAST_CALENDAR, calendar.id)?.apply()
            val zone = ZoneId.systemDefault()
            val first = (plan.events - result.failed.toSet()).minByOrNull { it.start }
            _uiState.update {
                it.copy(
                    sending = false,
                    sendResult = SendResult(
                        createdIds = result.createdIds,
                        calendarName = calendar.name,
                        duplicatesIgnored = plan.duplicates.alreadyPresent.size,
                        failedCount = result.failed.size,
                        firstStartMillis = first?.let { e ->
                            if (e.allDay) e.start.atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                            else e.start.atZone(zone).toInstant().toEpochMilli()
                        },
                    ),
                )
            }
            onDone()
        }
    }

    /** Deletes the events just created ("Annuler l'ajout"). Keeps the result (and allows a retry) if deletion fails. */
    fun undoSend() {
        val result = _uiState.value.sendResult ?: return
        if (result.undone) return
        viewModelScope.launch {
            val ok = runCatching { repository.delete(result.createdIds) }.isSuccess
            _uiState.update {
                if (ok) it.copy(sendResult = result.copy(undone = true), error = null) else it.copy(error = SendError.INSERT)
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    /** Replaces the current slots (e.g. after recognition). */
    fun setSlots(slots: List<Slot>) = _uiState.update { it.copy(slots = slots, sendResult = null, error = null) }

    /** Selected slot becomes ignored; any other slot becomes kept. */
    fun toggleSlot(id: String) = _uiState.update { s ->
        s.copy(slots = s.slots.map { if (it.id == id) (if (it.isSelected) it.ignore() else it.keep()) else it })
    }

    /** Replaces the slot with the same id (after editing it). */
    fun updateSlot(slot: Slot) = _uiState.update { s ->
        s.copy(slots = s.slots.map { if (it.id == slot.id) slot else it })
    }

    fun setShowIgnored(show: Boolean) = _uiState.update { it.copy(showIgnored = show) }

    fun selectCalendar(id: Long) {
        _uiState.update { it.copy(selectedCalendarId = id, error = null) }
        loadExisting()
    }

    /** Current slot by id, or null. */
    fun slot(id: String): Slot? = _uiState.value.slots.firstOrNull { it.id == id }

    private companion object {
        const val KEY_LAST_CALENDAR = "last_calendar_id"
    }
}
