package fr.calevent.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme

/** Capitalised section label above a group. */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(androidx.compose.ui.platform.LocalLocale.current.platformLocale),
        style = CalEventTheme.type.label,
        color = CalEventTheme.colors.ink2,
        modifier = modifier.padding(start = 4.dp, bottom = 6.dp).semantics { heading() },
    )
}

/** Rounded surface card holding rows separated by thin lines. */
@Composable
internal fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = CalEventTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth().background(c.surface, shape).border(1.dp, c.line, shape), content = content)
}

@Composable
internal fun RowDivider() = HorizontalDivider(color = CalEventTheme.colors.line)

/** Navigation row: label, optional [value], optional chevron. Min 52 dp. */
@Composable
internal fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    chevron: Boolean = true,
    icon: ImageVector? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = CalEventTheme.colors
    val text = if (danger) c.danger else c.ink
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = text, modifier = Modifier.size(20.dp))
        Text(label, style = CalEventTheme.type.bodyStrong, color = if (enabled) text else c.ink2, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = CalEventTheme.type.secondary, color = c.ink2)
        if (chevron && onClick != null && enabled) Icon(CalEventIcons.Chevron, contentDescription = null, tint = c.ink2, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun ColorDot(argb: Long, modifier: Modifier = Modifier) {
    Box(modifier.size(14.dp).background(Color(argb), RoundedCornerShape(7.dp)))
}
