package fr.calevent.app.ui.screens.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import fr.calevent.app.R
import fr.calevent.app.ui.components.BottomActionBar
import fr.calevent.app.ui.components.LabeledField
import fr.calevent.app.ui.components.PrimaryButton
import fr.calevent.app.ui.components.ScreenScaffold
import fr.calevent.app.ui.components.SecondaryButton
import fr.calevent.app.ui.components.SegmentOption
import fr.calevent.app.ui.components.SegmentedControl
import fr.calevent.app.ui.components.TertiaryButton
import fr.calevent.app.ui.components.ToggleRow
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.screens.results.TimePickerSheet
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode
import fr.calevent.core.ShiftCode
import fr.calevent.core.ShiftCodeTable
import fr.calevent.core.ShiftType
import fr.calevent.core.Slot
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private fun LocalTime?.label(): String = this?.format(HHMM) ?: "--:--"

/** What the form edits: a new code (optionally prefilled) or an existing one. */
private sealed interface Editing {
    data object Adding : Editing
    data class Existing(val code: String) : Editing
}

/**
 * Shift code table (9b). [newCode] is set when the screen is opened from the results for a code that is
 * not in the table yet: a highlighted card with the code prefilled is shown on top.
 * Every change goes through [onChange] (the caller persists it); [onReset] deletes every code (the table has no built-in defaults).
 */
@Composable
fun CodesScreen(
    table: ShiftCodeTable,
    newCode: String?,
    onChange: (ShiftCodeTable) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    val c = CalEventTheme.colors
    var editing by remember { mutableStateOf<Editing?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val showNewCard = newCode != null && table.find(newCode) == null

    ScreenScaffold(
        title = stringResource(R.string.codes_title),
        onBack = onBack,
        bottomBar = {
            BottomActionBar {
                SecondaryButton(
                    stringResource(R.string.codes_add),
                    onClick = { editing = Editing.Adding },
                    icon = CalEventIcons.Plus,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.codes_subtitle), style = CalEventTheme.type.secondary, color = c.ink2, modifier = Modifier.padding(start = 4.dp))
            if (showNewCard) {
                CodeForm(
                    initial = null,
                    prefillCode = newCode!!.trim(),
                    table = table,
                    highlighted = true,
                    onCancel = onBack,
                    onSave = { onChange(table.with(it)); onBack() },
                )
            }
            if (table.codes.isEmpty()) {
                Text(stringResource(R.string.codes_empty), style = CalEventTheme.type.body, color = c.ink2)
            } else {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp))) {
                    table.codes.forEachIndexed { i, entry ->
                        if (i > 0) RowDivider()
                        CodeRow(entry, onClick = { editing = Editing.Existing(entry.code) })
                    }
                }
            }
            if (table.codes.isNotEmpty()) TertiaryButton(stringResource(R.string.codes_reset), onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth())
        }
    }

    editing?.let { e ->
        val existing = (e as? Editing.Existing)?.let { table.find(it.code) }
        CodeEditSheet(
            existing = existing,
            table = table,
            onDismiss = { editing = null },
            onSave = { saved ->
                onChange(if (existing != null) table.update(existing.code, saved) else table.add(saved))
                editing = null
            },
            onDelete = { pendingDelete = existing?.code },
        )
    }
    pendingDelete?.let { code ->
        ConfirmDialog(
            title = stringResource(R.string.codes_delete_title, code),
            message = stringResource(R.string.codes_delete_message),
            confirm = stringResource(R.string.codes_delete_confirm),
            onConfirm = { onChange(table.without(code)); pendingDelete = null; editing = null },
            onDismiss = { pendingDelete = null },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.codes_reset_title),
            message = stringResource(R.string.codes_reset_message),
            confirm = stringResource(R.string.codes_reset_confirm),
            onConfirm = { onReset(); confirmReset = false; editing = null },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = CalEventTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, style = CalEventTheme.type.section, color = c.ink) },
        text = { Text(message, style = CalEventTheme.type.body, color = c.ink2) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 44.dp)) { Text(confirm, color = c.danger) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(R.string.codes_cancel), color = c.ink) } },
    )
}

