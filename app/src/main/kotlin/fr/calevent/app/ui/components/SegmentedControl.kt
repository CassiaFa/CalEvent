package fr.calevent.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.calevent.app.ui.theme.CalEventTheme

/** One segment: [label] and an optional leading [icon]. */
data class SegmentOption(val label: String, val icon: ImageVector? = null)

/**
 * Segmented selector with two or more equal-width options.
 * Segments are 44 dp high (the mockup has 40; 44 keeps the touch target rule).
 */
@Composable
fun SegmentedControl(
    options: List<SegmentOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = CalEventTheme.colors
    val outer = RoundedCornerShape(14.dp)
    val inner = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier.fillMaxWidth().background(c.surface2, outer).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .then(if (selected) Modifier.shadow(1.dp, inner) else Modifier)
                    .clip(inner)
                    .background(if (selected) c.surface else androidx.compose.ui.graphics.Color.Transparent, inner)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) }),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = if (selected) c.ink else c.ink2
                if (option.icon != null) Icon(option.icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Text(option.label, style = CalEventTheme.type.bodyStrong.copy(fontSize = 15.sp), color = color, maxLines = 1)
            }
        }
    }
}
