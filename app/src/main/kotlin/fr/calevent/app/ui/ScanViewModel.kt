package fr.calevent.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.calevent.app.calendar.CalendarRepository
import fr.calevent.app.calendar.ContentResolverCalendarRepository
import fr.calevent.app.calendar.FakeCalendarRepository
import android.graphics.Bitmap
import fr.calevent.app.analysis.AnalysisStep
import fr.calevent.app.analysis.ScanAnalyzer
import fr.calevent.app.capture.CapturedPhotoStore
import fr.calevent.core.ShiftCodeTable
import fr.calevent.core.resolveUnknownCodes
import fr.calevent.core.parse.ParseResult
import fr.calevent.core.parse.ParseWarning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import fr.calevent.app.data.DeviceCalendar
import fr.calevent.app.settings.Settings
import fr.calevent.app.data.FakeData
import fr.calevent.app.history.HistoryRepository
import fr.calevent.core.HistoryEntry
import fr.calevent.core.CalendarEvent
import fr.calevent.core.SendPlan
import fr.calevent.core.Slot
import fr.calevent.core.UserDecision
import fr.calevent.core.SlotMemory
import fr.calevent.core.SlotSummary
import fr.calevent.core.findPerson
import fr.calevent.core.displaySlots
import fr.calevent.core.toCalendarEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** State of the calendar permission (asked when the send screen opens, never at launch). */
enum class CalendarAccess { UNKNOWN, GRANTED, DENIED, BLOCKED }

enum class SendError { LOAD_CALENDARS, INSERT }

/** Why the analysis ended without usable slots. */
enum class AnalysisFailure { NO_SLOTS, ERROR }

/** Progress of the photo analysis (see [ScanViewModel.startAnalysis]). */
sealed interface AnalysisState {
    data object Idle : AnalysisState

    /** [step] is an [AnalysisStep] constant. */
    data class Running(val step: Int) : AnalysisState
    data object Done : AnalysisState

    /** [textRead]: some text was recognized, so "Voir quand même" makes sense. */
    data class Failed(val reason: AnalysisFailure, val textRead: Boolean) : AnalysisState
}

