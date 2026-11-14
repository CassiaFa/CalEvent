package fr.calevent.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.calevent.app.R
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.core.Slot
import fr.calevent.core.SlotStatus
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Draws a dashed rounded outline inside the bounds (like CSS `outline-offset: -width`). */
private fun Modifier.dashedOutline(color: Color, width: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp) =
    drawBehind {
        val w = width.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius(radius.toPx() - w / 2),
            style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 3, w * 2.5f))),
        )
    }

/** "06:00 – 14:00", "06:00 – 07:00 (+1 j)", "Journée entière" or "Heure inconnue". */
@Composable
fun slotTimeText(slot: Slot): String {
    if (slot.allDay) return stringResource(R.string.slot_all_day)
    val start = slot.start ?: return stringResource(R.string.slot_time_unknown)
    val end = slot.end ?: return start.format(TIME_FORMAT)
    val range = stringResource(R.string.slot_time_range, start.format(TIME_FORMAT), end.format(TIME_FORMAT))
    return if (slot.endsNextDay) "$range ${stringResource(R.string.slot_next_day)}" else range
}

/**
 * Card for one detected [Slot]. Look depends on [Slot.status]:
 * kept = plain card with a blue checked box; to review = tinted, dashed amber-brown outline,
 * written reason and "À revoir" chip; ignored = dimmed, dashed grey outline and chip.
 *
 * @param onToggle tap on the 44 dp checkbox (the caller flips keep / ignore).
 * @param onClick tap on the card body (open the slot sheet).
 * @param showStatusChip defaults to false for kept slots, whose blue box already says it.
 */
@Composable
fun SlotCard(
    slot: Slot,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showStatusChip: Boolean = slot.status != SlotStatus.KEPT,
) {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    val status = slot.status
    val shape = RoundedCornerShape(14.dp)
    val cardModifier = when (status) {
        SlotStatus.KEPT -> Modifier.background(c.surface, shape).border(1.dp, c.line, shape)
        SlotStatus.TO_REVIEW -> Modifier.background(c.reviewBg, shape).dashedOutline(c.review, 2.dp, 14.dp)
        SlotStatus.IGNORED -> Modifier.border(1.5.dp, c.line, shape).dashedOutline(c.ignored, 1.5.dp, 14.dp)
    }
    val ignored = status == SlotStatus.IGNORED
    val detail = listOfNotNull(slotTimeText(slot), slot.location?.takeIf { it.isNotBlank() }).let {
        if (it.size == 2) stringResource(R.string.slot_detail_separator, it[0], it[1]) else it[0]
    }
    val checkDescription = stringResource(R.string.slot_select_cd, slot.title)

    Row(
        modifier = modifier
            .then(cardModifier)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(start = 4.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .toggleable(value = slot.isSelected, role = Role.Checkbox, onValueChange = { onToggle() })
                .semantics { contentDescription = checkDescription },
            contentAlignment = Alignment.Center,
        ) {
            val boxShape = RoundedCornerShape(7.dp)
            val borderColor = if (ignored) c.ignored else c.review
            if (slot.isSelected) {
                Box(Modifier.size(24.dp).background(c.kept, boxShape), contentAlignment = Alignment.Center) {
                    Icon(CalEventIcons.CheckBold, contentDescription = null, tint = c.surface, modifier = Modifier.size(16.dp))
                }
            } else {
                Box(Modifier.size(24.dp).border(2.dp, borderColor, boxShape))
            }
        }
        Column(modifier = Modifier.width(38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                slot.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).trimEnd('.').uppercase(Locale.FRENCH),
                style = t.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp),
                color = c.ink2,
            )
            Text(
                slot.date.dayOfMonth.toString(),
                style = t.section.copy(lineHeight = 22.sp, fontFeatureSettings = "tnum"),
                color = if (ignored) c.ignored else c.ink,
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(slot.title, style = t.bodyStrong, color = if (ignored) c.ink2 else c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (ignored && slot.reviewReason != null) {
                Text(slot.reviewReason!!, style = t.secondary, color = c.ink2)
            } else {
                Text(detail, style = t.secondary, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (status == SlotStatus.TO_REVIEW && slot.reviewReason != null) {
                Text(slot.reviewReason!!, style = t.chip, color = c.review)
            }
        }
        if (showStatusChip) {
            StatusChip(status, containerColor = if (status == SlotStatus.TO_REVIEW) c.surface else Color.Unspecified)
        }
    }
}
