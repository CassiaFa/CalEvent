package fr.calevent.app.ui.screens.success

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import fr.calevent.app.ui.components.TertiaryButton
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.screens.send.periodLabel
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode
import fr.calevent.core.toCalendarEvent

/** Confirmation after sending; [onDone] returns to home. */
@Composable
fun SuccessScreen(viewModel: ScanViewModel, onDone: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var noCalendarApp by remember { mutableStateOf(false) }
    SuccessContent(
        state,
        onOpenCalendar = {
            val millis = state.sendResult?.firstStartMillis ?: System.currentTimeMillis()
            val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also { ContentUris.appendId(it, millis) }.build()
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                noCalendarApp = true
            }
        },
        onUndo = viewModel::undoSend,
        onDone = onDone,
        noCalendarApp = noCalendarApp,
    )
}

@Composable
private fun SuccessContent(
    state: ScanUiState,
    onOpenCalendar: () -> Unit,
    onDone: () -> Unit,
    onUndo: () -> Unit = {},
    noCalendarApp: Boolean = false,
) {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    val plan = state.sendPlan
    val result = state.sendResult
    val undone = result?.undone == true
    val count = result?.createdIds?.size ?: plan.events.size
    val duplicatesLabel = if (result != null) {
        if (result.duplicatesIgnored == 0) null else plan.duplicates.label
    } else plan.duplicates.label
    val period = periodLabel(plan)
    val calendarName = result?.calendarName ?: state.selectedCalendar?.name
    ScreenScaffold(
        title = stringResource(R.string.success_title),
        bottomBar = {
            BottomActionBar {
                if (!undone) {
                    PrimaryButton(stringResource(R.string.success_open_calendar), onClick = onOpenCalendar, modifier = Modifier.fillMaxWidth())
                    SecondaryButton(stringResource(R.string.success_back_home), onClick = onDone, modifier = Modifier.fillMaxWidth())
                    if (result != null) TertiaryButton(stringResource(R.string.success_undo), onClick = onUndo, modifier = Modifier.fillMaxWidth())
                } else {
                    PrimaryButton(stringResource(R.string.success_back_home), onClick = onDone, modifier = Modifier.fillMaxWidth())
                }
            }
        },
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Box(
                Modifier.size(72.dp).background(c.keptBg, CircleShape).border(1.dp, c.line, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CalEventIcons.CheckBold, contentDescription = null, tint = c.kept, modifier = Modifier.size(36.dp))
            }
            Text(
                if (undone) stringResource(R.string.success_undone_headline) else pluralStringResource(R.plurals.success_headline, count, count),
                style = t.title,
                color = c.ink,
                textAlign = TextAlign.Center,
            )
            val detail = when {
                undone -> pluralStringResource(R.plurals.success_undone_detail, count, count, calendarName.orEmpty())
                calendarName != null && period != null -> stringResource(R.string.success_calendar_period_line, calendarName, period)
                calendarName != null -> stringResource(R.string.success_calendar_line, calendarName)
                else -> null
            }
            if (detail != null) {
                Text(detail, style = t.body, color = c.ink2, textAlign = TextAlign.Center)
            }
            if (!undone) duplicatesLabel?.let {
                Banner(BannerKind.Info, it, modifier = Modifier.padding(top = 12.dp))
            }
            if (result != null && result.failedCount > 0 && !undone) {
                Banner(BannerKind.Error, pluralStringResource(R.plurals.success_failed, result.failedCount, result.failedCount))
            }
            if (state.error != null) Banner(BannerKind.Error, stringResource(R.string.send_error_undo))
            if (noCalendarApp) Banner(BannerKind.Error, stringResource(R.string.success_no_calendar_app))
        }
    }
}

private fun previewState(existing: Int) = ScanUiState(
    slots = FakeData.weeklySlots,
    calendars = FakeData.calendars,
    selectedCalendarId = FakeData.calendars[1].id,
    existingEvents = FakeData.weeklySlots.filter { it.isSelected }.take(existing).map { it.toCalendarEvent() },
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SuccessLightPreview() = CalEventTheme(ThemeMode.LIGHT) { SuccessContent(previewState(0), {}, {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SuccessDarkPreview() = CalEventTheme(ThemeMode.DARK) { SuccessContent(previewState(0), {}, {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SuccessDuplicatesPreview() = CalEventTheme(ThemeMode.LIGHT) { SuccessContent(previewState(2), {}, {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SuccessDuplicatesDarkPreview() = CalEventTheme(ThemeMode.DARK) { SuccessContent(previewState(2), {}, {}) }
