package fr.calevent.app.ui.screens.results

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.calevent.app.R
import fr.calevent.app.ui.components.SwipeableSlotCard
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.core.Slot
import fr.calevent.core.SlotStatus
import fr.calevent.core.groupByMonth
import fr.calevent.core.groupByWeek
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private data class Period(val label: String, val slots: List<Slot>)

private fun monthLabel(month: YearMonth): String =
    month.month.getDisplayName(TextStyle.FULL, Locale.FRENCH).replaceFirstChar { it.uppercase(Locale.FRENCH) } + " " + month.year

/**
 * Agenda tab: "to review" callout, week / month switch with previous / next, then the slots of the
 * current period as [SlotCard]s (slots to review first). Lazy, so hundreds of slots stay smooth.
 */
@Composable
internal fun AgendaView(
    slots: List<Slot>,
    reviewCount: Int,
    onReviewClick: () -> Unit,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
    onKeep: (String) -> Unit = {},
    onIgnore: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var monthMode by rememberSaveable { mutableStateOf(false) }
    var periodIndex by rememberSaveable { mutableIntStateOf(0) }
    val periods = remember(slots, monthMode) {
        val raw = if (monthMode) {
            groupByMonth(slots).map { Period(monthLabel(it.month), it.slots) }
        } else {
            groupByWeek(slots).map { Period(it.label, it.slots) }
        }
        // Stable sort: slots to review go first, chronological order is kept inside each part.
        raw.map { p -> p.copy(slots = p.slots.sortedBy { if (it.status == SlotStatus.TO_REVIEW) 0 else 1 }) }
    }
    val index = periodIndex.coerceIn(0, (periods.size - 1).coerceAtLeast(0))
    val period = periods.getOrNull(index)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (reviewCount > 0) {
            item(key = "callout", contentType = "callout") {
                ReviewCallout(reviewCount, onReviewClick, Modifier.padding(bottom = 6.dp))
            }
        }
        item(key = "nav", contentType = "nav") {
            PeriodBar(
                label = period?.label.orEmpty(),
                monthMode = monthMode,
                canPrevious = index > 0,
                canNext = index < periods.size - 1,
                onPrevious = { periodIndex = index - 1 },
                onNext = { periodIndex = index + 1 },
                onModeChange = { monthMode = it; periodIndex = 0 },
            )
        }
        if (period == null) {
            item(key = "empty", contentType = "empty") {
                Text(
                    stringResource(R.string.results_empty),
                    style = CalEventTheme.type.body,
                    color = CalEventTheme.colors.ink2,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            items(period.slots, key = { it.id }, contentType = { "slot" }) { slot ->
                SwipeableSlotCard(
                    slot = slot,
                    onToggle = { onToggle(slot.id) },
                    onClick = { onOpen(slot.id) },
                    onKeep = { onKeep(slot.id) },
                    onIgnore = { onIgnore(slot.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ReviewCallout(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = CalEventTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(c.reviewBg, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(CalEventIcons.Warning, contentDescription = null, tint = c.review, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(
                pluralStringResource(R.plurals.results_review_callout_title, count, count),
                style = CalEventTheme.type.bodyStrong.copy(fontWeight = FontWeight.Bold),
                color = c.ink,
            )
            Text(
                stringResource(R.string.results_review_callout_hint),
                style = CalEventTheme.type.secondary.copy(fontSize = 13.sp),
                color = c.ink2,
            )
        }
        Text(
            stringResource(R.string.results_review_callout_action),
            style = CalEventTheme.type.bodyStrong.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            color = c.review,
        )
        Icon(CalEventIcons.Chevron, contentDescription = null, tint = c.review, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PeriodBar(
    label: String,
    monthMode: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onModeChange: (Boolean) -> Unit,
) {
    val c = CalEventTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            NavArrow(
                forward = false,
                enabled = canPrevious,
                description = stringResource(if (monthMode) R.string.results_period_previous_month else R.string.results_period_previous_week),
                onClick = onPrevious,
            )
            Text(label, style = CalEventTheme.type.bodyStrong.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"), color = c.ink)
            NavArrow(
                forward = true,
                enabled = canNext,
                description = stringResource(if (monthMode) R.string.results_period_next_month else R.string.results_period_next_week),
                onClick = onNext,
            )
        }
        Row(
            Modifier.background(c.surface2, RoundedCornerShape(12.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ModeButton(stringResource(R.string.results_period_week), selected = !monthMode) { onModeChange(false) }
            ModeButton(stringResource(R.string.results_period_month), selected = monthMode) { onModeChange(true) }
        }
    }
}

@Composable
private fun ModeButton(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = CalEventTheme.colors
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) c.surface else androidx.compose.ui.graphics.Color.Transparent, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = CalEventTheme.type.bodyStrong.copy(fontSize = 14.sp),
            color = if (selected) c.ink else c.ink2,
            maxLines = 1,
        )
    }
}

@Composable
private fun NavArrow(forward: Boolean, enabled: Boolean, description: String, onClick: () -> Unit) {
    val c = CalEventTheme.colors
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            CalEventIcons.Chevron,
            contentDescription = description,
            tint = if (enabled) c.ink else c.line,
            modifier = Modifier.size(22.dp).rotate(if (forward) 0f else 180f),
        )
    }
}
