package fr.calevent.app.ui

import androidx.lifecycle.ViewModel
import fr.calevent.app.data.DeviceCalendar
import fr.calevent.app.data.FakeData
import fr.calevent.app.data.RecentPlanning
import fr.calevent.core.CalendarEvent
import fr.calevent.core.SendPlan
import fr.calevent.core.Slot
import fr.calevent.core.SlotSummary
import fr.calevent.core.displaySlots
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
 * Seeded with [FakeData] until the camera and the recognition are wired.
 */
class ScanViewModel : ViewModel() {
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

    /** Replaces the current slots (e.g. after recognition). */
    fun setSlots(slots: List<Slot>) = _uiState.update { it.copy(slots = slots) }

    /** Selected slot becomes ignored; any other slot becomes kept. */
    fun toggleSlot(id: String) = _uiState.update { s ->
        s.copy(slots = s.slots.map { if (it.id == id) (if (it.isSelected) it.ignore() else it.keep()) else it })
    }

    /** Replaces the slot with the same id (after editing it). */
    fun updateSlot(slot: Slot) = _uiState.update { s ->
        s.copy(slots = s.slots.map { if (it.id == slot.id) slot else it })
    }

    fun setShowIgnored(show: Boolean) = _uiState.update { it.copy(showIgnored = show) }

    fun selectCalendar(id: Long) = _uiState.update { it.copy(selectedCalendarId = id) }

    /** Current slot by id, or null. */
    fun slot(id: String): Slot? = _uiState.value.slots.firstOrNull { it.id == id }
}
