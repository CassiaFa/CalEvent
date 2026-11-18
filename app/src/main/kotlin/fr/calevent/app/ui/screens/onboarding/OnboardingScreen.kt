package fr.calevent.app.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.calevent.app.R
import fr.calevent.app.ui.components.PrimaryButton
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode

private const val PAGES = 3

/**
 * First-launch presentation (3 screens). No permission is requested here, whatever the page.
 * [onFinish] runs on "Passer" and on "Commencer".
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    var page by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = page > 0) { page-- }
    val last = page == PAGES - 1
    val pageCd = stringResource(R.string.onb_page_cd, page + 1, PAGES)
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (!last) {
                    Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onFinish).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.onb_skip), style = t.bodyStrong.copy(fontSize = 15.sp), color = c.ink2)
                    }
                }
            }
            Box(Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(360.dp)) {
                when (page) {
                    0 -> HowItWorksVisual()
                    1 -> OfflineVisual()
                    else -> PermissionsVisual()
                }
            }
            val (title, body) = when (page) {
                0 -> R.string.onb_1_title to R.string.onb_1_body
                1 -> R.string.onb_2_title to R.string.onb_2_body
                else -> R.string.onb_3_title to R.string.onb_3_body
            }
            Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(title), style = t.title.copy(fontSize = 26.sp), color = c.ink, modifier = Modifier.semantics { heading() })
                Text(stringResource(body), style = t.body, color = c.ink2)
            }
            Box(Modifier.weight(1f))
            Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(
                    Modifier.fillMaxWidth().semantics { contentDescription = pageCd },
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    repeat(PAGES) { i ->
                        Box(Modifier.height(8.dp).width(if (i == page) 22.dp else 8.dp).background(if (i == page) c.ink else c.line, RoundedCornerShape(4.dp)))
                    }
                }
                PrimaryButton(
                    text = stringResource(if (last) R.string.onb_start else R.string.onb_continue),
                    onClick = { if (last) onFinish() else page++ },
                    tall = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Page 1: a sheet of paper, an arrow, and the detected slots. */
@Composable
private fun HowItWorksVisual() {
    val c = CalEventTheme.colors
    Box(
        Modifier.fillMaxSize().background(c.surface, RoundedCornerShape(28.dp)).border(1.dp, c.line, RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.rotate(-6f).size(width = 112.dp, height = 150.dp).background(c.surface2, RoundedCornerShape(4.dp)).border(1.dp, c.line, RoundedCornerShape(4.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.height(7.dp).fillMaxWidth(0.7f).background(c.ink2, RoundedCornerShape(2.dp)))
                repeat(5) { Box(Modifier.height(1.dp).fillMaxWidth().background(c.line)) }
            }
            Icon(CalEventIcons.ArrowRight, contentDescription = null, tint = c.ink2, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { i -> SlotMock(highlighted = i == 2) }
            }
        }
    }
}

@Composable
private fun SlotMock(highlighted: Boolean) {
    val c = CalEventTheme.colors
    val fg = if (highlighted) c.onAccent else c.kept
    Row(
        Modifier.fillMaxWidth().height(40.dp).then(if (highlighted) Modifier.rotate(-4f) else Modifier)
            .background(if (highlighted) c.accent else c.keptBg, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(CalEventIcons.CheckBold, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Box(Modifier.height(6.dp).weight(1f).background(fg.copy(alpha = 0.5f), RoundedCornerShape(3.dp)))
    }
}

/** Page 2: shield, with a crossed-out wifi badge. */
@Composable
private fun OfflineVisual() {
    val c = CalEventTheme.colors
    Box(
        Modifier.fillMaxSize().background(c.surface, RoundedCornerShape(28.dp)).border(1.dp, c.line, RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(150.dp).background(c.keptBg, CircleShape), contentAlignment = Alignment.Center) {
            Icon(CalEventIcons.ShieldOffline, contentDescription = null, tint = c.kept, modifier = Modifier.size(72.dp))
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 70.dp, bottom = 92.dp).size(52.dp)
                .background(c.surface, CircleShape).border(1.dp, c.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(CalEventIcons.WifiOff, contentDescription = null, tint = c.ink2, modifier = Modifier.size(24.dp))
        }
    }
}

/** Page 3: what will be asked, and when. Nothing is asked now. */
@Composable
private fun PermissionsVisual() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PermissionRow(CalEventIcons.Camera, R.string.onb_perm_camera, R.string.onb_perm_camera_note, framed = true)
        PermissionRow(CalEventIcons.Calendar, R.string.onb_perm_calendar, R.string.onb_perm_calendar_note, framed = true)
        PermissionRow(CalEventIcons.Gallery, null, R.string.onb_perm_gallery_note, framed = false)
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, titleRes: Int?, noteRes: Int, framed: Boolean) {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().then(if (framed) Modifier.background(c.surface, shape).border(1.dp, c.line, shape) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).then(if (framed) Modifier.background(c.surface2, RoundedCornerShape(12.dp)) else Modifier.border(1.dp, c.line, RoundedCornerShape(12.dp))),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(22.dp)) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (titleRes != null) Text(stringResource(titleRes), style = t.bodyStrong, color = c.ink)
            Text(stringResource(noteRes), style = t.secondary, color = c.ink2)
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingLightPreview() = CalEventTheme(ThemeMode.LIGHT) { OnboardingScreen({}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingDarkPreview() = CalEventTheme(ThemeMode.DARK) { OnboardingScreen({}) }