/** What was written, kept for the success screen and for "Annuler l'ajout". */
data class SendResult(
    val createdIds: List<Long>,
    val calendarName: String,
    val duplicatesIgnored: Int,
    val failedCount: Int,
    /** Start of the first created event (epoch millis), to open the calendar app on that day. */
    val firstStartMillis: Long?,
    val undone: Boolean = false,
    /** Id of the history entry written for this send (removed again on undo). */
    val historyId: String? = null,
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
    val calendarAccess: CalendarAccess = CalendarAccess.GRANTED,
    /** True while calendars or existing events are being read. */
    val loading: Boolean = false,
    val sending: Boolean = false,
    val error: SendError? = null,
    /** Set once events were written; read by the success screen. */
    val sendResult: SendResult? = null,
    val analysis: AnalysisState = AnalysisState.Idle,
    /** Parser output of the current photo; null for fake data. */
    val parse: ParseResult? = null,
    /** Problems noticed while reading the photo (rotation, blur, tilt...). */
    val warnings: List<ParseWarning> = emptyList(),
    /** Title of the detected kind of schedule, e.g. "Calendrier mural". */
    val kindLabel: String? = null,
    /** People of a team schedule, in the order of the photo; empty otherwise. */
    val people: List<String> = emptyList(),
    /** Number of shifts of each person of a team schedule (shown in the person picker). */
    val peopleCounts: Map<String, Int> = emptyMap(),
    /** Name stored as "Moi" in the settings, if any. */
    val me: String? = null,
    /** Person chosen on a team schedule; null (the default) means nobody, hence no slots. */
    val selectedPerson: String? = null,
    /** Réglages › Ignorer les doublons: when false, existing events are not compared. */
    val skipDuplicates: Boolean = true,
) {
    /** True when a person must be chosen to see slots. */
    val needsPerson: Boolean = people.isNotEmpty() && selectedPerson == null

    /** Distinct shift codes still missing from the code table, in order of appearance. */
    val unknownCodes: List<String> = slots.mapNotNull { it.unknownCode }.distinctBy { it.uppercase() }

    /** Count per status ("12 retenus · 3 à revoir"). */
    val summary: SlotSummary = SlotSummary.of(slots)

    /** Slots in display order: to review first, ignored hidden unless [showIgnored]. */
    val visibleSlots: List<Slot> = displaySlots(slots, showIgnored)

    val selectedCalendar: DeviceCalendar? = calendars.firstOrNull { it.id == selectedCalendarId }

    /** What "send" will do for the current selection. */
    val sendPlan: SendPlan = SendPlan.build(slots, if (skipDuplicates) existingEvents else emptyList())
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
    private var analysisJob: Job? = null
    private val analyzer = ScanAnalyzer()
    private var codes: ShiftCodeTable = ShiftCodeTable.default()

    /** Decisions and edits per slot for the whole scan session (kept when switching person). */
    private var memory = SlotMemory()

    private val _photoBitmap = MutableStateFlow<Bitmap?>(null)

    /** Image the current slots were read on (their regions are fractions of it); null with fake data. */
    val photoBitmap: StateFlow<Bitmap?> = _photoBitmap.asStateFlow()

    private val _photoTurn = MutableStateFlow(PhotoTurn.None)

    /** How to turn [photoBitmap] to read it upright (from the parser); [PhotoTurn.None] with fake data. */
    val photoTurn: StateFlow<PhotoTurn> = _photoTurn.asStateFlow()

    private val _uiState = MutableStateFlow(
        ScanUiState(
            slots = FakeData.weeklySlots,
            calendars = FakeData.calendars,
            selectedCalendarId = FakeData.calendars.first().id,
            existingEvents = FakeData.existingEvents,
        ),
    )
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var history: HistoryRepository? = null
    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())

    /** Last sends, newest first (home list and history screen). Empty until [attach]. */
    val historyEntries: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    /** Binds the real calendar provider (idempotent). Drops the fake calendars and existing events. */
    fun attach(context: Context) {
        if (repository is ContentResolverCalendarRepository) return
        repository = ContentResolverCalendarRepository(context)
        prefs = context.applicationContext.getSharedPreferences("calevent", Context.MODE_PRIVATE)
        val repo = HistoryRepository.get(context).also { history = it }
        viewModelScope.launch { repo.load(); repo.entries.collect { _history.value = it } }
        _uiState.update {
            it.copy(calendars = emptyList(), selectedCalendarId = null, existingEvents = emptyList(), calendarAccess = CalendarAccess.UNKNOWN)
        }
    }

    private var settings = Settings()

    /** Applies the user settings: default calendar, duplicate handling. Safe to call at every change. */
    fun applySettings(new: Settings) {
        val old = settings
        settings = new
        val before = _uiState.value.selectedCalendarId
        _uiState.update { s ->
            val preferred = new.defaultCalendarId?.takeIf { id -> !new.alwaysAskCalendar && s.calendars.any { it.id == id } }
            val changed = new.defaultCalendarId != old.defaultCalendarId || new.alwaysAskCalendar != old.alwaysAskCalendar
            s.copy(
                skipDuplicates = new.skipDuplicates,
                me = new.me,
                selectedCalendarId = if (preferred != null && (changed || s.selectedCalendarId == null)) preferred else s.selectedCalendarId,
            )
        }
        if (_uiState.value.selectedCalendarId != before) loadExistingIfReady()
    }

    /**
     * Uses the user's code table from now on and recomputes the slots whose unknown code it now defines
     * (title, times, status), keeping the user's decisions. The photo is not read again.
     */
    fun applyCodeTable(table: ShiftCodeTable) {
        if (table == codes) return
        captureCurrent()
        codes = table
        val before = _uiState.value.slots
        val after = table.resolveUnknownCodes(before)
        if (after != before) {
            _uiState.update { it.copy(slots = after, sendResult = null, error = null) }
            loadExistingIfReady()
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
            val default = settings.defaultCalendarId
            val order = if (settings.alwaysAskCalendar) listOf(current, last, default) else listOf(current, default, last)
            val selected = order.firstOrNull { id -> id != null && list.any { it.id == id } }
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
            val written = (plan.events - result.failed.toSet()).ifEmpty { plan.events }
            val first = written.minByOrNull { it.start }
            val historyId = java.util.UUID.randomUUID().toString()
            history?.let { repo ->
                val isTeam = s.people.isNotEmpty()
                val entry = HistoryEntry(
                    id = historyId, createdAtMillis = System.currentTimeMillis(), kindLabel = s.kindLabel,
                    isTeam = isTeam, personName = if (isTeam) s.selectedPerson else null,
                    addedCount = result.createdIds.size, calendarName = calendar.name,
                    from = written.minOf { it.start.toLocalDate() }, to = written.maxOf { it.end.minusSeconds(1).toLocalDate() },
                    hasThumbnail = false, eventIds = result.createdIds,
                )
                // History is best effort: a failure here must not hide that the events were written.
                runCatching { repo.add(entry, _photoBitmap.value) }
            }
            _uiState.update {
                it.copy(
                    sending = false,
                    sendResult = SendResult(
                        createdIds = result.createdIds,
                        calendarName = calendar.name,
                        duplicatesIgnored = plan.duplicates.alreadyPresent.size,
                        failedCount = result.failed.size,
                        historyId = historyId,
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
            if (ok) result.historyId?.let { id -> history?.let { runCatching { it.remove(id) } } }
            _uiState.update {
                if (ok) it.copy(sendResult = result.copy(undone = true), error = null) else it.copy(error = SendError.INSERT)
            }
        }
    }

    /**
     * Reads the current photo ([CapturedPhotoStore]) on a background thread and fills the state
     * (slots, warnings, people). Replaces any analysis in progress. Observe [ScanUiState.analysis].
     */
    fun startAnalysis(context: Context) {
        analysisJob?.cancel()
        memory = SlotMemory()
        val app = context.applicationContext
        _uiState.update {
            it.copy(
                analysis = AnalysisState.Running(AnalysisStep.DETECT), slots = emptyList(), parse = null, warnings = emptyList(),
                kindLabel = null, people = emptyList(), peopleCounts = emptyMap(), selectedPerson = null, sendResult = null, error = null, existingEvents = emptyList(),
            )
        }
        analysisJob = viewModelScope.launch {
            try {
                val out = analyzer.analyze(app, codes, LocalDate.now()) { step ->
                    _uiState.update { if (it.analysis is AnalysisState.Running) it.copy(analysis = AnalysisState.Running(step)) else it }
                }
                val result = out.result
                _photoBitmap.value = out.bitmap
                _photoTurn.value = PhotoTurn(result.rotationDegrees, result.skewDegrees)
                val failed = ParseWarning.NO_SLOTS in result.warnings
                val team = result.kind.isTeam
                val preselected = if (team) findPerson(result.people, settings.me) else null
                _uiState.update {
                    it.copy(
                        parse = result,
                        warnings = result.warnings,
                        kindLabel = result.kind.label,
                        people = if (team) result.people else emptyList(),
                        peopleCounts = if (team) result.people.associateWith { p -> result.slotsFor(p, codes).size } else emptyMap(),
                        selectedPerson = preselected,
                        slots = if (team) result.slotsFor(preselected, codes) else result.slots,
                        analysis = if (failed) AnalysisState.Failed(AnalysisFailure.NO_SLOTS, textRead = out.lineCount > 0) else AnalysisState.Done,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(analysis = AnalysisState.Failed(AnalysisFailure.ERROR, textRead = false)) }
            }
        }
    }

    /** Stops the analysis in progress (Cancel button). */
    fun cancelAnalysis() {
        analysisJob?.cancel()
        _uiState.update { if (it.analysis is AnalysisState.Running) it.copy(analysis = AnalysisState.Idle) else it }
    }

    /**
     * Turns the photo by a quarter turn (clockwise when [clockwise]) or a half turn ([halfTurn]);
     * the caller then restarts the analysis.
     */
    fun rotatePhoto(clockwise: Boolean, halfTurn: Boolean = false) {
        if (halfTurn) {
            CapturedPhotoStore.rotateRight(); CapturedPhotoStore.rotateRight()
        } else if (clockwise) CapturedPhotoStore.rotateRight() else CapturedPhotoStore.rotateLeft()
    }

    /**
     * Chooses (or clears with null) the person of a team schedule; their slots replace the current ones.
     * Decisions and edits made on the previous person are remembered and restored when coming back.
     */
    fun selectPerson(name: String?) {
        val parse = _uiState.value.parse ?: return
        captureCurrent()
        val slots = memory.restore(parse.slotsFor(name, codes))
        _uiState.update { it.copy(selectedPerson = name, slots = slots, sendResult = null, error = null, existingEvents = emptyList()) }
        loadExistingIfReady()
    }

    /** Remembers what the user changed on the slots currently shown (team schedules only). */
    private fun captureCurrent() {
        val s = _uiState.value
        val parse = s.parse ?: return
        val person = s.selectedPerson ?: return
        memory = memory.capture(parse.slotsFor(person, codes), s.slots)
    }

    private fun loadExistingIfReady() {
        if (_uiState.value.calendarAccess == CalendarAccess.GRANTED && _uiState.value.selectedCalendarId != null) loadExisting()
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    /** Replaces the current slots (e.g. after recognition). */
    fun setSlots(slots: List<Slot>) = _uiState.update { it.copy(slots = slots, sendResult = null, error = null) }

    /** Selected slot becomes ignored; any other slot becomes kept. */
    fun toggleSlot(id: String) = _uiState.update { s ->
        s.copy(slots = s.slots.map { if (it.id == id) (if (it.isSelected) it.ignore() else it.keep()) else it })
    }

    /** Sets (or, with null, clears) the user's decision on a slot; used by swipes and their undo. */
    fun setDecision(id: String, decision: UserDecision?) = _uiState.update { s ->
        s.copy(slots = s.slots.map { if (it.id == id) it.copy(decision = decision) else it })
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

/** Clockwise [rotationDegrees] (0/90/180/270) that makes the analysed photo upright, then the remaining row tilt. */
data class PhotoTurn(val rotationDegrees: Int, val skewDegrees: Double) {
    companion object { val None = PhotoTurn(0, 0.0) }
}
