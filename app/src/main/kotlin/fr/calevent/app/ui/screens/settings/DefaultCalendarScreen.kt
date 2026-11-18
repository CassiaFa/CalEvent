package fr.calevent.app.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.remember
import fr.calevent.app.R
import fr.calevent.app.data.DeviceCalendar
import fr.calevent.app.data.FakeData
import fr.calevent.app.settings.Settings
import fr.calevent.app.settings.SettingsRepository
import fr.calevent.app.ui.CalendarAccess
import fr.calevent.app.ui.ScanViewModel
import fr.calevent.app.ui.components.Banner
import fr.calevent.app.ui.components.BannerKind
import fr.calevent.app.ui.components.ScreenScaffold
import fr.calevent.app.ui.components.SecondaryButton
import fr.calevent.app.ui.components.ToggleRow
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Opens the app's page in the system settings. */
internal fun openAppSystemSettings(context: Context) {
    context.startActivity(
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** Default calendar (9c). The calendar permission is requested here, on the user's tap. */
@Composable
fun DefaultCalendarScreen(viewModel: ScanViewModel, settings: Settings, repository: SettingsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val granted = grants.isNotEmpty() && grants.values.all { it }
        val activity = context.findActivity()
        val blocked = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.WRITE_CALENDAR)
        viewModel.onPermissionResult(granted, blocked)
    }
    LaunchedEffect(Unit) {
        viewModel.attach(context)
        viewModel.refreshAccess()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshAccess() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DefaultCalendarContent(
        calendars = state.calendars,
        access = state.calendarAccess,
        settings = settings,
        onBack = onBack,
        onSelect = { scope.launch { repository.setDefaultCalendar(it) } },
        onAlwaysAsk = { scope.launch { repository.setAlwaysAskCalendar(it) } },
        onSkipDuplicates = { scope.launch { repository.setSkipDuplicates(it) } },
        onAllowAccess = { launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) },
        onOpenSystemSettings = { openAppSystemSettings(context) },
    )
}

@Composable
internal fun DefaultCalendarContent(
    calendars: List<DeviceCalendar>,
    access: CalendarAccess,
    settings: Settings,
    onBack: () -> Unit,
    onSelect: (Long) -> Unit,
    onAlwaysAsk: (Boolean) -> Unit,
    onSkipDuplicates: (Boolean) -> Unit,
    onAllowAccess: () -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    val c = CalEventTheme.colors
    val granted = access == CalendarAccess.GRANTED
    ScreenScaffold(title = stringResource(R.string.settings_calendar_title), onBack = onBack) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (!granted) {
                Banner(
                    kind = BannerKind.Info,
                    text = stringResource(R.string.settings_calendar_permission),
                    actions = {
                        if (access == CalendarAccess.BLOCKED) {
                            SecondaryButton(stringResource(R.string.settings_calendar_permission_settings), onClick = onOpenSystemSettings, compact = true)
                        } else {
                            SecondaryButton(stringResource(R.string.settings_calendar_permission_allow), onClick = onAllowAccess, compact = true)
                        }
                    },
                )
            }
            Column {
                SectionLabel(stringResource(R.string.settings_calendar_section))
                SettingsGroup {
                    if (calendars.isEmpty()) {
                        if (granted) {
                            Text(
                                stringResource(R.string.settings_calendar_empty),
                                style = CalEventTheme.type.body,
                                color = c.ink2,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    calendars.forEachIndexed { i, cal ->
                        if (i > 0) RowDivider()
                        val selected = cal.id == settings.defaultCalendarId
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 58.dp)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(cal.id) })
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ColorDot(cal.colorArgb)
                            Column(Modifier.weight(1f)) {
                                Text(cal.name, style = CalEventTheme.type.bodyStrong, color = c.ink)
                                Text(cal.account, style = CalEventTheme.type.secondary, color = c.ink2)
                            }
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = c.ink, unselectedColor = c.ink2),
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.settings_calendar_note),
                    style = CalEventTheme.type.secondary,
                    color = c.ink2,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }
            Column {
                SectionLabel(stringResource(R.string.settings_send_section))
                SettingsGroup {
                    val defaultName = calendars.firstOrNull { it.id == settings.defaultCalendarId }?.name
                    ToggleRow(
                        label = stringResource(R.string.settings_always_ask),
                        supporting = if (defaultName != null) stringResource(R.string.settings_always_ask_off, defaultName)
                        else stringResource(R.string.settings_always_ask_off_none),
                        checked = settings.alwaysAskCalendar,
                        onCheckedChange = onAlwaysAsk,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    RowDivider()
                    ToggleRow(
                        label = stringResource(R.string.settings_skip_duplicates),
                        supporting = stringResource(R.string.settings_skip_duplicates_hint),
                        checked = settings.skipDuplicates,
                        onCheckedChange = onSkipDuplicates,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewContent(mode: ThemeMode) = CalEventTheme(mode) {
    DefaultCalendarContent(FakeData.calendars, CalendarAccess.GRANTED, Settings(defaultCalendarId = FakeData.calendars.first().id), {}, {}, {}, {}, {}, {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DefaultCalendarLightPreview() = PreviewContent(ThemeMode.LIGHT)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DefaultCalendarDarkPreview() = PreviewContent(ThemeMode.DARK)
