package fr.calevent.app.ui.screens.camera

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.calevent.app.R
import fr.calevent.app.ui.ScanViewModel
import fr.calevent.app.ui.components.PlanningPhotoPlaceholder
import fr.calevent.app.ui.components.PlanningPhotoVariant
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode

// The camera viewfinder is dark in both themes (like any camera app), so these overlay colours
// are fixed on purpose rather than theme tokens: a translucent black "glass" under white content.
private val ViewfinderBg = Color(0xFF0B0F13)
private val Glass = Color(0x9E0C1014)
private val OnGlass = Color.White

/**
 * Camera capture. No live feed yet: [PlanningPhotoPlaceholder] stands in for the viewfinder.
 * The shutter goes straight to [onNext]; close goes to [onBack].
 */
@Composable
fun CameraScreen(viewModel: ScanViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    var flashOn by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(ViewfinderBg)) {
        // Viewfinder stand-in, dimmed like an unlit scene.
        PlanningPhotoPlaceholder(
            PlanningPhotoVariant.Weekly,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(start = 44.dp, end = 44.dp, top = 118.dp).fillMaxWidth().height(420.dp),
        )
        // Guide frame (white corners, 4 dp), drawn around the sheet.
        GuideFrame(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(start = 28.dp, end = 28.dp, top = 92.dp).fillMaxWidth().height(470.dp))

        TopControls(
            flashOn = flashOn,
            onFlash = { flashOn = !flashOn },
            onClose = onBack,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        GlassPill(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 150.dp),
        ) {
            Text(stringResource(R.string.camera_tip), style = CalEventTheme.type.secondary.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = OnGlass)
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = 34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val importCd = stringResource(R.string.camera_import_cd)
            PlanningPhotoPlaceholder(
                PlanningPhotoVariant.Weekly,
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(2.dp, OnGlass.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onNext)
                    .semantics { contentDescription = importCd },
            )
            Shutter(onClick = onNext)
            // Balances the row so the shutter stays centred.
            Box(Modifier.size(52.dp))
        }
    }
}

@Composable
private fun TopControls(flashOn: Boolean, onFlash: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val t = CalEventTheme.type
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        val closeCd = stringResource(R.string.camera_close_cd)
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Glass, CircleShape).clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Icon(CalEventIcons.Close, contentDescription = closeCd, tint = OnGlass, modifier = Modifier.size(22.dp)) }

        GlassPill(Modifier.heightIn(min = 30.dp), vertical = 6.dp) {
            Icon(CalEventIcons.ShieldOffline, contentDescription = null, tint = OnGlass, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.camera_offline), style = t.chip.copy(fontSize = 13.sp), color = OnGlass)
        }

        val flashState = stringResource(if (flashOn) R.string.camera_flash_on else R.string.camera_flash_off)
        val flashCd = stringResource(R.string.camera_flash_toggle_cd, flashState)
        Row(
            Modifier
                .heightIn(min = 44.dp)
                .clip(CircleShape)
                .background(Glass, CircleShape)
                .clickable(role = Role.Button, onClick = onFlash)
                .semantics { contentDescription = flashCd }
                .padding(start = 10.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(CalEventIcons.Flash, contentDescription = null, tint = OnGlass, modifier = Modifier.size(22.dp))
            Text(stringResource(if (flashOn) R.string.camera_flash_on_label else R.string.camera_flash_off_label), style = t.chip.copy(fontSize = 14.sp), color = OnGlass)
        }
    }
}

@Composable
private fun GlassPill(modifier: Modifier = Modifier, vertical: androidx.compose.ui.unit.Dp = 10.dp, content: @Composable () -> Unit) {
    Row(
        modifier.background(Glass, RoundedCornerShape(15.dp)).padding(horizontal = 14.dp, vertical = vertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** White ring with a 64 dp inner disc: the touch target is the full 80 dp. */
@Composable
private fun Shutter(onClick: () -> Unit) {
    val cd = stringResource(R.string.camera_shutter_cd)
    Box(
        Modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(BorderStroke(4.dp, OnGlass), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(64.dp).background(OnGlass, CircleShape))
    }
}

@Composable
private fun GuideFrame(modifier: Modifier) {
    val cd = stringResource(R.string.camera_frame_cd)
    Box(
        modifier
            .semantics { contentDescription = cd }
            .drawBehind {
                val stroke = 4.dp.toPx()
                val len = 44.dp.toPx()
                val r = 18.dp.toPx()
                val w = size.width
                val h = size.height
                val s = Stroke(stroke)
                // Four corner brackets, each an arc plus two straight legs.
                fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                    drawLine(OnGlass, Offset(x, y + dy * r), Offset(x, y + dy * len), stroke)
                    drawLine(OnGlass, Offset(x + dx * r, y), Offset(x + dx * len, y), stroke)
                    val left = if (dx > 0) x else x - 2 * r
                    val top = if (dy > 0) y else y - 2 * r
                    drawArc(OnGlass, startAngle = if (dx > 0 && dy > 0) 180f else if (dx < 0 && dy > 0) 270f else if (dx < 0) 0f else 90f, sweepAngle = 90f, useCenter = false, topLeft = Offset(left, top), size = Size(2 * r, 2 * r), style = s)
                }
                corner(0f, 0f, 1f, 1f)
                corner(w, 0f, -1f, 1f)
                corner(0f, h, 1f, -1f)
                corner(w, h, -1f, -1f)
            },
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CameraScreenLightPreview() = CalEventTheme(ThemeMode.LIGHT) { CameraScreen(remember { ScanViewModel() }, {}, {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CameraScreenDarkPreview() = CalEventTheme(ThemeMode.DARK) { CameraScreen(remember { ScanViewModel() }, {}, {}) }
