package fr.calevent.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.calevent.app.R
import fr.calevent.app.data.FakeData
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode
import fr.calevent.core.SlotStatus
import java.util.Locale

/** Equivalent of the design-system board: every component once. Preview only. */
@Composable
internal fun ComponentGallery() {
    val c = CalEventTheme.colors
    var segment by remember { mutableStateOf(0) }
    var toggle by remember { mutableStateOf(true) }
    var field by remember { mutableStateOf("13:00") }
    val slots = FakeData.weeklySlots
    val kept = slots.first { it.status == SlotStatus.KEPT }
    val review = slots.first { it.status == SlotStatus.TO_REVIEW }
    val ignored = slots.first { it.status == SlotStatus.IGNORED }

    @Composable
    fun Section(title: Int, content: @Composable () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(title).uppercase(Locale.FRENCH), style = CalEventTheme.type.label, color = c.ink2)
            content()
        }
    }

    Column(
        modifier = Modifier.background(c.bg).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Section(R.string.gallery_buttons) {
            PrimaryButton(stringResource(R.string.gallery_primary), {}, Modifier.fillMaxWidth(), icon = CalEventIcons.Camera)
            PrimaryButton(stringResource(R.string.gallery_primary), {}, Modifier.fillMaxWidth(), tall = true)
            SecondaryButton(stringResource(R.string.gallery_secondary), {}, Modifier.fillMaxWidth(), icon = CalEventIcons.Gallery)
            TertiaryButton(stringResource(R.string.gallery_tertiary), {}, Modifier.fillMaxWidth())
            PrimaryButton(stringResource(R.string.gallery_disabled), {}, Modifier.fillMaxWidth(), enabled = false)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(CalEventIcons.Settings, CalEventIcons.Close, CalEventIcons.Flash, CalEventIcons.Plus, CalEventIcons.Search, CalEventIcons.Person)
                    .forEach { IconActionButton(it, stringResource(R.string.action_settings), {}) }
            }
        }
        Section(R.string.gallery_chips) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(SlotStatus.KEPT)
                StatusChip(SlotStatus.TO_REVIEW)
                StatusChip(SlotStatus.IGNORED)
            }
            OfflineBadge()
        }
        Section(R.string.gallery_cards) {
            listOf(kept, review, ignored).forEach { SlotCard(it, onToggle = {}, onClick = {}, modifier = Modifier.fillMaxWidth()) }
        }
        Section(R.string.gallery_banners) {
            Banner(BannerKind.Review, stringResource(R.string.gallery_banner_rotate), icon = CalEventIcons.RotateRight, actions = {
                SecondaryButton(stringResource(R.string.gallery_rotate_left), {}, compact = true)
                SecondaryButton(stringResource(R.string.gallery_rotate_right), {}, compact = true)
            })
            Banner(BannerKind.Info, stringResource(R.string.gallery_banner_info))
            Banner(BannerKind.Error, stringResource(R.string.gallery_banner_error))
        }
        Section(R.string.gallery_controls) {
            SegmentedControl(
                listOf(
                    SegmentOption(stringResource(R.string.gallery_segment_calendar), CalEventIcons.Calendar),
                    SegmentOption(stringResource(R.string.gallery_segment_photo), CalEventIcons.Photo),
                ),
                segment,
                { segment = it },
            )
            LabeledField(stringResource(R.string.gallery_field_start), field, { field = it })
            ToggleRow(stringResource(R.string.slot_all_day), toggle, { toggle = it })
        }
        Section(R.string.gallery_photos) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlanningPhotoVariant.entries.forEach {
                    PlanningPhotoPlaceholder(it, Modifier.size(90.dp, 110.dp).clip(RoundedCornerShape(10.dp)))
                }
            }
        }
        Section(R.string.gallery_bar) {
            BottomActionBar {
                PrimaryButton(stringResource(R.string.action_continue), {}, Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.action_back), {}, Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview(widthDp = 420, heightDp = 2300)
@Composable
private fun ComponentGalleryLightPreview() = CalEventTheme(ThemeMode.LIGHT) { ComponentGallery() }

@Preview(widthDp = 420, heightDp = 2300)
@Composable
private fun ComponentGalleryDarkPreview() = CalEventTheme(ThemeMode.DARK) { ComponentGallery() }
