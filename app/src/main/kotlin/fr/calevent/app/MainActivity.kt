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
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import fr.calevent.app.capture.PhotoStorage
import fr.calevent.app.settings.Settings
import fr.calevent.app.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val SPLASH_MS = 600L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash (swaps back to the normal theme); must come before super.onCreate.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Cold start only: a recreation (rotation, theme change) comes with a saved state.
        val coldStart = savedInstanceState == null
        val repository = SettingsRepository.get(this)
        // Photo retention: on each start, delete stored captures older than the chosen duration.
        lifecycleScope.launch {
            runCatching { PhotoStorage.purgeExpired(applicationContext, repository.settings.first().photoRetention) }
        }
        setContent {
            val settings by repository.settings.collectAsState(initial = Settings())
            CalEventTheme(settings.theme) {
                var showSplash by remember { mutableStateOf(coldStart) }
                LaunchedEffect(Unit) {
                    if (showSplash) {
                        delay(SPLASH_MS)
                        showSplash = false
                    }
                }
                if (showSplash) SplashScreen() else AppNav(settings, repository)
            }
        }
    }
}
