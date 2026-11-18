package fr.calevent.app.ui.screens.results

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.calevent.app.R
import fr.calevent.app.ui.components.IconActionButton
import fr.calevent.app.ui.components.PrimaryButton
import fr.calevent.app.ui.components.SecondaryButton
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.core.Slot
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRANCE)
private val DAY_ARIA: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)
private val MONTH_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL uuuu", Locale.FRANCE)
private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val WEEKDAYS = listOf("L", "M", "M", "J", "V", "S", "D")

/** Modal sheet shell shared by the date and time pickers: title, close, content, Annuler / Valider. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    title: String,
    validateLabel: String,
    onDismiss: () -> Unit,
    onValidate: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = CalEventTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = c.surface,
        contentColor = c.ink,
        scrimColor = c.scrim,
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.line) },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = CalEventTheme.type.section, color = c.ink)
            IconActionButton(CalEventIcons.Close, stringResource(R.string.slot_picker_close), onDismiss)
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(c.line, size = Size(size.width, 1.dp.toPx())) }
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecondaryButton(stringResource(R.string.slot_picker_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
            PrimaryButton(validateLabel, onClick = onValidate, modifier = Modifier.weight(1.6f))
        }
    }
}

@Composable
private fun ReadOnPhoto(text: String, highlight: Boolean) {
    if (text.isBlank()) return
    val c = CalEventTheme.colors
    Text(
        stringResource(R.string.slot_picker_read, text),
        style = CalEventTheme.type.secondary,
        color = if (highlight) c.review else c.ink2,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Date picker sheet (mockup 7b): Monday-first month grid, quick chips, validate with the chosen date. */
@Composable
internal fun DatePickerSheet(slot: Slot, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val c = CalEventTheme.colors
    val original = slot.date
    var selected by remember { mutableStateOf(original) }
    var month by remember { mutableStateOf(YearMonth.from(original)) }
    val today = LocalDate.now()
    fun choose(d: LocalDate) {
        selected = d
        month = YearMonth.from(d)
    }
    PickerSheet(
        title = stringResource(R.string.slot_date_title),
        validateLabel = stringResource(R.string.slot_date_validate, selected.format(SHORT_DATE)),
        onDismiss = onDismiss,
        onValidate = { onPick(selected) },
    ) {
        ReadOnPhoto(slot.sourceText, highlight = false)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconActionButton(CalEventIcons.ChevronLeft, stringResource(R.string.slot_date_prev_month), { month = month.minusMonths(1) })
            Text(
                month.format(MONTH_TITLE).replaceFirstChar { it.uppercase(Locale.FRANCE) },
                style = CalEventTheme.type.bodyStrong.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = c.ink,
            )
            IconActionButton(CalEventIcons.Chevron, stringResource(R.string.slot_date_next_month), { month = month.plusMonths(1) })
        }
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach {
                Box(Modifier.weight(1f).heightIn(min = 28.dp), contentAlignment = Alignment.Center) {
                    Text(it, style = CalEventTheme.type.chip.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = c.ink2)
                }
            }
        }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val rows = (offset + month.lengthOfMonth() + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(rows) { r ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val day = r * 7 + col - offset + 1
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (day in 1..month.lengthOfMonth()) {
                                DayCell(month.atDay(day), selected = month.atDay(day) == selected, isToday = month.atDay(day) == today) {
                                    selected = month.atDay(day)
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuickChip(stringResource(R.string.slot_date_today)) { choose(today) }
            QuickChip(stringResource(R.string.slot_date_tomorrow)) { choose(today.plusDays(1)) }
            QuickChip(stringResource(R.string.slot_date_read, original.dayOfMonth), dot = true) { choose(original) }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, selected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val c = CalEventTheme.colors
    val base = date.format(DAY_ARIA)
    val cd = when {
        selected -> stringResource(R.string.slot_date_selected, base)
        isToday -> stringResource(R.string.slot_date_is_today, base)
        else -> base
    }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(c.ink) else if (isToday) Modifier.border(BorderStroke(1.5.dp, c.ink2), CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = cd; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = CalEventTheme.type.bodyStrong.copy(fontFeatureSettings = "tnum", fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
            color = if (selected) c.surface else c.ink,
        )
    }
}

@Composable
private fun QuickChip(label: String, dot: Boolean = false, selected: Boolean = false, onClick: () -> Unit) {
    val c = CalEventTheme.colors
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) c.ink else c.surface2)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) Box(Modifier.size(8.dp).background(c.review, CircleShape))
        Text(label, style = CalEventTheme.type.secondary.copy(fontWeight = FontWeight.SemiBold), color = if (selected) c.surface else c.ink)
    }
}

