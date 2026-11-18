package fr.calevent.app.ui.screens.results

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import java.time.Instant
import java.time.ZoneOffset
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.calevent.app.R
import fr.calevent.app.data.FakeData
import fr.calevent.app.ui.components.IconActionButton
import fr.calevent.app.ui.components.LabeledField
import fr.calevent.app.ui.components.PrimaryButton
import fr.calevent.app.ui.components.SecondaryButton
import fr.calevent.app.ui.components.StatusChip
import fr.calevent.app.ui.components.ToggleRow
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode
import fr.calevent.core.Region
import fr.calevent.core.Slot
import fr.calevent.core.SlotStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_LABEL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM uuuu", Locale.FRANCE)

private val DEFAULT_START: LocalTime = LocalTime.of(9, 0)
private val DEFAULT_END: LocalTime = LocalTime.of(17, 0)

/** The slot with new times; a night (end before start) automatically ends the next day. */
private fun Slot.withTimes(start: LocalTime?, end: LocalTime?): Slot =
    copy(start = start, end = end, endsNextDay = start != null && end != null && end < start)

/**
 * Slot sheet (Material 3 modal bottom sheet). Edits are saved through [onUpdate] as soon as they are valid;
 * Retain / Ignore apply `keep()` / `ignore()` then close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SlotSheet(slot: Slot, onDismiss: () -> Unit, onUpdate: (Slot) -> Unit, photo: Bitmap? = null) {
    val c = CalEventTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = c.surface,
        contentColor = c.ink,
        scrimColor = c.scrim,
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.line) },
    ) {
        SlotSheetContent(slot = slot, onUpdate = onUpdate, onClose = onDismiss, photo = photo)
    }
}

/** Content of the sheet, separate from the Material container so that it can be previewed. */
@Composable
internal fun SlotSheetContent(slot: Slot, onUpdate: (Slot) -> Unit, onClose: () -> Unit, photo: Bitmap? = null) {
    val c = CalEventTheme.colors
    // Text being typed lives here; the slot is only replaced when the text is valid.
    var title by remember(slot.id) { mutableStateOf(slot.title) }
    var location by remember(slot.id) { mutableStateOf(slot.location.orEmpty()) }
    var person by remember(slot.id) { mutableStateOf(slot.person.orEmpty()) }

    val titleError = title.isBlank()
    val status = slot.status

    Column(Modifier.fillMaxWidth().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val label = stringResource(
                when (status) {
                    SlotStatus.KEPT -> R.string.status_kept
                    SlotStatus.TO_REVIEW -> R.string.status_review
                    SlotStatus.IGNORED -> R.string.status_ignored
                },
            )
            StatusChip(status, text = stringResource(R.string.slot_status_confidence, label, (slot.confidence * 100).toInt()))
            IconActionButton(CalEventIcons.Close, stringResource(R.string.slot_sheet_close), onClose)
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SourceCrop(slot, photo)
            if (status != SlotStatus.KEPT && slot.reviewReason != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(CalEventIcons.Warning, contentDescription = null, tint = c.review, modifier = Modifier.size(18.dp))
                    Text(slot.reviewReason!!, style = CalEventTheme.type.chip.copy(fontSize = 14.sp), color = c.review)
                }
            }
            LabeledField(stringResource(R.string.slot_field_title), title, {
                title = it
                if (it.isNotBlank()) onUpdate(slot.copy(title = it.trim()))
            })
            if (titleError) FieldError(stringResource(R.string.slot_error_title))
            DateField(slot.date) { onUpdate(slot.copy(date = it)) }
            if (!slot.allDay) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TimeWheel(
                        stringResource(R.string.slot_field_start_label), slot.start ?: DEFAULT_START,
                        onChange = { onUpdate(slot.withTimes(it, slot.end)) }, slotKey = slot.id,
                    )
                    TimeWheel(
                        stringResource(R.string.slot_field_end_label), slot.end ?: DEFAULT_END,
                        onChange = { onUpdate(slot.withTimes(slot.start, it)) }, slotKey = slot.id,
                    )
                }
                val start = slot.start
                val end = slot.end
                Text(
                    when {
                        start == null && end == null -> stringResource(R.string.slot_time_unknown)
                        else -> {
                            val range = stringResource(
                                R.string.slot_time_summary,
                                start?.format(TIME_FORMAT) ?: "--:--", end?.format(TIME_FORMAT) ?: "--:--",
                            )
                            if (slot.endsNextDay) "$range ${stringResource(R.string.slot_next_day)}" else range
                        }
                    },
                    style = CalEventTheme.type.bodyStrong.copy(fontFeatureSettings = "tnum"),
                    color = c.ink,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            ToggleRow(
                label = stringResource(R.string.slot_all_day),
                checked = slot.allDay,
                onCheckedChange = { onUpdate(slot.copy(allDay = it)) },
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField(stringResource(R.string.slot_field_location), location, {
                    location = it
                    onUpdate(slot.copy(location = it.trim().ifEmpty { null }))
                }, Modifier.weight(1f))
                LabeledField(stringResource(R.string.slot_field_person), person, {
                    person = it
                    onUpdate(slot.copy(person = it.trim().ifEmpty { null }))
                }, Modifier.weight(1f))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(c.line, size = Size(size.width, 1.dp.toPx())) }
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecondaryButton(stringResource(R.string.slot_ignore), onClick = {
                onUpdate(slot.ignore())
                onClose()
            }, modifier = Modifier.weight(1f))
            PrimaryButton(stringResource(R.string.slot_keep), onClick = {
                onUpdate(slot.keep())
                onClose()
            }, modifier = Modifier.weight(1.6f))
        }
    }
}

