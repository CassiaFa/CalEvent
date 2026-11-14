package fr.calevent.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.calevent.app.R

// Fonts are bundled in res/font (no downloadable fonts: the app has no network access).
private fun dmSans(weight: FontWeight) = Font(
    resId = R.font.dm_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val DmSans = FontFamily(
    dmSans(FontWeight.Normal),
    dmSans(FontWeight.Medium),
    dmSans(FontWeight.SemiBold),
    dmSans(FontWeight.Bold),
)

val DmMono = FontFamily(Font(R.font.dm_mono_medium, FontWeight.Medium))

private const val TABULAR = "tnum"

@Immutable
data class CalEventTypography(
    val title: TextStyle = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.02).em),
    val section: TextStyle = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp),
    val bodyStrong: TextStyle = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    val body: TextStyle = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    /** Secondary text; tabular figures so times align. */
    val secondary: TextStyle = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TABULAR),
    /** Section label, displayed in capitals by the caller. */
    val label: TextStyle = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.06.em),
    val chip: TextStyle = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    /** Schedule codes such as M06, N08, RH. */
    val code: TextStyle = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
)

val LocalCalEventTypography = staticCompositionLocalOf { CalEventTypography() }
