package fr.calevent.app.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.calevent.app.R
import fr.calevent.app.ui.ScanViewModel
import fr.calevent.app.ui.components.ScreenScaffold
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode

/** Full list of the last sends ("Tout voir"). A tap opens the summary sheet. */
@Composable
fun HistoryScreen(viewModel: ScanViewModel, onBack: () -> Unit) {
    val entries by viewModel.historyEntries.collectAsState()
    var openId by remember { mutableStateOf<String?>(null) }
    ScreenScaffold(title = stringResource(R.string.history_title), onBack = onBack) {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (entries.isEmpty()) {
                Text(stringResource(R.string.history_empty), style = CalEventTheme.type.body, color = CalEventTheme.colors.ink2)
            }
            entries.forEach { e -> HistoryCard(e, onClick = { openId = e.id }) }
        }
    }
    entries.firstOrNull { it.id == openId }?.let { HistoryDetailSheet(it, onDismiss = { openId = null }) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HistoryLightPreview() = CalEventTheme(ThemeMode.LIGHT) { HistoryScreen(remember { ScanViewModel() }, {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HistoryDarkPreview() = CalEventTheme(ThemeMode.DARK) { HistoryScreen(remember { ScanViewModel() }, {}) }
