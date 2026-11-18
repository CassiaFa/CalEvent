package fr.calevent.app.ui.screens.preview

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import fr.calevent.app.capture.CapturedPhoto
import fr.calevent.app.capture.CapturedPhotoStore
import fr.calevent.app.capture.CropRect
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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

// Photo backdrop and crop overlay: scene content, so fixed in both themes (same idea as the fake paper).
private val PhotoBackdrop = Color(0xFF2B241F)
// Translucent black veil outside the crop area (the photo is dark in both themes, so black reads well).
private val CropVeil = Color(0x59000000)
private val CropOutline = Color(0xB3FFFFFF)

/**
 * Photo preview before analysis: shows the real photo from [CapturedPhotoStore], rotate in quarter turns,
 * crop with four draggable corner handles, retake (deletes the photo) or analyse.
 * Without a photo in the store (e.g. previews) the fake planning is shown.
 */
@Composable
fun PreviewScreen(viewModel: ScanViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    val photo by CapturedPhotoStore.photo.collectAsState()
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
            photo = photo,
            cropping = cropping,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolButton(CalEventIcons.RotateLeft, stringResource(R.string.preview_rotate_left), stringResource(R.string.preview_rotate_left_cd), Modifier.weight(1f)) { CapturedPhotoStore.rotateLeft() }
            ToolButton(CalEventIcons.RotateRight, stringResource(R.string.preview_rotate_right), stringResource(R.string.preview_rotate_right_cd), Modifier.weight(1f)) { CapturedPhotoStore.rotateRight() }
            ToolButton(
                CalEventIcons.Crop,
                stringResource(R.string.preview_crop),
                stringResource(if (cropping) R.string.preview_crop_cd_on else R.string.preview_crop_cd_off),
                Modifier.weight(1f),
                selected = cropping,
            ) { cropping = !cropping }
            ToolButton(CalEventIcons.Camera, stringResource(R.string.preview_retake), stringResource(R.string.preview_retake), Modifier.weight(1f)) {
                CapturedPhotoStore.clear()
                onBack()
            }
        }
    }
}

@Composable
private fun PhotoViewport(photo: CapturedPhoto?, cropping: Boolean, modifier: Modifier) {
    val c = CalEventTheme.colors
    val photoCd = stringResource(R.string.preview_photo_cd)
    val context = LocalContext.current
    // Decode the full (uncropped) photo; the crop is drawn on top and applied later by loadBitmap.
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    val uri = photo?.uri
    val turns = photo?.quarterTurns ?: 0
    LaunchedEffect(uri, turns) {
        if (uri == null) { bitmap = null; return@LaunchedEffect }
        failed = false
        bitmap = runCatching { CapturedPhotoStore.loadBitmap(context, maxSide = 1600, applyCrop = false) }.getOrElse { failed = true; null }
    }
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PhotoBackdrop)
            .semantics { contentDescription = photoCd },
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (uri == null || failed) {
            PlanningPhotoPlaceholder(PlanningPhotoVariant.Weekly, Modifier.size(maxWidth - 56.dp, maxHeight - 52.dp))
        } else if (bmp != null) {
            val ratio = bmp.width.toFloat() / bmp.height
            val fit = if (maxWidth / maxHeight > ratio) Modifier.fillMaxHeight().aspectRatio(ratio) else Modifier.fillMaxWidth().aspectRatio(ratio)
            Box(fit) {
                Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                if (cropping) CropOverlay(photo?.crop ?: CropRect.Full, { CapturedPhotoStore.setCrop(it) }, c.accent, Modifier.fillMaxSize())
                else photo?.crop?.let { CropOverlay(it, {}, c.accent, Modifier.fillMaxSize(), interactive = false) }
            }
        }
    }
}

/**
 * Crop rectangle (normalised in the box it fills) with a veil outside and four draggable corner handles.
 * Each handle has a 44 dp touch target; the rectangle keeps at least 15 % of each side.
 */
@Composable
private fun CropOverlay(crop: CropRect, onCrop: (CropRect) -> Unit, handle: Color, modifier: Modifier, interactive: Boolean = true) {
    val density = LocalDensity.current
    val touch = 44.dp
    BoxWithConstraints(modifier) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val current by rememberUpdatedState(crop)
        Box(
            Modifier.fillMaxSize().drawBehind {
                val l = current.left * size.width; val t = current.top * size.height
                val r = current.right * size.width; val b = current.bottom * size.height
                drawRect(CropVeil, Offset(0f, 0f), Size(size.width, t))
                drawRect(CropVeil, Offset(0f, b), Size(size.width, size.height - b))
                drawRect(CropVeil, Offset(0f, t), Size(l, b - t))
                drawRect(CropVeil, Offset(r, t), Size(size.width - r, b - t))
                drawRect(CropOutline, Offset(l, t), Size(r - l, b - t), style = Stroke(1.5.dp.toPx()))
            },
        )
        if (!interactive) return@BoxWithConstraints
        val min = 0.15f
        fun drag(left: Boolean, top: Boolean, dx: Float, dy: Float) {
            val cr = current
            val nx = dx / wPx; val ny = dy / hPx
            onCrop(
                CropRect(
                    left = if (left) (cr.left + nx).coerceIn(0f, cr.right - min) else cr.left,
                    top = if (top) (cr.top + ny).coerceIn(0f, cr.bottom - min) else cr.top,
                    right = if (!left) (cr.right + nx).coerceIn(cr.left + min, 1f) else cr.right,
                    bottom = if (!top) (cr.bottom + ny).coerceIn(cr.top + min, 1f) else cr.bottom,
                ),
            )
        }
        @Composable
        fun Handle(left: Boolean, top: Boolean) {
            val cx = (if (left) crop.left else crop.right) * wPx
            val cy = (if (top) crop.top else crop.bottom) * hPx
            val half = with(density) { touch.toPx() / 2 }
            Box(
                Modifier
                    .offset { IntOffset((cx - half).roundToInt(), (cy - half).roundToInt()) }
                    .size(touch)
                    .pointerInput(left, top) {
                        detectDragGestures { change, amount -> change.consume(); drag(left, top, amount.x, amount.y) }
                    },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(22.dp).background(handle, CircleShape)) }
        }
        Handle(true, true); Handle(false, true); Handle(true, false); Handle(false, false)
    }
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
