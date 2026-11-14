package fr.calevent.app.ui.screens.preview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.calevent.app.R
import fr.calevent.app.ui.ScanViewModel
import fr.calevent.app.ui.components.BottomActionBar
import fr.calevent.app.ui.components.PlanningPhotoPlaceholder
import fr.calevent.app.ui.components.PlanningPhotoVariant
import fr.calevent.app.ui.components.PrimaryButton
import fr.calevent.app.ui.components.ScreenScaffold
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.app.ui.theme.ThemeMode
import kotlin.math.min

// Photo backdrop and crop overlay: scene content, so fixed in both themes (same idea as the fake paper).
private val PhotoBackdrop = Color(0xFF2B241F)
// Translucent black veil outside the crop area (the photo is dark in both themes, so black reads well).
private val CropVeil = Color(0x59000000)
private val CropOutline = Color(0xB3FFFFFF)

/**
 * Photo preview before analysis: rotate in quarter turns, toggle a (fake) crop frame, retake, or analyse.
 * Rotation and crop are local UI state only for now.
 */
@Composable
fun PreviewScreen(viewModel: ScanViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    var quarterTurns by rememberSaveable { mutableIntStateOf(0) }
    var cropping by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(
        title = stringResource(R.string.preview_title),
        onBack = onBack,
        bottomBar = {
            BottomActionBar {
                PrimaryButton(stringResource(R.string.preview_analyze), onClick = onNext, tall = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Icon(CalEventIcons.ShieldOffline, contentDescription = null, tint = c.ink2, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.preview_hint), style = t.secondary, color = c.ink2, textAlign = TextAlign.Center)
                }
            }
        },
    ) {
        PhotoViewport(
            quarterTurns = quarterTurns,
            cropping = cropping,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolButton(CalEventIcons.RotateLeft, stringResource(R.string.preview_rotate_left), stringResource(R.string.preview_rotate_left_cd), Modifier.weight(1f)) { quarterTurns-- }
            ToolButton(CalEventIcons.RotateRight, stringResource(R.string.preview_rotate_right), stringResource(R.string.preview_rotate_right_cd), Modifier.weight(1f)) { quarterTurns++ }
            ToolButton(
                CalEventIcons.Crop,
                stringResource(R.string.preview_crop),
                stringResource(if (cropping) R.string.preview_crop_cd_on else R.string.preview_crop_cd_off),
                Modifier.weight(1f),
                selected = cropping,
            ) { cropping = !cropping }
            ToolButton(CalEventIcons.Camera, stringResource(R.string.preview_retake), stringResource(R.string.preview_retake), Modifier.weight(1f), onClick = onBack)
        }
    }
}

@Composable
private fun PhotoViewport(quarterTurns: Int, cropping: Boolean, modifier: Modifier) {
    val c = CalEventTheme.colors
    val photoCd = stringResource(R.string.preview_photo_cd)
    val rotation by animateFloatAsState(quarterTurns * 90f, label = "rotation")
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PhotoBackdrop)
            .semantics { contentDescription = photoCd },
        contentAlignment = Alignment.Center,
    ) {
        val paperW = maxWidth - 56.dp
        val paperH = maxHeight - 52.dp
        // A quarter turn swaps width and height, so shrink the paper to keep it inside the viewport.
        val odd = Math.floorMod(quarterTurns, 2) == 1
        val fit by animateFloatAsState(
            if (odd) min(maxWidth / paperH, maxHeight / paperW).coerceAtMost(1f) else 1f,
            label = "fit",
        )
        PlanningPhotoPlaceholder(
            PlanningPhotoVariant.Weekly,
            Modifier.size(paperW, paperH).graphicsLayer { rotationZ = rotation - 2f; scaleX = fit; scaleY = fit },
        )
        if (cropping) CropOverlay(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp).fillVeil(), c.accent)
    }
}

// Draws the veil around the crop rectangle via a huge outer stroke-less fill, clipped by the viewport.
private fun Modifier.fillVeil(): Modifier = drawBehind {
    val pad = Offset(20.dp.toPx(), 18.dp.toPx())
    // Four rectangles around the crop area.
    drawRect(CropVeil, Offset(-pad.x, -pad.y), androidx.compose.ui.geometry.Size(size.width + 2 * pad.x, pad.y))
    drawRect(CropVeil, Offset(-pad.x, size.height), androidx.compose.ui.geometry.Size(size.width + 2 * pad.x, pad.y))
    drawRect(CropVeil, Offset(-pad.x, 0f), androidx.compose.ui.geometry.Size(pad.x, size.height))
    drawRect(CropVeil, Offset(size.width, 0f), androidx.compose.ui.geometry.Size(pad.x, size.height))
}

/** Fake crop frame: thin outline and four amber corner handles (decorative until cropping is wired). */
@Composable
private fun CropOverlay(modifier: Modifier, handle: Color) {
    Box(modifier.border(1.5.dp, CropOutline, RoundedCornerShape(4.dp))) {
        Corner(handle, Alignment.TopStart)
        Corner(handle, Alignment.TopEnd)
        Corner(handle, Alignment.BottomStart)
        Corner(handle, Alignment.BottomEnd)
    }
}

@Composable
private fun BoxScope.Corner(color: Color, align: Alignment) {
    Box(Modifier.align(align).size(22.dp).drawBehind {
        val w = 4.dp.toPx()
        val right = align == Alignment.TopEnd || align == Alignment.BottomEnd
        val bottom = align == Alignment.BottomStart || align == Alignment.BottomEnd
        val x = if (right) size.width - w / 2 else w / 2
        val y = if (bottom) size.height - w / 2 else w / 2
        drawLine(color, Offset(x, y), Offset(if (right) 0f else size.width, y), w)
        drawLine(color, Offset(x, y), Offset(x, if (bottom) 0f else size.height), w)
    })
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    onClick: () -> Unit,
) {
    val c = CalEventTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val border = if (selected == true) androidx.compose.foundation.BorderStroke(2.dp, c.accent) else androidx.compose.foundation.BorderStroke(1.dp, c.line)
    Column(
        modifier
            .height(64.dp)
            .clip(shape)
            .background(c.surface, shape)
            .border(border, shape)
            .then(
                if (selected != null) Modifier.toggleable(value = selected, role = Role.Switch, onValueChange = { onClick() })
                else Modifier.clickable(role = Role.Button, onClick = onClick),
            )
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(22.dp))
        Text(label, style = CalEventTheme.type.chip.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 1)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PreviewScreenLightPreview() = CalEventTheme(ThemeMode.LIGHT) { PreviewScreen(remember { ScanViewModel() }, {}, {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PreviewScreenDarkPreview() = CalEventTheme(ThemeMode.DARK) { PreviewScreen(remember { ScanViewModel() }, {}, {}) }
