package fr.calevent.app.ui.screens.results

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

/** Slot review list (stub). */
@Composable
fun ResultsScreen(viewModel: ScanViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    ScreenScaffold(
        title = stringResource(R.string.screen_results),
        onBack = onBack,
        bottomBar = {
            BottomActionBar {
                PrimaryButton(stringResource(R.string.action_continue), onClick = onNext, modifier = Modifier.fillMaxWidth())
            }
        },
    ) {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.screen_results), style = CalEventTheme.type.title, color = CalEventTheme.colors.ink)
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ResultsScreenLightPreview() = CalEventTheme(ThemeMode.LIGHT) { ResultsScreen(ScanViewModel(), {}, {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ResultsScreenDarkPreview() = CalEventTheme(ThemeMode.DARK) { ResultsScreen(ScanViewModel(), {}, {}) }
