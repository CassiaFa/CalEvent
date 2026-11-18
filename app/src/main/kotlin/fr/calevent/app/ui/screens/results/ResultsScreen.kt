package fr.calevent.app.ui.screens.results

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import fr.calevent.core.UserDecision
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.calevent.app.R
import fr.calevent.app.data.FakeData
import fr.calevent.app.ui.ScanUiState
import fr.calevent.app.ui.ScanViewModel
import fr.calevent.app.ui.components.Banner
import fr.calevent.app.ui.components.BannerKind
import fr.calevent.app.ui.components.BottomActionBar
import fr.calevent.app.ui.components.PrimaryButton
import fr.calevent.app.ui.components.ScreenScaffold
import fr.calevent.app.ui.components.SecondaryButton
import fr.calevent.app.ui.components.SegmentOption
import fr.calevent.app.ui.components.SegmentedControl
import fr.calevent.app.ui.components.StatusChip
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode
import fr.calevent.core.Slot
import fr.calevent.core.parse.ParseWarning
import fr.calevent.core.SlotStatus
import fr.calevent.core.rangeLabel

/**
 * Results: header, warnings, person picker (team schedules), Agenda / Photo switch, status counters,
 * the slot list (or the photo with zones) and the "add N slots" bar. Tapping a slot opens [SlotSheet].
 *
 * @param onReanalyze called after the photo was turned (Pivoter): the analysis must run again.
 */
@Composable
fun ResultsScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onReanalyze: () -> Unit = {},
    onDefineCode: (String) -> Unit = {},
    onSetMe: (String?) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val photo by viewModel.photoBitmap.collectAsState()
    ResultsContent(
        state = state,
        onBack = onBack,
        onNext = onNext,
        onToggle = viewModel::toggleSlot,
        onUpdate = viewModel::updateSlot,
        onSetDecision = viewModel::setDecision,
        onShowIgnored = viewModel::setShowIgnored,
        onRotate = { clockwise, half ->
            viewModel.rotatePhoto(clockwise, half)
            onReanalyze()
        },
        onSelectPerson = viewModel::selectPerson,
        onDefineCode = onDefineCode,
        onSetMe = onSetMe,
        photo = photo,
    )
}

