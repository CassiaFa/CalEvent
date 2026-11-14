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
import fr.calevent.app.data.PlanningKind
import fr.calevent.app.data.RecentPlanning
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
 * @param onTakePhoto start the camera. @param onImport pick a photo from the gallery.
 * @param onOpenSettings open settings. @param onOpenRecent open a recent planning (its id).
 * @param onSeeAll "Tout voir" link of the recent list.
 */
@Composable
fun HomeScreen(
    viewModel: ScanViewModel,
    onTakePhoto: () -> Unit,
    onImport: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecent: (String) -> Unit,
    onSeeAll: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
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
                    onClick = onImport,
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
                state.recentPlannings.forEach { RecentPlanningCard(it, onClick = { onOpenRecent(it.id) }) }
            }
        }
    }
}

@Composable
private fun RecentPlanningCard(planning: RecentPlanning, onClick: () -> Unit) {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(c.surface, shape)
            .border(1.dp, c.line, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PlanningPhotoPlaceholder(
            variant = when (planning.kind) {
                PlanningKind.WEEKLY -> PlanningPhotoVariant.Weekly
                PlanningKind.TEAM_CODES -> PlanningPhotoVariant.TeamCodes
                PlanningKind.WALL_CALENDAR -> PlanningPhotoVariant.WallCalendar
            },
            modifier = Modifier
                .size(width = 52.dp, height = 56.dp)
                .rotate(-3f)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, c.line, RoundedCornerShape(10.dp)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(planning.title, style = t.bodyStrong, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(planning.subtitle, style = t.secondary, color = c.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (planning.toReviewCount > 0) {
            StatusChip(SlotStatus.TO_REVIEW, pluralStringResource(R.plurals.recent_to_review, planning.toReviewCount, planning.toReviewCount), compact = true)
        } else {
            StatusChip(SlotStatus.KEPT, pluralStringResource(R.plurals.recent_added, planning.addedCount, planning.addedCount), compact = true)
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenLightPreview() = CalEventTheme(ThemeMode.LIGHT) { HomeScreen(remember { ScanViewModel() }, {}, {}, {}, {}, {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenDarkPreview() = CalEventTheme(ThemeMode.DARK) { HomeScreen(remember { ScanViewModel() }, {}, {}, {}, {}, {}) }
