package fr.calevent.app.ui.screens.success

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.calevent.app.R
import fr.calevent.app.ui.ScanViewModel
import fr.calevent.app.ui.components.BottomActionBar
import fr.calevent.app.ui.components.PrimaryButton
import fr.calevent.app.ui.components.ScreenScaffold
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode

/** Confirmation after sending; onDone returns to home (stub). */
@Composable
fun SuccessScreen(viewModel: ScanViewModel, onDone: () -> Unit) {
    ScreenScaffold(
        title = stringResource(R.string.screen_success),
        bottomBar = {
            BottomActionBar {
                PrimaryButton(stringResource(R.string.action_continue), onClick = onDone, modifier = Modifier.fillMaxWidth())
            }
        },
    ) {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.screen_success), style = CalEventTheme.type.title, color = CalEventTheme.colors.ink)
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SuccessScreenLightPreview() = CalEventTheme(ThemeMode.LIGHT) { SuccessScreen(ScanViewModel(), {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SuccessScreenDarkPreview() = CalEventTheme(ThemeMode.DARK) { SuccessScreen(ScanViewModel(), {}) }