/** Stateless body of the screen (previews use it directly). */
@Composable
internal fun ResultsContent(
    state: ScanUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onToggle: (String) -> Unit,
    onUpdate: (Slot) -> Unit,
    onShowIgnored: (Boolean) -> Unit,
    onRotate: (clockwise: Boolean, halfTurn: Boolean) -> Unit,
    onSelectPerson: (String?) -> Unit = {},
    onSetDecision: (String, UserDecision?) -> Unit = { _, _ -> },
    onDefineCode: (String) -> Unit = {},
    onSetMe: (String?) -> Unit = {},
    photo: Bitmap? = null,
    initialTab: Int = 0,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingPerson by rememberSaveable { mutableStateOf(false) }
    val summary = state.summary
    val slots = state.slots
    val first = slots.minOfOrNull { it.date }
    val last = slots.maxOfOrNull { it.date }
    val subtitle = if (first != null && last != null) {
        stringResource(
            R.string.results_subtitle,
            rangeLabel(first, last),
            pluralStringResource(R.plurals.results_slots_read, slots.size, slots.size),
        )
    } else {
        ""
    }

    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ignoredMessage = stringResource(R.string.slot_ignored_snackbar)
    val undoLabel = stringResource(R.string.slot_ignored_undo)
    val currentSlots by rememberUpdatedState(slots)
    fun swipe(id: String, decision: UserDecision) {
        val previous = currentSlots.firstOrNull { it.id == id }?.decision
        onSetDecision(id, decision)
        if (decision == UserDecision.IGNORE) {
            scope.launch {
                snackbarHost.currentSnackbarData?.dismiss()
                val result = snackbarHost.showSnackbar(ignoredMessage, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) onSetDecision(id, previous)
            }
        }
    }

    ScreenScaffold(
        title = state.kindLabel ?: stringResource(R.string.results_title),
        onBack = onBack,
        bottomBar = {
            BottomActionBar {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrimaryButton(
                        text = if (summary.kept > 0) {
                            pluralStringResource(R.plurals.results_add_slots, summary.kept, summary.kept)
                        } else {
                            stringResource(R.string.results_add_none)
                        },
                        onClick = onNext,
                        enabled = summary.kept > 0,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(if (state.needsPerson) R.string.results_people_choose_first else R.string.results_add_hint),
                        style = CalEventTheme.type.secondary.copy(fontSize = 13.sp),
                        color = CalEventTheme.colors.ink2,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        },
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = CalEventTheme.type.secondary, color = CalEventTheme.colors.ink2)
            }
            state.warnings.forEach { WarningBanner(it, onRotate) }
            if (state.unknownCodes.isNotEmpty()) {
                val codes = state.unknownCodes
                val shown = codes.take(3).joinToString(", ") + if (codes.size > 3) "…" else ""
                Banner(
                    kind = BannerKind.Review,
                    text = pluralStringResource(R.plurals.codes_unknown_banner, codes.size, codes.size, shown),
                    actions = {
                        SecondaryButton(stringResource(R.string.codes_unknown_define), onClick = { onDefineCode(codes.first()) }, compact = true)
                    },
                )
            }
            if (state.people.isNotEmpty()) {
                PersonRow(state.selectedPerson, onClick = { pickingPerson = true })
            }
            SegmentedControl(
                options = listOf(
                    SegmentOption(stringResource(R.string.results_tab_agenda), CalEventIcons.Calendar),
                    SegmentOption(stringResource(R.string.results_tab_photo), CalEventIcons.Photo),
                ),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
            StatusCounters(
                kept = summary.kept,
                toReview = summary.toReview,
                ignored = summary.ignored,
                showIgnored = state.showIgnored,
                onShowIgnored = onShowIgnored,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
            val firstReview = state.visibleSlots.firstOrNull { it.status == SlotStatus.TO_REVIEW }
            if (state.needsPerson) {
                PersonPrompt(state.people.size, onChoose = { pickingPerson = true })
            } else if (tab == 0) {
                AgendaView(
                    slots = state.visibleSlots,
                    reviewCount = summary.toReview,
                    onReviewClick = { firstReview?.let { openId = it.id } },
                    onToggle = onToggle,
                    onOpen = { openId = it },
                    onKeep = { swipe(it, UserDecision.KEEP) },
                    onIgnore = { swipe(it, UserDecision.IGNORE) },
                )
            } else {
                PhotoView(slots = state.visibleSlots, onOpen = { openId = it }, photo = photo)
            }
            SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp)) { data ->
                Snackbar(
                    data,
                    shape = RoundedCornerShape(14.dp),
                    containerColor = CalEventTheme.colors.brand,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    actionColor = CalEventTheme.colors.accent,
                )
            }
        }
    }

    val openSlot = openId?.let { id -> slots.firstOrNull { it.id == id } }
    if (openSlot != null) {
        SlotSheet(
            slot = openSlot,
            onDismiss = { openId = null },
            onUpdate = onUpdate,
            onDefineCode = onDefineCode,
            photo = photo,
        )
    }
    if (pickingPerson) {
        PersonSheet(
            people = state.people,
            counts = state.peopleCounts,
            selected = state.selectedPerson,
            me = state.me,
            onSetMe = onSetMe,
            onSelect = { onSelectPerson(it); pickingPerson = false },
            onDismiss = { pickingPerson = false },
        )
    }
}

@Composable
private fun WarningBanner(warning: ParseWarning, onRotate: (clockwise: Boolean, halfTurn: Boolean) -> Unit) {
    when (warning) {
        ParseWarning.ROTATED_QUARTER_TURN -> RotationBanner(onRotate)
        ParseWarning.UPSIDE_DOWN -> {
            val cd = stringResource(R.string.results_turn_half_cd)
            Banner(
                kind = BannerKind.Review,
                text = stringResource(R.string.results_upside_down_warning),
                actions = {
                    SecondaryButton(
                        stringResource(R.string.results_turn_half),
                        onClick = { onRotate(true, true) },
                        compact = true,
                        modifier = Modifier.semantics { contentDescription = cd },
                    )
                },
            )
        }
        ParseWarning.NO_SLOTS -> Banner(BannerKind.Error, warning.message)
        ParseWarning.TILTED, ParseWarning.LOW_TEXT -> Banner(BannerKind.Review, warning.message)
    }
}

