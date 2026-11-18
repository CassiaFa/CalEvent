package fr.calevent.app.ui.screens.send

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.calevent.app.ui.CalendarAccess
import fr.calevent.app.ui.SendError
import fr.calevent.app.ui.components.SecondaryButton
import fr.calevent.app.ui.icons.CalEventIcons
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.calevent.app.R
import fr.calevent.app.data.DeviceCalendar
import fr.calevent.app.data.FakeData
import fr.calevent.app.ui.ScanUiState
import fr.calevent.app.ui.ScanViewModel
import fr.calevent.app.ui.components.Banner
import fr.calevent.app.ui.components.BannerKind
import fr.calevent.app.ui.components.BottomActionBar
import fr.calevent.app.ui.components.PrimaryButton
import fr.calevent.app.ui.components.ScreenScaffold
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode
import fr.calevent.core.SendPlan
import fr.calevent.core.toCalendarEvent
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)

/** "16 – 29 nov." style period covered by the events to add, or null when there are none. */
@Composable
internal fun periodLabel(plan: SendPlan): String? {
    val first = plan.events.minOfOrNull { it.start.toLocalDate() } ?: return null
    val last = plan.events.maxOf { it.start.toLocalDate() }
    return if (first == last) shortDate.format(first)
    else stringResource(R.string.send_period_range, shortDate.format(first), shortDate.format(last))
}

/** Calendar choice, recap and confirmation. Stateless content lives in [SendContent]. */
@Composable
fun SendScreen(viewModel: ScanViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val granted = grants.isNotEmpty() && grants.values.all { it }
        val activity = context.findActivity()
        val blocked = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.WRITE_CALENDAR)
        viewModel.onPermissionResult(granted, blocked)
    }
    val requestAccess = { launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) }

    // Real calendar provider; the permission is asked here, when it becomes useful, never at launch.
    LaunchedEffect(Unit) {
        viewModel.attach(context)
        viewModel.refreshAccess()
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!granted && !asked) {
            asked = true
            requestAccess()
        }
    }
    // Coming back from the system settings: re-check.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshAccess() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SendContent(
        state,
        onSelectCalendar = viewModel::selectCalendar,
        onBack = onBack,
        onConfirm = { viewModel.send(onNext) },
        onAllowAccess = requestAccess,
        onOpenSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        onRetry = { viewModel.clearError(); viewModel.refreshAccess() },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun SendContent(
    state: ScanUiState,
    onSelectCalendar: (Long) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onAllowAccess: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    val plan = state.sendPlan
    val calendar = state.selectedCalendar
    val count = plan.events.size
    val hasAccess = state.calendarAccess == CalendarAccess.GRANTED
    val canConfirm = hasAccess && calendar != null && plan.canSend && !state.loading && !state.sending
    ScreenScaffold(
        title = stringResource(R.string.send_title),
        onBack = onBack,
        bottomBar = {
            BottomActionBar {
                PrimaryButton(
                    text = pluralStringResource(R.plurals.send_confirm, count, count),
                    onClick = onConfirm,
                    enabled = canConfirm,
                    modifier = Modifier.fillMaxWidth(),
                )
                val hint = when {
                    state.sending -> stringResource(R.string.send_sending)
                    !hasAccess -> null
                    calendar == null -> stringResource(R.string.send_choose_calendar_hint)
                    !plan.canSend -> plan.duplicates.allPresentLabel ?: stringResource(R.string.send_all_present_hint)
                    else -> null
                }
                if (hint != null) {
                    Text(hint, style = t.secondary, color = c.ink2, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
                }
            }
        },
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (!hasAccess) {
                PermissionPanel(state.calendarAccess, onAllowAccess, onOpenSettings)
                return@Column
            }
            state.error?.let { err ->
                Banner(
                    BannerKind.Error,
                    stringResource(if (err == SendError.LOAD_CALENDARS) R.string.send_error_load else R.string.send_error_insert),
                )
                if (err == SendError.LOAD_CALENDARS) SecondaryButton(stringResource(R.string.send_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth())
            }
            if (state.loading && state.calendars.isEmpty()) {
                Text(stringResource(R.string.send_loading), style = t.secondary, color = c.ink2)
            }
            Section(stringResource(R.string.send_calendar_section)) {
                val groupDescription = stringResource(R.string.send_calendar_group_description)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .card()
                        .selectableGroup()
                        .semantics { contentDescription = groupDescription },
                ) {
                    if (state.calendars.isEmpty()) {
                        Text(stringResource(R.string.send_calendar_none), style = t.body, color = c.ink2, modifier = Modifier.padding(16.dp))
                    }
                    state.calendars.forEachIndexed { index, cal ->
                        if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                        CalendarRow(cal, selected = cal.id == state.selectedCalendarId, onClick = { onSelectCalendar(cal.id) })
                    }
                }
            }
            Section(stringResource(R.string.send_recap_section)) {
                Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        pluralStringResource(R.plurals.send_recap_count, count, count),
                        style = t.bodyStrong,
                        color = c.ink,
                        modifier = Modifier.heightIn(min = 44.dp).wrapCenter(),
                    )
                    RecapLine(stringResource(R.string.send_recap_period_label), periodLabel(plan) ?: "–")
                    RecapLine(
                        stringResource(R.string.send_recap_calendar_label),
                        calendar?.name ?: stringResource(R.string.send_recap_calendar_none),
                    )
                }
            }
            plan.duplicates.label?.let { Banner(BannerKind.Info, it) }
        }
    }
}

