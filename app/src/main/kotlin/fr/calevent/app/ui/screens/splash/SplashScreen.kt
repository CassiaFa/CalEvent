package fr.calevent.app.ui.screens.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.calevent.app.BuildConfig
import fr.calevent.app.R
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode

/** Short in-app splash shown on cold start only: logo, name, version. */
@Composable
fun SplashScreen(version: String = BuildConfig.VERSION_NAME) {
    val c = CalEventTheme.colors
    val cd = stringResource(R.string.splash_cd)
    Box(Modifier.fillMaxSize().background(c.bg).semantics { contentDescription = cd }) {
        Column(
            Modifier.align(Alignment.Center).padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(112.dp))
            Text(
                stringResource(R.string.app_name),
                style = CalEventTheme.type.title.copy(fontSize = 30.sp),
                color = c.ink,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            stringResource(R.string.splash_version, version),
            style = CalEventTheme.type.secondary.copy(fontSize = 13.sp),
            color = c.ink2,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 42.dp),
        )
    }
}

@Preview(name = "Splash light", widthDp = 390, heightDp = 844)
@Composable
private fun SplashLightPreview() = CalEventTheme(ThemeMode.LIGHT) { SplashScreen("0.9.0") }

@Preview(name = "Splash dark", widthDp = 390, heightDp = 844)
@Composable
private fun SplashDarkPreview() = CalEventTheme(ThemeMode.DARK) { SplashScreen("0.9.0") }
