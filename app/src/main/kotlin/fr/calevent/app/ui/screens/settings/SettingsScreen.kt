package fr.calevent.app.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.calevent.app.BuildConfig
import fr.calevent.app.R
import fr.calevent.app.capture.PhotoStats
import fr.calevent.app.capture.PhotoStorage
import fr.calevent.app.settings.Settings
import fr.calevent.app.settings.SettingsRepository
import fr.calevent.app.ui.ScanViewModel
import fr.calevent.app.ui.components.ScreenScaffold
import fr.calevent.app.ui.components.SegmentOption
import fr.calevent.app.ui.components.SegmentedControl
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import java.util.Locale

/** "6,2 Mo" / "340 Ko" in French format. */
@Composable
internal fun formatSize(bytes: Long): String =
    if (bytes < 1_000_000L) stringResource(R.string.settings_size_kb, ((bytes + 999) / 1000).toInt())
    else stringResource(R.string.settings_size_mb, String.format(Locale.FRANCE, "%.1f", bytes / 1_000_000.0))

/** Loads the stored-photo stats; bump [refreshKey] to reload. */
@Composable
internal fun rememberPhotoStats(context: Context, refreshKey: Int = 0): PhotoStats? {
    var stats by remember { mutableStateOf<PhotoStats?>(null) }
    LaunchedEffect(refreshKey) { stats = runCatching { PhotoStorage.stats(context) }.getOrNull() }
    return stats
}

/** Settings home (9a). */
@Composable
fun SettingsScreen(
    viewModel: ScanViewModel,
    settings: Settings,
    repository: SettingsRepository,
    onBack: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenCodes: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val codeTable by repository.codeTable.collectAsState(initial = null)
    var refresh by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Lists calendars if the permission is already granted; never asks for it here.
        viewModel.attach(context)
        viewModel.refreshAccess()
    }
    val stats = rememberPhotoStats(context, refresh)
    val calendarName = when {
        settings.alwaysAskCalendar -> stringResource(R.string.settings_ask_each_time)
        settings.defaultCalendarId == null -> stringResource(R.string.settings_none)
        else -> state.calendars.firstOrNull { it.id == settings.defaultCalendarId }?.name ?: stringResource(R.string.settings_set)
    }
    if (confirmDelete) {
        DeletePhotosDialog(
            onConfirm = {
                confirmDelete = false
                scope.launch { PhotoStorage.deleteAll(context); refresh++ }
            },
            onDismiss = { confirmDelete = false },
        )
    }
    SettingsContent(
        calendarName = calendarName,
        me = settings.me,
        codesCount = codeTable?.size,
        theme = settings.theme,
        photosSummary = stats?.let { stringResource(R.string.settings_photos_summary, it.count, formatSize(it.bytes)) },
        onBack = onBack,
        onOpenCalendar = onOpenCalendar,
        onOpenCodes = onOpenCodes,
        onTheme = { scope.launch { repository.setTheme(it) } },
        onOpenPrivacy = onOpenPrivacy,
        onDeletePhotos = { confirmDelete = true },
        onOpenAbout = onOpenAbout,
        onOpenLicenses = onOpenLicenses,
    )
}

@Composable
internal fun SettingsContent(
    calendarName: String,
    me: String?,
    codesCount: Int?,
    theme: ThemeMode,
    photosSummary: String?,
    onBack: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenCodes: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onOpenPrivacy: () -> Unit,
    onDeletePhotos: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val c = CalEventTheme.colors
    ScreenScaffold(title = stringResource(R.string.screen_settings), onBack = onBack) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column {
                SectionLabel(stringResource(R.string.settings_section_calendar))
                SettingsGroup {
                    SettingsRow(stringResource(R.string.settings_default_calendar), value = calendarName, onClick = onOpenCalendar)
                    RowDivider()
                    // Team schedules are not built yet: the row is shown but not active.
                    SettingsRow(stringResource(R.string.settings_me), value = me ?: stringResource(R.string.settings_soon), enabled = false, onClick = {})
                    RowDivider()
                    SettingsRow(
                        stringResource(R.string.settings_codes),
                        value = codesCount?.let { pluralStringResource(R.plurals.codes_count, it, it) },
                        onClick = onOpenCodes,
                    )
                }
            }
            Column {
                SectionLabel(stringResource(R.string.settings_section_theme))
                SettingsGroup {
                    val modes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
                    SegmentedControl(
                        options = listOf(
                            SegmentOption(stringResource(R.string.settings_theme_system)),
                            SegmentOption(stringResource(R.string.settings_theme_light)),
                            SegmentOption(stringResource(R.string.settings_theme_dark)),
                        ),
                        selectedIndex = modes.indexOf(theme),
                        onSelect = { onTheme(modes[it]) },
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            Column {
                SectionLabel(stringResource(R.string.settings_section_privacy))
                SettingsGroup {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(CalEventIcons.ShieldOffline, contentDescription = null, tint = c.kept, modifier = Modifier.size(22.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.settings_privacy_title), style = CalEventTheme.type.bodyStrong, color = c.ink)
                            Text(stringResource(R.string.settings_privacy_body), style = CalEventTheme.type.secondary, color = c.ink2)
                        }
                    }
                    RowDivider()
                    SettingsRow(stringResource(R.string.settings_photos_kept), value = photosSummary, onClick = onOpenPrivacy)
                    RowDivider()
                    SettingsRow(
                        stringResource(R.string.settings_delete_analyzed),
                        icon = CalEventIcons.Trash,
                        chevron = false,
                        danger = true,
                        onClick = onDeletePhotos,
                    )
                }
            }
            Column {
                SectionLabel(stringResource(R.string.settings_section_about))
                SettingsGroup {
                    SettingsRow(
                        stringResource(R.string.settings_about_app),
                        value = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        onClick = onOpenAbout,
                    )
                    RowDivider()
                    SettingsRow(stringResource(R.string.settings_licenses), onClick = onOpenLicenses)
                }
            }
        }
    }
}

/** Confirmation before deleting every stored photo. */
@Composable
internal fun DeletePhotosDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = CalEventTheme.colors
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(stringResource(R.string.settings_delete_title), style = CalEventTheme.type.section, color = c.ink) },
        text = { Text(stringResource(R.string.settings_delete_message), style = CalEventTheme.type.body, color = c.ink2) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_delete_confirm), color = c.danger) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel), color = c.ink) }
        },
    )
}

@Composable
private fun PreviewContent(mode: ThemeMode) = CalEventTheme(mode) {
    SettingsContent("Travail", null, 9, mode, "4 · 6,2 Mo", {}, {}, {}, {}, {}, {}, {}, {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SettingsScreenLightPreview() = PreviewContent(ThemeMode.LIGHT)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SettingsScreenDarkPreview() = PreviewContent(ThemeMode.DARK)
