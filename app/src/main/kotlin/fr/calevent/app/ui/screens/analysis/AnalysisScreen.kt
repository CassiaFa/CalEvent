package fr.calevent.app.ui.screens.analysis

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.calevent.app.R
import fr.calevent.app.ui.ScanViewModel
import fr.calevent.app.ui.components.BottomActionBar
import fr.calevent.app.ui.components.OfflineBadge
import fr.calevent.app.ui.components.PlanningPhotoPlaceholder
import fr.calevent.app.ui.components.PlanningPhotoVariant
import fr.calevent.app.ui.components.ScreenScaffold
import fr.calevent.app.ui.components.SecondaryButton
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import fr.calevent.app.capture.rememberGalleryImporter
import fr.calevent.app.ui.AnalysisFailure
import fr.calevent.app.ui.AnalysisState
import fr.calevent.app.ui.components.Banner
import fr.calevent.app.ui.components.BannerKind
import fr.calevent.app.ui.components.PrimaryButton
import fr.calevent.app.ui.components.TertiaryButton

private const val STEP_COUNT = 3

// Duration of the progress bar animation between two steps.
private const val STEP_MILLIS = 600

// Photo backdrop: scene content, fixed in both themes.
private val PhotoBackdrop = Color(0xFF2B241F)

/**
 * Recognition in progress: Detection, Reading, Structuring, driven by the real pipeline
 * ([ScanViewModel.startAnalysis]); [onNext] when done. Cancel stops the analysis and goes back.
 * When nothing is found (or reading fails) the "no slot found" state replaces the progress.
 *
 * @param onRetake back to the camera.
 */
@Composable
fun AnalysisScreen(viewModel: ScanViewModel, onBack: () -> Unit, onNext: () -> Unit, onRetake: () -> Unit = {}) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    // Survives rotation of the phone so that the analysis is not restarted.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            viewModel.startAnalysis(context)
        }
    }
    val currentOnNext by rememberUpdatedState(onNext)
    val analysis = state.analysis
    LaunchedEffect(analysis) { if (analysis is AnalysisState.Done) currentOnNext() }

    BackHandler(enabled = analysis is AnalysisState.Running) {
        viewModel.cancelAnalysis()
        onBack()
    }

    var importError by remember { mutableStateOf(false) }
    val importer = rememberGalleryImporter(
        onImported = { started = true; viewModel.startAnalysis(context) },
        onError = { importError = true },
    )
    when (analysis) {
        is AnalysisState.Failed -> NoSlotsContent(
            failure = analysis.reason,
            canViewAnyway = analysis.textRead,
            importError = importError,
            onRetake = onRetake,
            onPickOther = importer::launch,
            onViewAnyway = onNext,
            onBack = onBack,
        )
        else -> AnalysisContent(
            step = when (analysis) {
                is AnalysisState.Running -> analysis.step
                is AnalysisState.Done -> STEP_COUNT
                else -> 0
            },
            onCancel = {
                viewModel.cancelAnalysis()
                onBack()
            },
        )
    }
}

@Composable
private fun AnalysisContent(step: Int, onCancel: () -> Unit) {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    val progress by animateFloatAsState(step / STEP_COUNT.toFloat() + if (step < STEP_COUNT) 0.5f / STEP_COUNT else 0f, tween(STEP_MILLIS), label = "progress")
    val headline = stringResource(
        when (step) {
            0 -> R.string.analysis_headline_detect
            1 -> R.string.analysis_headline_read
            2 -> R.string.analysis_headline_structure
            else -> R.string.analysis_headline_done
        },
    )
    val progressCd = stringResource(R.string.analysis_progress_cd)
    ScreenScaffold(
        bottomBar = {
            BottomActionBar {
                SecondaryButton(stringResource(R.string.analysis_cancel), onClick = onCancel, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Icon(CalEventIcons.ShieldOffline, contentDescription = null, tint = c.ink2, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.analysis_offline_reminder), style = t.secondary, color = c.ink2)
                }
            }
        },
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ScanningPhoto(running = step < STEP_COUNT)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Live region: the new step is announced by screen readers each time the headline changes.
                Text(
                    headline,
                    style = t.section.copy(fontSize = 22.sp, lineHeight = 27.sp),
                    color = c.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
                )
                Text(stringResource(R.string.analysis_subtitle), style = t.secondary, color = c.ink2, textAlign = TextAlign.Center)
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(c.surface2)
                        .semantics {
                            contentDescription = progressCd
                            progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                        },
                ) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(c.accent, CircleShape))
                }
                Column(
                    Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(16.dp)).border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(vertical = 4.dp),
                ) {
                    StepRow(1, R.string.analysis_step_detect, R.string.analysis_detail_detect, step, first = true)
                    StepRow(2, R.string.analysis_step_read, R.string.analysis_detail_read, step)
                    StepRow(3, R.string.analysis_step_structure, R.string.analysis_detail_structure, step)
                }
            }
        }
    }
}

private enum class StepState { Done, Running, Pending }