@Composable
private fun RotationBanner(onRotate: (clockwise: Boolean, halfTurn: Boolean) -> Unit) {
    val left = stringResource(R.string.results_rotate_left_cd)
    val right = stringResource(R.string.results_rotate_right_cd)
    Banner(
        kind = BannerKind.Review,
        text = stringResource(R.string.results_rotation_warning),
        actions = {
            SecondaryButton(
                stringResource(R.string.results_rotate_left),
                onClick = { onRotate(false, false) },
                compact = true,
                modifier = Modifier.semantics { contentDescription = left },
            )
            SecondaryButton(
                stringResource(R.string.results_rotate_right),
                onClick = { onRotate(true, false) },
                compact = true,
                modifier = Modifier.semantics { contentDescription = right },
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusCounters(
    kept: Int,
    toReview: Int,
    ignored: Int,
    showIgnored: Boolean,
    onShowIgnored: (Boolean) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        StatusChip(SlotStatus.KEPT, text = pluralStringResource(R.plurals.results_count_kept, kept, kept))
        if (toReview > 0) {
            StatusChip(SlotStatus.TO_REVIEW, text = pluralStringResource(R.plurals.results_count_review, toReview, toReview))
        }
        if (ignored > 0) {
            // The chip itself is 28 dp; the toggle area around it is 44 dp high.
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .toggleable(value = showIgnored, role = Role.Switch, onValueChange = onShowIgnored),
                contentAlignment = Alignment.Center,
            ) {
                val res = if (showIgnored) R.plurals.results_count_ignored_shown else R.plurals.results_count_ignored_show
                StatusChip(SlotStatus.IGNORED, text = pluralStringResource(res, ignored, ignored))
            }
        }
    }
}

private val previewState = ScanUiState(slots = FakeData.weeklySlots, calendars = FakeData.calendars)
private val rotationState = previewState.copy(warnings = listOf(ParseWarning.ROTATED_QUARTER_TURN))
private val teamState = ScanUiState(slots = emptyList(), calendars = FakeData.calendars, people = listOf("Camille B.", "Dominique L.", "Sam R."))

@Composable
private fun PreviewScreen(mode: ThemeMode, tab: Int = 0, rotation: Boolean = false, team: Boolean = false) = CalEventTheme(mode) {
    ResultsContent(if (team) teamState else if (rotation) rotationState else previewState, {}, {}, {}, {}, {}, { _, _ -> }, initialTab = tab)
}

@Preview(name = "Agenda light", widthDp = 390, heightDp = 844)
@Composable
private fun AgendaLightPreview() = PreviewScreen(ThemeMode.LIGHT)

@Preview(name = "Agenda dark", widthDp = 390, heightDp = 844)
@Composable
private fun AgendaDarkPreview() = PreviewScreen(ThemeMode.DARK)

@Preview(name = "Photo light", widthDp = 390, heightDp = 844)
@Composable
private fun PhotoLightPreview() = PreviewScreen(ThemeMode.LIGHT, tab = 1)

@Preview(name = "Photo dark", widthDp = 390, heightDp = 844)
@Composable
private fun PhotoDarkPreview() = PreviewScreen(ThemeMode.DARK, tab = 1)

@Preview(name = "Rotation light", widthDp = 390, heightDp = 844)
@Composable
private fun RotationLightPreview() = PreviewScreen(ThemeMode.LIGHT, rotation = true)

@Preview(name = "Rotation dark", widthDp = 390, heightDp = 844)
@Composable
private fun RotationDarkPreview() = PreviewScreen(ThemeMode.DARK, rotation = true)

@Preview(name = "Team light", widthDp = 390, heightDp = 844)
@Composable
private fun TeamLightPreview() = PreviewScreen(ThemeMode.LIGHT, team = true)

@Preview(name = "Team dark", widthDp = 390, heightDp = 844)
@Composable
private fun TeamDarkPreview() = PreviewScreen(ThemeMode.DARK, team = true)
