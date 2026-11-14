package fr.calevent.app.ui.screens.results

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
import androidx.compose.ui.draw.drawBehind
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
import fr.calevent.core.Slot
import fr.calevent.core.SlotStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.FRENCH).withResolverStyle(ResolverStyle.STRICT)
private val TIME_REGEX = Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")

/** Strict "HH:mm" parser; null when [text] is not a valid time. */
internal fun parseTime(text: String): LocalTime? =
    TIME_REGEX.matchEntire(text.trim())?.let { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }

private fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim(), DATE_FORMAT) }.getOrNull()

/**
 * Slot sheet (Material 3 modal bottom sheet). Edits are saved through [onUpdate] as soon as they are valid;
 * Retain / Ignore apply `keep()` / `ignore()` then close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SlotSheet(slot: Slot, onDismiss: () -> Unit, onUpdate: (Slot) -> Unit) {
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
        SlotSheetContent(slot = slot, onUpdate = onUpdate, onClose = onDismiss)
    }
}

/** Content of the sheet, separate from the Material container so that it can be previewed. */
@Composable
internal fun SlotSheetContent(slot: Slot, onUpdate: (Slot) -> Unit, onClose: () -> Unit) {
    val c = CalEventTheme.colors
    // Text being typed lives here; the slot is only replaced when the text is valid.
    var title by remember(slot.id) { mutableStateOf(slot.title) }
    var dateText by remember(slot.id) { mutableStateOf(slot.date.format(DATE_FORMAT)) }
    var startText by remember(slot.id) { mutableStateOf(slot.start?.format(TIME_FORMAT).orEmpty()) }
    var endText by remember(slot.id) { mutableStateOf(slot.end?.format(TIME_FORMAT).orEmpty()) }
    var location by remember(slot.id) { mutableStateOf(slot.location.orEmpty()) }
    var person by remember(slot.id) { mutableStateOf(slot.person.orEmpty()) }

    val titleError = title.isBlank()
    val dateError = parseDate(dateText) == null
    val startError = startText.isNotBlank() && parseTime(startText) == null
    val endError = endText.isNotBlank() && parseTime(endText) == null
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
            SourceCrop(slot)
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
            LabeledField(stringResource(R.string.slot_field_date), dateText, {
                dateText = it
                parseDate(it)?.let { d -> onUpdate(slot.copy(date = d)) }
            })
            if (dateError) FieldError(stringResource(R.string.slot_error_date))
            if (!slot.allDay) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledField(stringResource(R.string.slot_field_start), startText, {
                        startText = it
                        if (it.isBlank()) onUpdate(slot.copy(start = null)) else parseTime(it)?.let { t -> onUpdate(slot.copy(start = t)) }
                    }, Modifier.weight(1f))
                    LabeledField(stringResource(R.string.slot_field_end), endText, {
                        endText = it
                        if (it.isBlank()) onUpdate(slot.copy(end = null)) else parseTime(it)?.let { t -> onUpdate(slot.copy(end = t)) }
                    }, Modifier.weight(1f))
                }
                if (startError || endError) FieldError(stringResource(R.string.slot_error_time))
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
private fun SourceCrop(slot: Slot) {
    val c = CalEventTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.slot_read_on_photo),
            style = CalEventTheme.type.label.copy(fontSize = 12.sp),
            color = c.ink2,
        )
        Box(
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