private fun Modifier.wrapCenter(): Modifier = this.then(Modifier.fillMaxWidth()).padding(top = 10.dp)

@Composable
private fun Modifier.card(): Modifier {
    val c = CalEventTheme.colors
    val shape = RoundedCornerShape(16.dp)
    return this.clip(shape).background(c.surface, shape).border(1.dp, c.line, shape)
}

@Composable
private fun PermissionPanel(access: CalendarAccess, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    if (access == CalendarAccess.UNKNOWN) return
    val blocked = access == CalendarAccess.BLOCKED
    Column(
        Modifier.fillMaxWidth().card().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).background(c.dangerBg, CircleShape), contentAlignment = Alignment.Center) {
            Icon(CalEventIcons.Calendar, contentDescription = null, tint = c.danger, modifier = Modifier.size(28.dp))
        }
        Text(stringResource(R.string.send_permission_title), style = t.section, color = c.ink, textAlign = TextAlign.Center)
        Text(
            stringResource(if (blocked) R.string.send_permission_blocked_body else R.string.send_permission_body),
            style = t.body,
            color = c.ink2,
            textAlign = TextAlign.Center,
        )
        if (blocked) {
            PrimaryButton(stringResource(R.string.send_permission_settings), onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
        } else {
            PrimaryButton(stringResource(R.string.send_permission_allow), onClick = onAllow, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title.uppercase(Locale.FRENCH), style = CalEventTheme.type.label, color = CalEventTheme.colors.ink2)
        content()
    }
}

@Composable
private fun RecapLine(label: String, value: String) {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = t.secondary, color = c.ink2, modifier = Modifier.width(88.dp))
        Text(value, style = t.bodyStrong, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CalendarRow(calendar: DeviceCalendar, selected: Boolean, onClick: () -> Unit) {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The only colour outside the theme: the system calendar's own colour.
        Box(Modifier.size(14.dp).background(Color(calendar.colorArgb), CircleShape))
        Column(Modifier.weight(1f)) {
            Text(calendar.name, style = t.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(calendar.account, style = t.secondary, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Selection is conveyed by the radio itself; the row handles the click and semantics.
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = c.ink, unselectedColor = c.ink2),
        )
    }
}

private fun previewState(
    selected: Long? = FakeData.calendars[1].id,
    existing: Int = 2,
) = ScanUiState(
    slots = FakeData.weeklySlots,
    calendars = FakeData.calendars,
    selectedCalendarId = selected,
    existingEvents = FakeData.weeklySlots.filter { it.isSelected }.take(existing).map { it.toCalendarEvent() },
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SendDeniedPreview() = CalEventTheme(ThemeMode.LIGHT) {
    SendContent(previewState().copy(calendarAccess = CalendarAccess.BLOCKED), {}, {}, {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SendDeniedDarkPreview() = CalEventTheme(ThemeMode.DARK) {
    SendContent(previewState().copy(calendarAccess = CalendarAccess.DENIED), {}, {}, {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SendLightPreview() = CalEventTheme(ThemeMode.LIGHT) { SendContent(previewState(existing = 0), {}, {}, {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SendDarkPreview() = CalEventTheme(ThemeMode.DARK) { SendContent(previewState(existing = 0), {}, {}, {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SendDuplicatesPreview() = CalEventTheme(ThemeMode.LIGHT) { SendContent(previewState(), {}, {}, {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SendDuplicatesDarkPreview() = CalEventTheme(ThemeMode.DARK) { SendContent(previewState(), {}, {}, {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SendNoCalendarPreview() = CalEventTheme(ThemeMode.LIGHT) { SendContent(previewState(selected = null), {}, {}, {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SendNoCalendarDarkPreview() = CalEventTheme(ThemeMode.DARK) { SendContent(previewState(selected = null), {}, {}, {}) }
