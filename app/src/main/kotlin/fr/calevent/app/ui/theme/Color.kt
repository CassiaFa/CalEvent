package fr.calevent.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Design tokens from design/mockup/DesignSystem.dc.html (slate + amber). */
@Immutable
data class CalEventColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val ink: Color,
    val ink2: Color,
    val brand: Color,
    /** Primary action background. Never used as text on a light background. */
    val accent: Color,
    val onAccent: Color,
    val kept: Color,
    val keptBg: Color,
    val review: Color,
    val reviewBg: Color,
    val ignored: Color,
    val ignoredBg: Color,
    val danger: Color,
    val dangerBg: Color,
    val scrim: Color,
    val isDark: Boolean,
)

val LightCalEventColors = CalEventColors(
    bg = Color(0xFFF3F5F7),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFE9EDF1),
    line = Color(0xFFD3DAE1),
    ink = Color(0xFF16202A),
    ink2 = Color(0xFF4A5A6A),
    brand = Color(0xFF2E3E4E),
    accent = Color(0xFFF0A202),
    onAccent = Color(0xFF1C2733),
    kept = Color(0xFF1D5CA3),
    keptBg = Color(0xFFE2ECF8),
    review = Color(0xFFA3400B),
    reviewBg = Color(0xFFFCEBDF),
    ignored = Color(0xFF5B6773),
    ignoredBg = Color(0xFFECEFF2),
    danger = Color(0xFFB42318),
    dangerBg = Color(0xFFFDECEA),
    scrim = Color(0x6B0E141B),
    isDark = false,
)

val DarkCalEventColors = CalEventColors(
    bg = Color(0xFF0F151B),
    surface = Color(0xFF18212A),
    surface2 = Color(0xFF212C37),
    line = Color(0xFF2D3A47),
    ink = Color(0xFFEDF1F5),
    ink2 = Color(0xFFA6B3C0),
    brand = Color(0xFF51677D),
    accent = Color(0xFFF3AD1C),
    onAccent = Color(0xFF18212A),
    kept = Color(0xFF86B8EF),
    keptBg = Color(0xFF17304C),
    review = Color(0xFFFFA06B),
    reviewBg = Color(0xFF3B2316),
    ignored = Color(0xFF9CA8B4),
    ignoredBg = Color(0xFF222B34),
    danger = Color(0xFFFF8F80),
    dangerBg = Color(0xFF3C1E1B),
    scrim = Color(0x9E000000),
    isDark = true,
)

val LocalCalEventColors = staticCompositionLocalOf { LightCalEventColors }
