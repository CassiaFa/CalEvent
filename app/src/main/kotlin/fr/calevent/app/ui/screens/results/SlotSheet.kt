package fr.calevent.app.ui.screens.results

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
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
import fr.calevent.app.ui.PhotoTurn
import fr.calevent.core.Slot
import fr.calevent.core.SlotStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_LABEL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM uuuu", Locale.FRANCE)

internal val DEFAULT_START: LocalTime = LocalTime.of(9, 0)
internal val DEFAULT_END: LocalTime = LocalTime.of(17, 0)

/** True when the end time is the doubtful part of a slot to review (date doubts are flagged on the date). */
internal fun Slot.endToConfirm(): Boolean = status == SlotStatus.TO_REVIEW && !dateUncertain

/** The slot with new times; a night (end before start) automatically ends the next day. */
internal fun Slot.withTimes(start: LocalTime?, end: LocalTime?): Slot =
    copy(start = start, end = end, endsNextDay = start != null && end != null && end < start)

/**
 * Slot sheet (Material 3 modal bottom sheet). Edits are saved through [onUpdate] as soon as they are valid;
 * Retain / Ignore apply `keep()` / `ignore()` then close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SlotSheet(slot: Slot, onDismiss: () -> Unit, onUpdate: (Slot) -> Unit, photo: Bitmap? = null, turn: PhotoTurn = PhotoTurn.None, onDefineCode: ((String) -> Unit)? = null) {
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
        SlotSheetContent(slot = slot, onUpdate = onUpdate, onClose = onDismiss, photo = photo, turn = turn, onDefineCode = onDefineCode?.let { f -> { code -> onDismiss(); f(code) } })
    }
}

/** Content of the sheet, separate from the Material container so that it can be previewed. */
@Composable
internal fun SlotSheetContent(slot: Slot, onUpdate: (Slot) -> Unit, onClose: () -> Unit, photo: Bitmap? = null, turn: PhotoTurn = PhotoTurn.None, onDefineCode: ((String) -> Unit)? = null) {
    val c = CalEventTheme.colors
    // Text being typed lives here; the slot is only replaced when the text is valid.
    var title by remember(slot.id) { mutableStateOf(slot.title) }
    var location by remember(slot.id) { mutableStateOf(slot.location.orEmpty()) }
    var person by remember(slot.id) { mutableStateOf(slot.person.orEmpty()) }

    var dateOpen by remember(slot.id) { mutableStateOf(false) }
    var timeTab by remember(slot.id) { mutableStateOf<Int?>(null) }

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
            SourceCrop(slot, photo, turn)
            if (status != SlotStatus.KEPT && slot.reviewReason != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(CalEventIcons.Warning, contentDescription = null, tint = c.review, modifier = Modifier.size(18.dp))
                    Text(slot.reviewReason!!, style = CalEventTheme.type.chip.copy(fontSize = 14.sp), color = c.review)
                }
            }
            val unknown = slot.unknownCode
            if (unknown != null && onDefineCode != null) {
                SecondaryButton(
                    stringResource(R.string.codes_define),
                    onClick = { onDefineCode(unknown) },
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LabeledField(stringResource(R.string.slot_field_title), title, {
                title = it
                if (it.isNotBlank()) onUpdate(slot.copy(title = it.trim()))
            })
            if (titleError) FieldError(stringResource(R.string.slot_error_title))
            val dateToConfirm = slot.dateUncertain
            PickerField(
                label = stringResource(if (dateToConfirm) R.string.slot_field_date_confirm else R.string.slot_field_date_label),
                value = slot.date.format(DATE_LABEL_FORMAT).replaceFirstChar { it.uppercase(Locale.FRANCE) },
                icon = CalEventIcons.Calendar,
                onClick = { dateOpen = true },
                onClickLabel = stringResource(R.string.slot_date_pick_cd),
                review = dateToConfirm,
            )
            if (!slot.allDay) {
                val endToConfirm = slot.endToConfirm()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerField(
                        label = stringResource(R.string.slot_field_start_label),
                        value = slot.start?.format(TIME_FORMAT) ?: "--:--",
                        icon = CalEventIcons.Clock,
                        onClick = { timeTab = 0 },
                        onClickLabel = stringResource(R.string.slot_start_pick_cd),
                        review = false,
                        modifier = Modifier.weight(1f),
                    )
                    PickerField(
                        label = stringResource(if (endToConfirm) R.string.slot_field_end_confirm else R.string.slot_field_end_label),
                        value = (slot.end?.format(TIME_FORMAT) ?: "--:--") + if (slot.endsNextDay) " ${stringResource(R.string.slot_next_day)}" else "",
                        icon = CalEventIcons.Clock,
                        onClick = { timeTab = 1 },
                        onClickLabel = stringResource(R.string.slot_end_pick_cd),
                        review = endToConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
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
    if (dateOpen) {
        DatePickerSheet(slot, onDismiss = { dateOpen = false }, onPick = {
            onUpdate(slot.copy(date = it))
            dateOpen = false
        })
    }
    timeTab?.let { tab ->
        TimePickerSheet(slot, tab, onDismiss = { timeTab = null }, onPick = { s, e ->
            onUpdate(slot.withTimes(s, e))
            timeTab = null
        })
    }
}

/** Read-only tappable field (Date, Début, Fin); review colours + warning icon when the value is to confirm. */
@Composable
private fun PickerField(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    onClickLabel: String,
    review: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = CalEventTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val tint = if (review) c.review else c.ink2
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (review) c.reviewBg else c.surface2)
            .then(if (review) Modifier.border(2.dp, c.review, shape) else Modifier)
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (review) Icon(CalEventIcons.Warning, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                Text(label, style = CalEventTheme.type.chip.copy(fontSize = 12.sp), color = tint)
            }
            Text(value, style = CalEventTheme.type.bodyStrong.copy(fontFeatureSettings = "tnum"), color = c.ink)
        }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
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
private fun SourceCrop(slot: Slot, photo: Bitmap?, turn: PhotoTurn) {
    val c = CalEventTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.slot_read_on_photo),
            style = CalEventTheme.type.label.copy(fontSize = 12.sp),
            color = c.ink2,
        )
        val crop = remember(photo, slot.region, turn) { cropAround(photo, slot.region, turn) }
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
private fun cropAround(photo: Bitmap?, region: Region?, turn: PhotoTurn = PhotoTurn.None): Bitmap? {
    if (photo == null || region == null) return null
    val marginX = (region.right - region.left) * 0.15 + 0.005
    val marginY = (region.bottom - region.top) * 0.4 + 0.005
    val x = ((region.left - marginX).coerceAtLeast(0.0) * photo.width).toInt().coerceIn(0, photo.width - 1)
    val y = ((region.top - marginY).coerceAtLeast(0.0) * photo.height).toInt().coerceIn(0, photo.height - 1)
    val right = ((region.right + marginX).coerceAtMost(1.0) * photo.width).toInt().coerceIn(x + 1, photo.width)
    val bottom = ((region.bottom + marginY).coerceAtMost(1.0) * photo.height).toInt().coerceIn(y + 1, photo.height)
    // The parser works in the upright frame: turn the extract by the parse rotation, minus the row tilt.
    val skew = if (kotlin.math.abs(turn.skewDegrees) < 0.3) 0.0 else turn.skewDegrees
    val degrees = turn.rotationDegrees - skew.toFloat()
    return runCatching {
        if (degrees == 0f) Bitmap.createBitmap(photo, x, y, right - x, bottom - y)
        else Bitmap.createBitmap(photo, x, y, right - x, bottom - y, android.graphics.Matrix().apply { postRotate(degrees) }, true)
    }.getOrNull()
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
