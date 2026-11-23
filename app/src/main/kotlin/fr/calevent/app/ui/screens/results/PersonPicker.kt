package fr.calevent.app.ui.screens.results

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import fr.calevent.app.ui.components.PrimaryButton
import fr.calevent.app.ui.components.SecondaryButton
import fr.calevent.app.ui.components.TertiaryButton
import fr.calevent.app.ui.components.IconActionButton
import fr.calevent.core.findPerson
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.calevent.app.R
import fr.calevent.app.ui.components.LabeledField
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import java.text.Normalizer

/** Case and accent insensitive form used to filter names. */
private fun String.foldKey(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

/** Row under the warnings (6a/6c): shows the chosen person (or "Aucune sélectionnée"); opens the picker. */
@Composable
internal fun PersonRow(selected: String?, onClick: () -> Unit) {
    val c = CalEventTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(c.surface2, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(CalEventIcons.Person, contentDescription = null, tint = c.ink, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.results_people_label), style = CalEventTheme.type.secondary, color = c.ink2)
            Text(
                selected ?: stringResource(R.string.results_people_none_selected),
                style = CalEventTheme.type.bodyStrong,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(stringResource(R.string.results_people_change), style = CalEventTheme.type.bodyStrong, color = c.ink)
        Icon(CalEventIcons.Search, contentDescription = null, tint = c.ink2, modifier = Modifier.size(20.dp))
    }
}

/** Shown instead of the slots while nobody is chosen on a team schedule (6a). */
@Composable
internal fun PersonPrompt(peopleCount: Int, onChoose: () -> Unit) {
    val c = CalEventTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Icon(CalEventIcons.Person, contentDescription = null, tint = c.ink2, modifier = Modifier.size(32.dp))
        Text(stringResource(R.string.results_people_prompt), style = CalEventTheme.type.section, color = c.ink, textAlign = TextAlign.Center)
        Text(
            pluralStringResource(R.plurals.results_people_prompt_hint, peopleCount, peopleCount),
            style = CalEventTheme.type.secondary,
            color = c.ink2,
            textAlign = TextAlign.Center,
        )
        SecondaryButton(stringResource(R.string.results_people_choose), onClick = onChoose, modifier = Modifier.fillMaxWidth())
    }
}

private fun initials(name: String): String =
    name.split(' ', '.', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

/**
 * Person picker (6b): search, the stored "Moi" first, then everyone with their number of shifts.
 * [onSetMe] stores (or, with null, clears) the "Moi" name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonSheet(
    people: List<String>,
    counts: Map<String, Int>,
    selected: String?,
    me: String?,
    onSelect: (String?) -> Unit,
    onSetMe: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = CalEventTheme.colors
    var query by remember { mutableStateOf("") }
    val key = query.foldKey()
    val matching = remember(people, key) { if (key.isEmpty()) people else people.filter { it.foldKey().contains(key) } }
    val meName = remember(people, me) { findPerson(people, me) }
    val mine = matching.filter { it == meName }
    val others = matching.filter { it != meName }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = c.surface,
        contentColor = c.ink,
        scrimColor = c.scrim,
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.line) },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.results_people_sheet_title), style = CalEventTheme.type.section, color = c.ink, modifier = Modifier.weight(1f))
                IconActionButton(CalEventIcons.Close, stringResource(R.string.results_people_close), onClick = onDismiss)
            }
            LabeledField(stringResource(R.string.results_people_search), query, { query = it })
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 420.dp)) {
                if (mine.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.results_people_me_section)) }
                    items(mine) { name -> PersonItem(name, counts[name], name == selected, isMe = true) { onSelect(name) } }
                }
                if (others.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.results_people_all_section, people.size)) }
                    items(others) { name -> PersonItem(name, counts[name], name == selected, isMe = false) { onSelect(name) } }
                }
                if (matching.isEmpty()) {
                    item { Text(stringResource(R.string.results_people_none_found), style = CalEventTheme.type.secondary, color = c.ink2, modifier = Modifier.padding(vertical = 12.dp)) }
                }
            }
            if (selected != null) {
                if (selected != null && selected == meName) {
                    TertiaryButton(stringResource(R.string.results_people_not_me), onClick = { onSetMe(null) }, modifier = Modifier.fillMaxWidth())
                } else {
                    PrimaryButton(stringResource(R.string.results_people_its_me), onClick = { onSetMe(selected) }, modifier = Modifier.fillMaxWidth())
                }
                TertiaryButton(stringResource(R.string.results_people_clear), onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), style = CalEventTheme.type.label, color = CalEventTheme.colors.ink2, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
private fun PersonItem(name: String, count: Int?, checked: Boolean, isMe: Boolean, onClick: () -> Unit) {
    val c = CalEventTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(selected = checked, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(34.dp).background(c.surface2, RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
            Text(initials(name), style = CalEventTheme.type.chip.copy(fontWeight = FontWeight.Bold), color = c.ink2)
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = CalEventTheme.type.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (count != null) {
                Text(pluralStringResource(R.plurals.results_people_shifts, count, count), style = CalEventTheme.type.secondary, color = c.ink2)
            }
        }
        if (isMe) {
            Text(
                stringResource(R.string.results_people_me_badge),
                style = CalEventTheme.type.chip,
                color = c.kept,
                modifier = Modifier.background(c.keptBg, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        RadioButton(
            selected = checked,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = c.ink, unselectedColor = c.ink2),
        )
    }
}
