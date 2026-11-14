package fr.calevent.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.calevent.app.R
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.core.SlotStatus

/**
 * Status pill: colour + icon + label, never colour alone.
 * [text] overrides the default label ("Retenu" / "À revoir" / "Ignoré"), e.g. "3 à revoir".
 * [containerColor] overrides the background (e.g. surface, on top of a tinted card);
 * [compact] gives the 26 dp variant used in list rows (28 dp otherwise).
 */
@Composable
fun StatusChip(
    status: SlotStatus,
    text: String? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    compact: Boolean = false,
) {
    val c = CalEventTheme.colors
    val (bg, fg, icon) = when (status) {
        SlotStatus.KEPT -> Triple(c.keptBg, c.kept, CalEventIcons.Check)
        SlotStatus.TO_REVIEW -> Triple(c.reviewBg, c.review, CalEventIcons.Warning)
        SlotStatus.IGNORED -> Triple(c.ignoredBg, c.ignored, CalEventIcons.EyeOff)
    }
    val label = text ?: stringResource(
        when (status) {
            SlotStatus.KEPT -> R.string.status_kept
            SlotStatus.TO_REVIEW -> R.string.status_review
            SlotStatus.IGNORED -> R.string.status_ignored
        },
    )
    Row(
        modifier = modifier
            .height(if (compact) 26.dp else 28.dp)
            .background(if (containerColor == Color.Unspecified) bg else containerColor, CircleShape)
            .then(if (status == SlotStatus.IGNORED) Modifier.border(1.dp, c.line, CircleShape) else Modifier)
            .padding(start = 7.dp, end = if (compact) 9.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(label, style = CalEventTheme.type.chip, color = fg, maxLines = 1)
    }
}

/** Reassurance pill "Hors ligne · rien n'est envoyé". */
@Composable
fun OfflineBadge(modifier: Modifier = Modifier) {
    val c = CalEventTheme.colors
    Row(
        modifier = modifier
            .height(30.dp)
            .background(c.surface2, CircleShape)
            .padding(start = 9.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(CalEventIcons.ShieldOffline, contentDescription = null, tint = c.ink, modifier = Modifier.size(16.dp))
        Text(stringResource(R.string.offline_badge), style = CalEventTheme.type.chip, color = c.ink, maxLines = 1)
    }
}
