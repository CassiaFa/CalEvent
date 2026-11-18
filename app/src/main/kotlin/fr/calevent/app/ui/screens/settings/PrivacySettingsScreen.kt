package fr.calevent.app.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.calevent.app.R
import fr.calevent.app.capture.PhotoStorage
import fr.calevent.app.settings.Settings
import fr.calevent.app.settings.SettingsRepository
import fr.calevent.app.ui.components.ScreenScaffold
import fr.calevent.app.ui.components.SegmentOption
import fr.calevent.app.ui.components.SegmentedControl
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode
import fr.calevent.core.PhotoRetention
import kotlinx.coroutines.launch

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Privacy (9d): what happens to a photo, retention, stored photos, permissions. */
@Composable
fun PrivacySettingsScreen(settings: Settings, repository: SettingsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Permissions can change in the system settings: re-read on resume.
    var cameraOk by remember { mutableStateOf(context.granted(Manifest.permission.CAMERA)) }
    var calendarOk by remember { mutableStateOf(context.granted(Manifest.permission.WRITE_CALENDAR) && context.granted(Manifest.permission.READ_CALENDAR)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                cameraOk = context.granted(Manifest.permission.CAMERA)
                calendarOk = context.granted(Manifest.permission.WRITE_CALENDAR) && context.granted(Manifest.permission.READ_CALENDAR)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val stats = rememberPhotoStats(context, refresh)
    if (confirmDelete) {
        DeletePhotosDialog(
            onConfirm = {
                confirmDelete = false
                scope.launch { PhotoStorage.deleteAll(context); refresh++ }
            },
            onDismiss = { confirmDelete = false },
        )
    }
    PrivacyContent(
        retention = settings.photoRetention,
        photoCount = stats?.count,
        photoSize = stats?.let { formatSize(it.bytes) },
        cameraOk = cameraOk,
        calendarOk = calendarOk,
        onBack = onBack,
        onRetention = { scope.launch { repository.setPhotoRetention(it) } },
        onDelete = { confirmDelete = true },
        onManage = { openAppSystemSettings(context) },
    )
}

@Composable
internal fun PrivacyContent(
    retention: PhotoRetention,
    photoCount: Int?,
    photoSize: String?,
    cameraOk: Boolean,
    calendarOk: Boolean,
    onBack: () -> Unit,
    onRetention: (PhotoRetention) -> Unit,
    onDelete: () -> Unit,
    onManage: () -> Unit,
) {
    val c = CalEventTheme.colors
    ScreenScaffold(title = stringResource(R.string.settings_privacy_screen_title), onBack = onBack) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column {
                SectionLabel(stringResource(R.string.settings_steps_section))
                SettingsGroup {
                    listOf(R.string.settings_step_1, R.string.settings_step_2, R.string.settings_step_3).forEachIndexed { i, res ->
                        if (i > 0) RowDivider()
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(24.dp).background(c.keptBg, CircleShape), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", style = CalEventTheme.type.chip, color = c.kept)
                            }
                            Text(stringResource(res), style = CalEventTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Column {
                SectionLabel(stringResource(R.string.settings_photos_kept))
                SettingsGroup {
                    val options = listOf(PhotoRetention.DAYS_7, PhotoRetention.DAYS_30, PhotoRetention.ALWAYS)
                    SegmentedControl(
                        options = listOf(
                            SegmentOption(stringResource(R.string.settings_retention_7)),
                            SegmentOption(stringResource(R.string.settings_retention_30)),
                            SegmentOption(stringResource(R.string.settings_retention_always)),
                        ),
                        selectedIndex = options.indexOf(retention),
                        onSelect = { onRetention(options[it]) },
                        modifier = Modifier.padding(8.dp),
                    )
                    RowDivider()
                    SettingsRow(
                        label = if (photoCount != null) pluralStringResource(R.plurals.settings_photo_count, photoCount, photoCount) else "",
                        value = photoSize,
                    )
                    RowDivider()
                    SettingsRow(stringResource(R.string.settings_delete_all), icon = CalEventIcons.Trash, danger = true, chevron = false, onClick = onDelete)
                }
            }
            Column {
                SectionLabel(stringResource(R.string.settings_permissions_section))
                SettingsGroup {
                    PermissionRow(stringResource(R.string.settings_permission_camera), cameraOk, R.string.settings_permission_camera_ok, R.string.settings_permission_camera_no)
                    RowDivider()
                    PermissionRow(stringResource(R.string.settings_permission_calendar), calendarOk, R.string.settings_permission_calendar_ok, R.string.settings_permission_calendar_no)
                    RowDivider()
                    SettingsRow(stringResource(R.string.settings_permission_manage), chevron = false, onClick = onManage)
                }
            }
        }
    }
}

/** Status is colour + icon + text, never colour alone. */
@Composable
private fun PermissionRow(label: String, ok: Boolean, okRes: Int, noRes: Int) {
    val c = CalEventTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = CalEventTheme.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
        val tint = if (ok) c.kept else c.danger
        Icon(if (ok) CalEventIcons.Check else CalEventIcons.Error, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(stringResource(if (ok) okRes else noRes), style = CalEventTheme.type.secondary.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = tint)
    }
}

@Composable
private fun PreviewContent(mode: ThemeMode) = CalEventTheme(mode) {
    PrivacyContent(PhotoRetention.DAYS_30, 4, "6,2 Mo", cameraOk = true, calendarOk = false, onBack = {}, onRetention = {}, onDelete = {}, onManage = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PrivacyLightPreview() = PreviewContent(ThemeMode.LIGHT)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PrivacyDarkPreview() = PreviewContent(ThemeMode.DARK)
