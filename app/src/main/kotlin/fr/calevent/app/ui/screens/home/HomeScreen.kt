package fr.calevent.app.ui.screens.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.calevent.app.capture.rememberGalleryImporter
import fr.calevent.app.ui.components.Banner
import fr.calevent.app.ui.components.BannerKind
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.calevent.app.R
import fr.calevent.app.ui.screens.history.HistoryCard
import fr.calevent.app.ui.screens.history.HistoryDetailSheet
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import fr.calevent.app.ui.ScanViewModel
import fr.calevent.app.ui.components.BottomActionBar
import fr.calevent.app.ui.components.IconActionButton
import fr.calevent.app.ui.components.OfflineBadge
import fr.calevent.app.ui.components.PlanningPhotoPlaceholder
import fr.calevent.app.ui.components.PlanningPhotoVariant
import fr.calevent.app.ui.components.PrimaryButton
import fr.calevent.app.ui.components.ScreenScaffold
import fr.calevent.app.ui.components.SecondaryButton
import fr.calevent.app.ui.components.StatusChip
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode
import fr.calevent.core.SlotStatus
import java.util.Locale

/**
 * Home: brand row, offline badge, pitch, recent plannings, and the two capture actions at the bottom.
 * This is the reference screen for the others: ScreenScaffold + BottomActionBar + theme tokens only.
 *
 * @param onTakePhoto start the camera. @param onImport the gallery photo was picked and imported: go on to the preview.
 * @param onOpenSettings open settings. @param onSeeAll "Tout voir" link of the recent list (full history).
 */
@Composable
fun HomeScreen(
    viewModel: ScanViewModel,
    onTakePhoto: () -> Unit,
    onImport: () -> Unit,
    onOpenSettings: () -> Unit,
    onSeeAll: () -> Unit,
) {
    val history by viewModel.historyEntries.collectAsState()
    var openId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    // Binds the history store (no permission involved); the calendar permission is never asked here.
    LaunchedEffect(Unit) { viewModel.attach(context) }
    var importError by remember { mutableStateOf(false) }
    // "Importer" opens the system photo picker; onImport runs once the photo is the current one.
    val importer = rememberGalleryImporter(onImported = { importError = false; onImport() }, onError = { importError = true })
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    ScreenScaffold(
        bottomBar = {
            BottomActionBar {
                PrimaryButton(
                    text = stringResource(R.string.home_take_photo),
                    onClick = onTakePhoto,
                    icon = CalEventIcons.Camera,
                    tall = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(
                    text = stringResource(R.string.home_import),
                    onClick = importer::launch,
                    enabled = !importer.busy,
                    icon = CalEventIcons.Gallery,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(36.dp))
                    Text(stringResource(R.string.app_name), style = t.section, color = c.ink)
                }
                IconActionButton(CalEventIcons.Settings, stringResource(R.string.action_settings), onOpenSettings)
            }
            if (importError) Banner(BannerKind.Error, stringResource(R.string.home_import_error))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OfflineBadge()
                Text(stringResource(R.string.home_title), style = t.title, color = c.ink)
                Text(stringResource(R.string.home_subtitle), style = t.secondary.copy(fontSize = 15.sp), color = c.ink2)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.home_recent_title).uppercase(Locale.FRENCH), style = t.label, color = c.ink2)
                Box(
                    modifier = Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onSeeAll).padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(stringResource(R.string.home_see_all), style = t.secondary.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (history.isEmpty()) {
                    Text(stringResource(R.string.home_recent_empty), style = t.secondary, color = c.ink2)
                }
                history.take(3).forEach { e -> HistoryCard(e, onClick = { openId = e.id }) }
            }
        }
    }
    history.firstOrNull { it.id == openId }?.let { HistoryDetailSheet(it, onDismiss = { openId = null }) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenLightPreview() = CalEventTheme(ThemeMode.LIGHT) { HomeScreen(remember { ScanViewModel() }, {}, {}, {}, {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenDarkPreview() = CalEventTheme(ThemeMode.DARK) { HomeScreen(remember { ScanViewModel() }, {}, {}, {}, {}) }
