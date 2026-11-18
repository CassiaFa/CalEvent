package fr.calevent.app.ui.screens.history

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.calevent.app.R
import fr.calevent.app.history.HistoryRepository
import fr.calevent.app.ui.components.PlanningPhotoPlaceholder
import fr.calevent.app.ui.components.PlanningPhotoVariant
import fr.calevent.app.ui.components.StatusChip
import fr.calevent.app.ui.components.TertiaryButton
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.core.History
import fr.calevent.core.HistoryEntry
import fr.calevent.core.SlotStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ADDED_FORMAT = DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.FRENCH)

private fun addedAt(e: HistoryEntry): String = ADDED_FORMAT.format(Instant.ofEpochMilli(e.createdAtMillis).atZone(ZoneId.systemDefault()))

@Composable
private fun entryTitle(e: HistoryEntry): String {
    val label = if (e.isTeam) e.personName ?: stringResource(R.string.history_kind_default) else e.kindLabel ?: stringResource(R.string.history_kind_default)
    return stringResource(R.string.history_subtitle, label, History.dateRangeLabel(e.from, e.to))
}

/** Thumbnail of [entry] when it still has one, else the drawn placeholder. */
@Composable
private fun HistoryThumbnail(entry: HistoryEntry, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, entry.id, entry.hasThumbnail) {
        value = if (!entry.hasThumbnail) null else withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(HistoryRepository.get(context).thumbnailFile(entry.id).path)?.asImageBitmap() }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(10.dp)
    val frame = modifier.clip(shape).border(1.dp, CalEventTheme.colors.line, shape)
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp, contentDescription = stringResource(R.string.history_thumb_cd), contentScale = ContentScale.Crop, modifier = frame)
    } else {
        PlanningPhotoPlaceholder(if (entry.isTeam) PlanningPhotoVariant.TeamCodes else PlanningPhotoVariant.Weekly, frame)
    }
}

/** One row of the history: thumbnail, title, date, "N créneaux ajoutés". */
@Composable
fun HistoryCard(entry: HistoryEntry, onClick: () -> Unit) {
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
        HistoryThumbnail(entry, Modifier.size(width = 52.dp, height = 56.dp).rotate(-3f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entryTitle(entry), style = t.bodyStrong, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(addedAt(entry), style = t.secondary, color = c.ink2, maxLines = 1)
            Text(pluralStringResource(R.plurals.history_added, entry.addedCount, entry.addedCount), style = t.secondary, color = c.kept, maxLines = 1)
        }
        StatusChip(SlotStatus.KEPT, pluralStringResource(R.plurals.recent_added, entry.addedCount, entry.addedCount), compact = true)
    }
}

/** Summary of one send. Reopening the results is not offered. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailSheet(entry: HistoryEntry, onDismiss: () -> Unit) {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = c.surface,
        contentColor = c.ink,
        scrimColor = c.scrim,
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.line) },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                HistoryThumbnail(entry, Modifier.size(width = 64.dp, height = 70.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(entryTitle(entry), style = t.section, color = c.ink)
                    Text(pluralStringResource(R.plurals.history_added, entry.addedCount, entry.addedCount), style = t.secondary, color = c.kept)
                }
            }
            Detail(stringResource(R.string.history_detail_calendar), entry.calendarName)
            Detail(stringResource(R.string.history_detail_period), History.dateRangeLabel(entry.from, entry.to))
            if (entry.isTeam && entry.personName != null) Detail(stringResource(R.string.history_detail_person), entry.personName!!)
            Detail(stringResource(R.string.history_detail_added), addedAt(entry))
            Text(stringResource(R.string.history_detail_note), style = t.secondary, color = c.ink2)
            TertiaryButton(stringResource(R.string.history_close), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    val c = CalEventTheme.colors
    Column {
        Text(label.uppercase(Locale.FRENCH), style = CalEventTheme.type.label, color = c.ink2)
        Text(value, style = CalEventTheme.type.body, color = c.ink)
    }
}