/** Time picker sheet (mockup 7c): Début / Fin tabs, hour and minute wheels, step selector, duration. */
@Composable
internal fun TimePickerSheet(slot: Slot, initialTab: Int, onDismiss: () -> Unit, onPick: (LocalTime?, LocalTime?) -> Unit) {
    val c = CalEventTheme.colors
    var tab by remember { mutableIntStateOf(initialTab) }
    var start by remember { mutableStateOf(slot.start) }
    var end by remember { mutableStateOf(slot.end) }
    var step by remember { mutableIntStateOf(5) }
    val endToConfirm = slot.endToConfirm()
    val range = stringResource(R.string.slot_time_range, start.label(), end.label())
    PickerSheet(
        title = stringResource(R.string.slot_time_title),
        validateLabel = stringResource(R.string.slot_time_validate, range),
        onDismiss = onDismiss,
        onValidate = { onPick(start, end) },
    ) {
        Row(
            Modifier.fillMaxWidth().background(c.surface2, RoundedCornerShape(14.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TimeTab(stringResource(R.string.slot_field_start_label), start.label(), tab == 0, false, Modifier.weight(1f)) { tab = 0 }
            TimeTab(
                stringResource(if (endToConfirm) R.string.slot_field_end_confirm else R.string.slot_field_end_label),
                end.label(), tab == 1, endToConfirm, Modifier.weight(1f),
            ) { tab = 1 }
        }
        key(tab, step) {
            val label = stringResource(if (tab == 0) R.string.slot_field_start_label else R.string.slot_field_end_label)
            val current = (if (tab == 0) start else end) ?: if (tab == 0) DEFAULT_START else DEFAULT_END
            TimeWheel(
                label = label,
                time = current,
                onChange = { if (tab == 0) start = it else end = it },
                slotKey = Pair(tab, step),
                minuteStep = step,
                showLabel = false,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.slot_time_step), style = CalEventTheme.type.chip, color = c.ink2, modifier = Modifier.padding(end = 2.dp))
            listOf(1, 5, 15).forEach { s ->
                QuickChip(stringResource(R.string.slot_time_step_value, s), selected = step == s) { step = s }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.weight(1f)) {
                ReadOnPhoto(slot.sourceText, highlight = endToConfirm)
            }
            val s = start
            val e = end
            if (s != null && e != null) {
                val minutes = Duration.between(s, e).toMinutes().let { if (it < 0) it + 24 * 60 else it }
                Text(
                    stringResource(
                        if (e < s) R.string.slot_time_duration_next_day else R.string.slot_time_duration,
                        minutes / 60, minutes % 60,
                    ),
                    style = CalEventTheme.type.secondary, color = c.ink2, modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TimeTab(label: String, value: String, on: Boolean, review: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = CalEventTheme.colors
    Column(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) c.surface else c.surface2)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { selected = on }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (review) androidx.compose.material3.Icon(CalEventIcons.Warning, null, tint = c.review, modifier = Modifier.size(14.dp))
            Text(label, style = CalEventTheme.type.chip.copy(fontSize = 12.sp), color = if (review) c.review else if (on) c.ink else c.ink2)
        }
        Text(
            value,
            style = CalEventTheme.type.bodyStrong.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = if (on) c.ink else c.ink2,
        )
    }
}

private fun LocalTime?.label(): String = this?.format(HHMM) ?: "--:--"
