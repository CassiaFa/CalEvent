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
import kotlinx.coroutines.delay

private const val STEP_COUNT = 3

// Simulated work: three steps of 1.1 s each (3.3 s total, inside the 2 to 5 s range), then a short pause.
private const val STEP_MILLIS = 1100L
private const val FINISH_PAUSE_MILLIS = 300L

// Photo backdrop: scene content, fixed in both themes.
private val PhotoBackdrop = Color(0xFF2B241F)

/**
 * Recognition in progress (simulated): Detection, Reading, Structuring, then [onNext] by itself.
 * Cancel goes back. [viewModel] is unused until the real pipeline is wired.
 */
@Composable
fun AnalysisScreen(viewModel: ScanViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    // Index of the running step; STEP_COUNT means everything is done.
    var step by remember { mutableIntStateOf(0) }
    val currentOnNext by rememberUpdatedState(onNext)
    LaunchedEffect(Unit) {
        for (i in 0 until STEP_COUNT) {
            step = i
            delay(STEP_MILLIS)
        }
        step = STEP_COUNT
        delay(FINISH_PAUSE_MILLIS)
        currentOnNext()
    }
    AnalysisContent(step = step, onCancel = onBack)
}

@Composable
private fun AnalysisContent(step: Int, onCancel: () -> Unit) {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    val progress by animateFloatAsState(step / STEP_COUNT.toFloat() + if (step < STEP_COUNT) 0.5f / STEP_COUNT else 0f, tween(STEP_MILLIS.toInt()), label = "progress")
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

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AnalysisScreenLightPreview() = CalEventTheme(ThemeMode.LIGHT) { AnalysisContent(step = 1, onCancel = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AnalysisScreenDarkPreview() = CalEventTheme(ThemeMode.DARK) { AnalysisContent(step = 1, onCancel = {}) }
