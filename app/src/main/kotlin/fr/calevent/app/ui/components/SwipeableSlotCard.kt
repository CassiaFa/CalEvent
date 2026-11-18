package fr.calevent.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.calevent.app.R
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.core.Slot

/**
 * [SlotCard] with swipe actions: right = "Ajouter" (keep), left = "Ignorer". The card always snaps back;
 * the action itself is reported through [onKeep] / [onIgnore] and the slot then moves by the usual rules.
 */
@Composable
fun SwipeableSlotCard(
    slot: Slot,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onKeep: () -> Unit,
    onIgnore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = CalEventTheme.colors
    val haptic = LocalHapticFeedback.current
    val keep by rememberUpdatedState(onKeep)
    val ignore by rememberUpdatedState(onIgnore)
    val state = rememberSwipeToDismissBoxState(
        positionalThreshold = { total -> total * 0.4f },
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> keep()
                SwipeToDismissBoxValue.EndToStart -> ignore()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false // never settle on the dismissed position: the card springs back
        },
    )
    LaunchedEffect(state.targetValue) {
        if (state.targetValue != SwipeToDismissBoxValue.Settled) {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    val addLabel = stringResource(R.string.slot_swipe_add)
    val ignoreLabel = stringResource(R.string.slot_swipe_ignore)
    SwipeToDismissBox(
        state = state,
        modifier = modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(addLabel) { keep(); true },
                CustomAccessibilityAction(ignoreLabel) { ignore(); true },
            )
        },
        backgroundContent = {
            when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeBackground(addLabel, CalEventIcons.Check, c.keptBg, c.kept, alignStart = true)
                SwipeToDismissBoxValue.EndToStart -> SwipeBackground(ignoreLabel, CalEventIcons.Close, c.ignoredBg, c.ignored, alignStart = false)
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
    ) {
        SlotCard(slot = slot, onToggle = onToggle, onClick = onClick, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun SwipeBackground(label: String, icon: ImageVector, bg: Color, fg: Color, alignStart: Boolean) {
    Box(
        Modifier.fillMaxSize().background(bg, RoundedCornerShape(14.dp)).padding(horizontal = 20.dp),
        contentAlignment = if (alignStart) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Text(label, style = CalEventTheme.type.bodyStrong, color = fg)
        }
    }
}
