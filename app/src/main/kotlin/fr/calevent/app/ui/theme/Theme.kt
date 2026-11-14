package fr.calevent.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** User theme preference (Réglages › Thème). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

object CalEventTheme {
    val colors: CalEventColors
        @Composable @ReadOnlyComposable get() = LocalCalEventColors.current
    val type: CalEventTypography
        @Composable @ReadOnlyComposable get() = LocalCalEventTypography.current
}

@Composable
fun CalEventTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val c = if (dark) DarkCalEventColors else LightCalEventColors

    // Material components (sheets, switches, text fields) pick up the same tokens.
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        secondary = c.kept,
        onSecondary = c.surface,
        background = c.bg,
        onBackground = c.ink,
        surface = c.surface,
        onSurface = c.ink,
        surfaceVariant = c.surface2,
        onSurfaceVariant = c.ink2,
        outline = c.line,
        outlineVariant = c.line,
        error = c.danger,
        errorContainer = c.dangerBg,
        scrim = c.scrim,
    )

    CompositionLocalProvider(LocalCalEventColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
