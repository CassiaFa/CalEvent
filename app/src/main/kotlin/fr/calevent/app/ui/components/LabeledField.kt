package fr.calevent.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.calevent.app.ui.theme.CalEventTheme

/**
 * Text field with its label above the value, on a surface2 background (min height 56 dp).
 * Digits are tabular. A 2 dp blue outline shows the focus.
 */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    val shape = RoundedCornerShape(14.dp)
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(c.surface2, shape)
            .then(if (focused) Modifier.border(2.dp, c.kept, shape) else Modifier)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        Text(label, style = t.chip.copy(fontSize = 12.sp), color = c.ink2)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = source,
            textStyle = t.bodyStrong.copy(color = c.ink, fontFeatureSettings = "tnum"),
            cursorBrush = SolidColor(c.ink),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