@Composable
private fun StepRow(number: Int, label: Int, detail: Int, currentStep: Int, first: Boolean = false) {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    val state = when {
        currentStep >= number -> StepState.Done
        currentStep == number - 1 -> StepState.Running
        else -> StepState.Pending
    }
    val labelText = stringResource(label)
    val detailText = stringResource(detail)
    val stateText = stringResource(
        when (state) {
            StepState.Done -> R.string.analysis_state_done
            StepState.Running -> R.string.analysis_state_running
            StepState.Pending -> R.string.analysis_state_pending
        },
    )
    val rowCd = stringResource(R.string.analysis_step_cd, number, labelText, stateText)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .drawBehind { if (!first) drawRect(c.line, Offset.Zero, Size(size.width, 1.dp.toPx())) }
            .padding(horizontal = 16.dp)
            .semantics(mergeDescendants = true) { contentDescription = rowCd },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when (state) {
                StepState.Done -> Box(Modifier.size(22.dp).background(c.kept, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(CalEventIcons.CheckBold, contentDescription = null, tint = c.surface, modifier = Modifier.size(16.dp))
                }
                StepState.Running -> CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, trackColor = c.surface2, strokeWidth = 3.dp)
                StepState.Pending -> Box(Modifier.size(22.dp).border(2.dp, c.line, CircleShape))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(labelText, style = t.bodyStrong, color = if (state == StepState.Pending) c.ink2 else c.ink)
            Text(detailText, style = t.secondary, color = c.ink2)
        }
        Text(
            stateText,
            style = t.secondary.copy(fontWeight = if (state == StepState.Running) FontWeight.Bold else FontWeight.SemiBold),
            color = if (state == StepState.Running) c.ink else c.ink2,
        )
    }
}

/** Small photo with a scan line sweeping over it while [running]. */
@Composable
private fun ScanningPhoto(running: Boolean) {
    val c = CalEventTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val sweep by rememberInfiniteTransition(label = "scan").animateFloat(
        0.08f, 0.88f,
        infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "sweep",
    )
    val cd = stringResource(R.string.analysis_photo_cd)
    Box(
        Modifier
            .size(width = 200.dp, height = 250.dp)
            .clip(shape)
            .background(PhotoBackdrop)
            .semantics { contentDescription = cd },
    ) {
        PlanningPhotoPlaceholder(PlanningPhotoVariant.Weekly, Modifier.padding(horizontal = 22.dp, vertical = 20.dp).fillMaxWidth().fillMaxHeight().rotate(-2f))
        if (running) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .drawBehind {
                        val y = size.height * sweep
                        drawRect(c.accent.copy(alpha = 0.35f), Offset(0f, y - 7.dp.toPx()), Size(size.width, 14.dp.toPx()))
                        drawRect(c.accent, Offset(0f, y - 1.5.dp.toPx()), Size(size.width, 3.dp.toPx()))
                    },
            )
        }
    }
}

/** "No slot found" state (also used when reading failed): tips to retake the photo, and the way out. */
@Composable
private fun NoSlotsContent(
    failure: AnalysisFailure,
    canViewAnyway: Boolean,
    importError: Boolean,
    onRetake: () -> Unit,
    onPickOther: () -> Unit,
    onViewAnyway: () -> Unit,
    onBack: () -> Unit,
) {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    ScreenScaffold(
        onBack = onBack,
        bottomBar = {
            BottomActionBar {
                PrimaryButton(stringResource(R.string.analysis_retake), onClick = onRetake, icon = CalEventIcons.Camera, modifier = Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.analysis_pick_other), onClick = onPickOther, icon = CalEventIcons.Gallery, modifier = Modifier.fillMaxWidth())
                if (canViewAnyway) TertiaryButton(stringResource(R.string.analysis_view_anyway), onClick = onViewAnyway, modifier = Modifier.fillMaxWidth())
            }
        },
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.size(72.dp).background(c.surface2, CircleShape), contentAlignment = Alignment.Center) {
                Icon(CalEventIcons.Search, contentDescription = null, tint = c.ink2, modifier = Modifier.size(32.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(if (failure == AnalysisFailure.NO_SLOTS) R.string.analysis_none_title else R.string.analysis_error_title),
                    style = t.section.copy(fontSize = 22.sp, lineHeight = 27.sp),
                    color = c.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(if (failure == AnalysisFailure.NO_SLOTS) R.string.analysis_none_body else R.string.analysis_error_body),
                    style = t.secondary,
                    color = c.ink2,
                    textAlign = TextAlign.Center,
                )
            }
            if (importError) Banner(BannerKind.Error, stringResource(R.string.home_import_error))
            Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(16.dp)).border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(vertical = 4.dp)) {
                Text(
                    stringResource(R.string.analysis_tips_title).uppercase(),
                    style = t.label,
                    color = c.ink2,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                listOf(R.string.analysis_tip_flat, R.string.analysis_tip_light, R.string.analysis_tip_whole, R.string.analysis_tip_close).forEach { tip ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(CalEventIcons.Check, contentDescription = null, tint = c.kept, modifier = Modifier.size(20.dp))
                        Text(stringResource(tip), style = t.body, color = c.ink)
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AnalysisScreenLightPreview() = CalEventTheme(ThemeMode.LIGHT) { AnalysisContent(step = 1, onCancel = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AnalysisScreenDarkPreview() = CalEventTheme(ThemeMode.DARK) { AnalysisContent(step = 1, onCancel = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NoSlotsPreview() = CalEventTheme(ThemeMode.LIGHT) { NoSlotsContent(AnalysisFailure.NO_SLOTS, true, false, {}, {}, {}, {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NoSlotsDarkPreview() = CalEventTheme(ThemeMode.DARK) { NoSlotsContent(AnalysisFailure.NO_SLOTS, false, false, {}, {}, {}, {}) }