@Composable
private fun CodeChip(code: String) {
    val c = CalEventTheme.colors
    Box(
        Modifier.widthIn(min = 48.dp).heightIn(min = 32.dp).background(c.surface2, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(code, style = CalEventTheme.type.code, color = c.ink, maxLines = 1)
    }
}

@Composable
private fun detailsOf(entry: ShiftCode): String = when {
    entry.type == ShiftType.ABSENCE -> stringResource(R.string.codes_all_day_no_detail)
    entry.allDay -> stringResource(R.string.codes_all_day)
    entry.endsNextDay -> stringResource(R.string.codes_hours_night, entry.start.label(), entry.end.label())
    else -> stringResource(R.string.codes_hours, entry.start.label(), entry.end.label())
}

@Composable
private fun CodeRow(entry: ShiftCode, onClick: () -> Unit) {
    val c = CalEventTheme.colors
    val cd = stringResource(R.string.codes_edit_cd, entry.code)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$cd, ${entry.label}" }
            .padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CodeChip(entry.code)
        Column(Modifier.weight(1f)) {
            Text(entry.label, style = CalEventTheme.type.bodyStrong, color = c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (entry.endsNextDay) {
                    Icon(CalEventIcons.Moon, contentDescription = stringResource(R.string.codes_night_cd), tint = c.ink2, modifier = Modifier.size(14.dp))
                }
                Text(detailsOf(entry), style = CalEventTheme.type.secondary, color = c.ink2)
            }
        }
        Icon(CalEventIcons.Pencil, contentDescription = null, tint = c.ink2, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeEditSheet(
    existing: ShiftCode?,
    table: ShiftCodeTable,
    onDismiss: () -> Unit,
    onSave: (ShiftCode) -> Unit,
    onDelete: () -> Unit,
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
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(if (existing == null) R.string.codes_add_title else R.string.codes_edit_title),
                style = CalEventTheme.type.section, color = c.ink,
            )
            CodeForm(initial = existing, prefillCode = null, table = table, highlighted = false, onCancel = onDismiss, onSave = onSave)
            if (existing != null) {
                TertiaryButton(stringResource(R.string.codes_delete), onClick = onDelete, icon = CalEventIcons.Trash, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Editable form for one code. [highlighted] gives the "new code found" card look (review colours). */
@Composable
private fun CodeForm(
    initial: ShiftCode?,
    prefillCode: String?,
    table: ShiftCodeTable,
    highlighted: Boolean,
    onCancel: () -> Unit,
    onSave: (ShiftCode) -> Unit,
) {
    val c = CalEventTheme.colors
    var code by remember(initial, prefillCode) { mutableStateOf(initial?.code ?: prefillCode.orEmpty()) }
    var label by remember(initial) { mutableStateOf(initial?.label.orEmpty()) }
    var type by remember(initial) { mutableStateOf(initial?.type ?: ShiftType.WORK) }
    var start by remember(initial) { mutableStateOf(initial?.start) }
    var end by remember(initial) { mutableStateOf(initial?.end) }
    var allDay by remember(initial) { mutableStateOf(initial?.allDay ?: false) }
    var nextDay by remember(initial) { mutableStateOf(initial?.endsNextDay ?: false) }
    var timeTab by remember { mutableStateOf<Int?>(null) }
    var showErrors by remember { mutableStateOf(false) }

    val types = listOf(ShiftType.WORK, ShiftType.REST, ShiftType.ABSENCE)
    val timed = type == ShiftType.WORK && !allDay
    val codeText = code.trim()
    val clash = table.find(codeText)?.let { !it.code.equals(initial?.code, ignoreCase = true) || initial == null } ?: false
    val errorCode = when {
        codeText.isBlank() -> R.string.codes_error_code_blank
        clash -> R.string.codes_error_code_exists
        else -> null
    }
    val errorLabel = if (type != ShiftType.ABSENCE && label.isBlank()) R.string.codes_error_label else null
    val errorTimes = if (timed && (start == null || end == null)) R.string.codes_error_times else null
    val valid = errorCode == null && errorLabel == null && errorTimes == null

    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (highlighted) Modifier.background(c.surface, shape).border(BorderStroke(2.dp, c.review), shape).padding(12.dp) else Modifier),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (highlighted) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(CalEventIcons.Warning, contentDescription = null, tint = c.review, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.codes_new_found), style = CalEventTheme.type.chip, color = c.review)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledField(
                stringResource(R.string.codes_field_code), code, { code = it.take(8) },
                Modifier.weight(0.8f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )
            LabeledField(stringResource(R.string.codes_field_label), label, { label = it }, Modifier.weight(1.4f))
        }
        if (showErrors) listOfNotNull(errorCode, errorLabel, errorTimes).forEach {
            Text(stringResource(it), style = CalEventTheme.type.secondary, color = c.danger)
        }
        SegmentedControl(
            options = listOf(
                SegmentOption(stringResource(R.string.codes_type_work)),
                SegmentOption(stringResource(R.string.codes_type_rest)),
                SegmentOption(stringResource(R.string.codes_type_absence)),
            ),
            selectedIndex = types.indexOf(type),
            onSelect = { type = types[it] },
        )
        if (type == ShiftType.WORK) {
            if (timed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickField(stringResource(R.string.codes_field_start), start.label(), stringResource(R.string.codes_pick_start_cd), Modifier.weight(1f)) { timeTab = 0 }
                    PickField(stringResource(R.string.codes_field_end), end.label(), stringResource(R.string.codes_pick_end_cd), Modifier.weight(1f)) { timeTab = 1 }
                }
            }
            ToggleRow(stringResource(R.string.codes_all_day), allDay, { allDay = it; if (it) nextDay = false })
            if (timed) ToggleRow(stringResource(R.string.codes_next_day), nextDay, { nextDay = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
            SecondaryButton(stringResource(R.string.codes_cancel), onClick = onCancel, modifier = Modifier.weight(1f))
            PrimaryButton(
                if (codeText.isNotBlank() && highlighted) stringResource(R.string.codes_save_named, codeText) else stringResource(R.string.codes_save),
                onClick = {
                    if (!valid) { showErrors = true; return@PrimaryButton }
                    onSave(
                        ShiftCode(
                            code = codeText, label = label.trim().ifBlank { codeText }, type = type,
                            start = start.takeIf { timed }, end = end.takeIf { timed },
                            allDay = type != ShiftType.WORK || allDay, endsNextDay = timed && nextDay,
                        ),
                    )
                },
                modifier = Modifier.weight(1.4f),
            )
        }
    }
    timeTab?.let { tab ->
        val picker = Slot(id = "code", title = "", date = LocalDate.now(), start = start, end = end, confidence = 1.0)
        TimePickerSheet(picker, tab, onDismiss = { timeTab = null }, onPick = { s, e ->
            start = s; end = e
            // Like the slot sheet: an end before the start means the shift ends the next day.
            if (s != null && e != null) nextDay = e < s
            timeTab = null
        })
    }
}

/** Read-only tappable field that opens the time picker sheet. */
@Composable
private fun PickField(label: String, value: String, clickLabel: String, modifier: Modifier, onClick: () -> Unit) {
    val c = CalEventTheme.colors
    Column(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface2)
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = CalEventTheme.type.chip, color = c.ink2)
        Text(value, style = CalEventTheme.type.bodyStrong.copy(fontFeatureSettings = "tnum"), color = c.ink, textAlign = TextAlign.Start)
    }
}

@Composable
private fun PreviewBody(mode: ThemeMode, newCode: String?) = CalEventTheme(mode) {
    CodesScreen(ShiftCodeTable(), newCode, {}, {}, {})
}

@Preview(name = "Codes light", widthDp = 390, heightDp = 844)
@Composable
private fun CodesLightPreview() = PreviewBody(ThemeMode.LIGHT, null)

@Preview(name = "Codes dark", widthDp = 390, heightDp = 844)
@Composable
private fun CodesDarkPreview() = PreviewBody(ThemeMode.DARK, null)

@Preview(name = "Codes new light", widthDp = 390, heightDp = 844)
@Composable
private fun CodesNewLightPreview() = PreviewBody(ThemeMode.LIGHT, "FX")

@Preview(name = "Codes new dark", widthDp = 390, heightDp = 844)
@Composable
private fun CodesNewDarkPreview() = PreviewBody(ThemeMode.DARK, "FX")
