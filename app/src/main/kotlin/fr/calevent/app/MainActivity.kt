package fr.calevent.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import fr.calevent.app.ui.nav.AppNav
import fr.calevent.app.ui.screens.splash.SplashScreen
import fr.calevent.app.ui.theme.CalEventTheme
import kotlinx.coroutines.delay

private const val SPLASH_MS = 600L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash (swaps back to the normal theme); must come before super.onCreate.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Cold start only: a recreation (rotation, theme change) comes with a saved state.
        val coldStart = savedInstanceState == null
        setContent {
            CalEventTheme {
                var showSplash by remember { mutableStateOf(coldStart) }
                LaunchedEffect(Unit) {
                    if (showSplash) {
                        delay(SPLASH_MS)
                        showSplash = false
                    }
                }
                if (showSplash) SplashScreen() else AppNav()
            }
        }
    }
}
