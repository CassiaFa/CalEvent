package fr.calevent.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme

/** Tone of a [Banner]: information (blue), something to check (orange), error (red). */
enum class BannerKind { Info, Review, Error }

/**
 * Full-width message banner: icon + text, and optional [actions] on a row below
 * (put [SecondaryButton] with `compact = true` there). [icon] overrides the default of the kind.
 */
@Composable
fun Banner(
    kind: BannerKind,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = CalEventTheme.colors
    val (bg, fg, defaultIcon) = when (kind) {
        BannerKind.Info -> Triple(c.keptBg, c.kept, CalEventIcons.Info)
        BannerKind.Review -> Triple(c.reviewBg, c.review, CalEventIcons.Warning)
        BannerKind.Error -> Triple(c.dangerBg, c.danger, CalEventIcons.Error)
    }
    val weight = if (actions != null) FontWeight.SemiBold else FontWeight.Medium
    Column(
        modifier = modifier.fillMaxWidth().background(bg, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = if (actions != null) Alignment.Top else Alignment.CenterVertically,
        ) {
            Icon(icon ?: defaultIcon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Text(text, style = CalEventTheme.type.body.copy(fontWeight = weight), color = c.ink, modifier = Modifier.weight(1f))
        }
        if (actions != null) {
            Row(Modifier.padding(start = 32.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}
