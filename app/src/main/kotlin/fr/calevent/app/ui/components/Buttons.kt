package fr.calevent.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.calevent.app.ui.theme.CalEventTheme

private enum class ButtonKind { Primary, Secondary, Tertiary }

@Composable
private fun CalEventButton(
    kind: ButtonKind,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: ImageVector?,
    enabled: Boolean,
    minHeight: Int,
    fontSize: Int,
) {
    val c = CalEventTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val container: Color
    val content: Color
    when {
        !enabled -> { container = c.surface2; content = c.ink2 }
        kind == ButtonKind.Primary -> { container = c.accent; content = c.onAccent }
        kind == ButtonKind.Secondary -> { container = c.surface; content = c.ink }
        else -> { container = Color.Transparent; content = c.ink }
    }
    val weight = if (kind == ButtonKind.Primary && enabled) FontWeight.Bold else FontWeight.SemiBold
    Row(
        modifier = modifier
            .heightIn(min = minHeight.dp)
            .clip(shape)
            .background(container, shape)
            .then(if (kind == ButtonKind.Secondary && enabled) Modifier.border(1.dp, c.line, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Text(
            text,
            style = CalEventTheme.type.bodyStrong.copy(fontWeight = weight, fontSize = fontSize.sp),
            color = content,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Main action of a screen (amber). Min height 52 dp, or 60 dp when [tall] (home / capture).
 * Wraps its content: pass `Modifier.fillMaxWidth()` in a bottom bar.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tall: Boolean = false,
) = CalEventButton(ButtonKind.Primary, text, onClick, modifier, icon, enabled, if (tall) 60 else 52, if (tall) 17 else 16)

/** Secondary action: surface with a thin outline. [compact] gives the 44 dp variant used inside banners. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
) = CalEventButton(ButtonKind.Secondary, text, onClick, modifier, icon, enabled, if (compact) 44 else 52, if (compact) 15 else 16)

/** Low-emphasis action: transparent background. */
@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) = CalEventButton(ButtonKind.Tertiary, text, onClick, modifier, icon, enabled, 52, 16)

/**
 * 44 dp icon-only button. [contentDescription] is required: it is the button's only label.
 * [tint] defaults to the main ink colour; pass a theme token if needed.
 */
@Composable
fun IconActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
) {
    val c = CalEventTheme.colors
    val color = if (!enabled) c.ink2 else if (tint == Color.Unspecified) c.ink else tint
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = color, modifier = Modifier.size(22.dp))
    }
}
