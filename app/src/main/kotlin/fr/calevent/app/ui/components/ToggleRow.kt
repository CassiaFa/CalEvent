package fr.calevent.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.calevent.app.ui.theme.CalEventTheme

/** Label + switch on one row (min 48 dp); the whole row toggles. */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = CalEventTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = CalEventTheme.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.kept,
                checkedThumbColor = c.surface,
                checkedBorderColor = c.kept,
                uncheckedTrackColor = c.surface2,
                uncheckedThumbColor = c.ink2,
                uncheckedBorderColor = c.line,
                disabledCheckedTrackColor = c.surface2,
                disabledCheckedThumbColor = c.ink2,
                disabledCheckedBorderColor = c.line,
                disabledUncheckedTrackColor = c.surface2,
                disabledUncheckedThumbColor = c.ink2,
                disabledUncheckedBorderColor = c.line,
            ),
        )
    }
}
