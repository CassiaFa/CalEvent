package fr.calevent.app.ui.nav

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.stringResource
import fr.calevent.app.R
import fr.calevent.app.ui.theme.CalEventTheme

/** Closes the whole capture flow straight to Home. Null outside flow screens (no close button shown). */
val LocalFlowClose = compositionLocalOf<(() -> Unit)?> { null }

/** Confirmation shown when closing the flow would drop detected slots that were not sent. */
@Composable
fun AbandonDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = CalEventTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(stringResource(R.string.abandon_title), style = CalEventTheme.type.section, color = c.ink) },
        text = { Text(stringResource(R.string.abandon_message), style = CalEventTheme.type.body, color = c.ink2) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.abandon_confirm), color = c.danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_continue), color = c.ink) }
        },
    )
}
