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

/** Row under the warnings: shows the chosen person (or asks to choose one); opens the picker. */
@Composable
internal fun PersonRow(selected: String?, onClick: () -> Unit) {
    val c = CalEventTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(c.surface2, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(CalEventIcons.Person, contentDescription = null, tint = c.ink, modifier = Modifier.size(22.dp))
        Text(
            if (selected == null) stringResource(R.string.results_people_choose) else stringResource(R.string.results_people_selected, selected),
            style = CalEventTheme.type.bodyStrong,
            color = c.ink,
            modifier = Modifier.weight(1f),
        )
        Icon(CalEventIcons.Search, contentDescription = null, tint = c.ink2, modifier = Modifier.size(20.dp))
    }
}

/** Shown instead of the slots while nobody is chosen on a team schedule. */
@Composable
internal fun PersonPrompt() {
    val c = CalEventTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.results_people_prompt), style = CalEventTheme.type.bodyStrong, color = c.ink, textAlign = TextAlign.Center)
        Text(stringResource(R.string.results_people_prompt_hint), style = CalEventTheme.type.secondary, color = c.ink2, textAlign = TextAlign.Center)
    }
}

/** Minimal person picker: search field and the list of names; the full team screen comes later. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonSheet(people: List<String>, selected: String?, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    val c = CalEventTheme.colors
    var query by remember { mutableStateOf("") }
    val shown = remember(people, query) {
        val key = query.foldKey()
        if (key.isEmpty()) people else people.filter { it.foldKey().contains(key) }
    }
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
            Text(stringResource(R.string.results_people_sheet_title), style = CalEventTheme.type.section, color = c.ink)
            LabeledField(stringResource(R.string.results_people_search), query, { query = it })
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 420.dp)) {
                if (selected != null) {
                    item { PersonItem(stringResource(R.string.results_people_clear), false) { onSelect(null) } }
                }
                items(shown) { name -> PersonItem(name, name == selected) { onSelect(name) } }
                if (shown.isEmpty()) {
                    item { Text(stringResource(R.string.results_people_none_found), style = CalEventTheme.type.secondary, color = c.ink2, modifier = Modifier.padding(vertical = 12.dp)) }
                }
            }
            Box(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun PersonItem(label: String, checked: Boolean, onClick: () -> Unit) {
    val c = CalEventTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = CalEventTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
        if (checked) Icon(CalEventIcons.Check, contentDescription = null, tint = c.kept, modifier = Modifier.size(22.dp))
    }
}