/** Date row: opens a French Material 3 date picker (Monday first, local dates). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate, onPick: (LocalDate) -> Unit) {
    val c = CalEventTheme.colors
    var open by remember { mutableStateOf(false) }
    val cd = stringResource(R.string.slot_date_pick_cd)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.slot_field_date_label),
            style = CalEventTheme.type.label, color = c.ink2, modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.surface2)
                .clickable(role = Role.Button, onClickLabel = cd) { open = true }
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                date.format(DATE_LABEL_FORMAT).replaceFirstChar { it.uppercase(Locale.FRANCE) },
                style = CalEventTheme.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f),
            )
            Icon(CalEventIcons.Calendar, contentDescription = null, tint = c.ink2, modifier = Modifier.size(22.dp))
        }
    }
    if (open) {
        // Force French labels and a Monday-first week whatever the system locale is.
        val base = LocalConfiguration.current
        val frConfig = remember(base) { Configuration(base).apply { setLocale(Locale.FRANCE) } }
        val baseContext = LocalContext.current
        val frContext = remember(baseContext, frConfig) { baseContext.createConfigurationContext(frConfig) }
        CompositionLocalProvider(LocalConfiguration provides frConfig, LocalContext provides frContext) {
            // The picker works in UTC midnight millis: convert from / to the local date explicitly.
            val state = rememberDatePickerState(
                initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                initialDisplayedMonthMillis = date.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { open = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            state.selectedDateMillis?.let { ms ->
                                onPick(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                            }
                            open = false
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.slot_date_ok), color = c.ink, style = CalEventTheme.type.bodyStrong) }
                },
                dismissButton = {
                    TextButton(onClick = { open = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.slot_date_cancel), color = c.ink2, style = CalEventTheme.type.bodyStrong)
                    }
                },
                colors = DatePickerDefaults.colors(containerColor = c.surface),
            ) {
                DatePicker(
                    state = state,
                    colors = DatePickerDefaults.colors(
                        containerColor = c.surface,
                        titleContentColor = c.ink2,
                        headlineContentColor = c.ink,
                        weekdayContentColor = c.ink2,
                        subheadContentColor = c.ink2,
                        navigationContentColor = c.ink,
                        yearContentColor = c.ink,
                        currentYearContentColor = c.ink,
                        selectedYearContainerColor = c.accent,
                        selectedYearContentColor = c.onAccent,
                        dayContentColor = c.ink,
                        selectedDayContainerColor = c.accent,
                        selectedDayContentColor = c.onAccent,
                        todayContentColor = c.ink,
                        todayDateBorderColor = c.brand,
                    ),
                )
            }
        }
    }
}

@Composable
private fun FieldError(text: String) {
    val c = CalEventTheme.colors
    Row(
        Modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(CalEventIcons.Error, contentDescription = null, tint = c.danger, modifier = Modifier.size(18.dp))
        Text(text, style = CalEventTheme.type.chip.copy(fontSize = 13.sp), color = c.danger)
    }
}

/** Fake cropped extract of the photo around the slot, with the text read there. */
@Composable
private fun SourceCrop(slot: Slot, photo: Bitmap?) {
    val c = CalEventTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.slot_read_on_photo),
            style = CalEventTheme.type.label.copy(fontSize = 12.sp),
            color = c.ink2,
        )
        val crop = remember(photo, slot.region) { cropAround(photo, slot.region) }
        if (crop != null) {
            val cropCd = stringResource(R.string.slot_crop_cd)
            Image(
                bitmap = remember(crop) { crop.asImageBitmap() },
                contentDescription = cropCd,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 120.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, ScenePaper.Rule, RoundedCornerShape(12.dp)),
            )
            if (slot.sourceText.isNotBlank()) {
                Text(slot.sourceText, style = CalEventTheme.type.secondary, color = c.ink2)
            }
        } else Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(ScenePaper.Paper, RoundedCornerShape(12.dp))
                .border(1.dp, ScenePaper.Rule, RoundedCornerShape(12.dp))
                .drawBehind {
                    val y = 10.dp.toPx()
                    drawRect(ScenePaper.Rule, Offset(0f, y), Size(size.width, 1.dp.toPx()))
                    drawRect(ScenePaper.Rule, Offset(0f, size.height - y), Size(size.width, 1.dp.toPx()))
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val text = slot.sourceText.ifBlank { stringResource(R.string.slot_no_source_text) }
            val review = slot.status == SlotStatus.TO_REVIEW && slot.sourceText.isNotBlank()
            Text(
                text,
                style = CalEventTheme.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
                color = ScenePaper.Ink,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .then(if (review) Modifier.dashedHighlight() else Modifier)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/** The part of [photo] around [region] (with a small margin), or null when either is missing. */
private fun cropAround(photo: Bitmap?, region: Region?): Bitmap? {
    if (photo == null || region == null) return null
    val marginX = (region.right - region.left) * 0.15 + 0.005
    val marginY = (region.bottom - region.top) * 0.4 + 0.005
    val x = ((region.left - marginX).coerceAtLeast(0.0) * photo.width).toInt().coerceIn(0, photo.width - 1)
    val y = ((region.top - marginY).coerceAtLeast(0.0) * photo.height).toInt().coerceIn(0, photo.height - 1)
    val right = ((region.right + marginX).coerceAtMost(1.0) * photo.width).toInt().coerceIn(x + 1, photo.width)
    val bottom = ((region.bottom + marginY).coerceAtMost(1.0) * photo.height).toInt().coerceIn(y + 1, photo.height)
    return runCatching { Bitmap.createBitmap(photo, x, y, right - x, bottom - y) }.getOrNull()
}

/** Dashed outline around the text that raised doubts (style, not colour alone: it is also written below). */
@Composable
private fun Modifier.dashedHighlight(): Modifier {
    val color = CalEventTheme.colors.review
    return drawBehind {
        val w = 2.dp.toPx()
        drawRoundRect(
            color,
            topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 3, w * 2.5f))),
        )
    }
}

@Composable
private fun SheetPreview(mode: ThemeMode) = CalEventTheme(mode) {
    val c = CalEventTheme.colors
    Box(Modifier.fillMaxWidth().height(844.dp).background(c.bg)) {
        Box(Modifier.matchParentSizeScrim())
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(top = 132.dp)
                .background(c.surface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp).size(width = 40.dp, height = 5.dp).background(c.line, RoundedCornerShape(3.dp)))
            SlotSheetContent(FakeData.weeklySlots.first { it.id == "w04" }, {}, {})
        }
    }
}

@Composable
private fun Modifier.matchParentSizeScrim(): Modifier = fillMaxWidth().height(844.dp).background(CalEventTheme.colors.scrim)

@Preview(name = "Sheet light", widthDp = 390, heightDp = 844)
@Composable
private fun SheetLightPreview() = SheetPreview(ThemeMode.LIGHT)

@Preview(name = "Sheet dark", widthDp = 390, heightDp = 844)
@Composable
private fun SheetDarkPreview() = SheetPreview(ThemeMode.DARK)
