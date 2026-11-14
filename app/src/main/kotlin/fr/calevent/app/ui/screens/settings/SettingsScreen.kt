package fr.calevent.app.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

/** Settings (stub); onBack leaves the screen. */
@Composable
fun SettingsScreen(viewModel: ScanViewModel, onBack: () -> Unit) {
    ScreenScaffold(
        title = stringResource(R.string.screen_settings),
        onBack = onBack,
        bottomBar = {
            BottomActionBar {
                PrimaryButton(stringResource(R.string.action_continue), onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        },
    ) {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.screen_settings), style = CalEventTheme.type.title, color = CalEventTheme.colors.ink)
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SettingsScreenLightPreview() = CalEventTheme(ThemeMode.LIGHT) { SettingsScreen(remember { ScanViewModel() }, {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SettingsScreenDarkPreview() = CalEventTheme(ThemeMode.DARK) { SettingsScreen(remember { ScanViewModel() }, {}) }
