package fr.calevent.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CalEventTheme { ThemeCheckScreen() } }
    }
}

/** Temporary screen proving the theme; replaced by the real home screen (milestone 2). */
@Composable
private fun ThemeCheckScreen() {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    Column(
        modifier = Modifier.fillMaxSize().background(c.bg).safeDrawingPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.offline_badge), style = t.chip, color = c.ink)
        Text(stringResource(R.string.home_title), style = t.title, color = c.ink)
        Text(stringResource(R.string.home_subtitle), style = t.secondary, color = c.ink2)
        Column(Modifier.weight(1f)) {}
        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
        ) { Text(stringResource(R.string.home_take_photo), style = t.bodyStrong) }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PreviewLight() = CalEventTheme(ThemeMode.LIGHT) { ThemeCheckScreen() }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PreviewDark() = CalEventTheme(ThemeMode.DARK) { ThemeCheckScreen() }
