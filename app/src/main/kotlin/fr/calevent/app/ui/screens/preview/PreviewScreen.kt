package fr.calevent.app.ui.screens.preview

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import fr.calevent.core.crop.CropDrag
import fr.calevent.core.crop.CropHandle
import fr.calevent.core.crop.NormalizedRect
import kotlin.math.max
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
import fr.calevent.app.capture.AutoCrop
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextDecoration
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
private val LoupeDot = Color(0xFF000000)

// Crop frame geometry: handles are drawn 22-24 dp wide inside a 48 dp touch target; the photo is inset by half of
// it so handles on its border stay reachable; the frame never gets smaller than two touch targets.
private val TouchTarget = 48.dp
private val HandleInset = 24.dp
private val MinFrame = 96.dp
private val LoupeSize = 96.dp

/**
 * Photo preview before analysis: shows the real photo from [CapturedPhotoStore], rotate in quarter turns,
 * crop with a draggable frame (corners, edges, whole frame, magnifier), retake (deletes the photo) or analyse.
 * Without a photo in the store (e.g. previews) the fake planning is shown.
 */
@Composable
fun PreviewScreen(viewModel: ScanViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val c = CalEventTheme.colors
    val t = CalEventTheme.type
    val photo by CapturedPhotoStore.photo.collectAsState()
    var cropping by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val uri = photo?.uri
    val turns = photo?.quarterTurns ?: 0
    // Automatic pre-crop: OCR on a reduced bitmap, applied only if the user has not set a crop meanwhile.
    var searching by remember { mutableStateOf(false) }
    var autoApplied by rememberSaveable(uri.toString(), turns) { mutableStateOf(false) }
    LaunchedEffect(uri, turns) {
        if (uri == null || CapturedPhotoStore.isAutoCropDone(uri, turns)) return@LaunchedEffect
        val snapshot = CapturedPhotoStore.photo.value?.takeIf { it.uri == uri && it.quarterTurns == turns } ?: return@LaunchedEffect
        searching = true
        val suggestion = try { AutoCrop.suggest(context, snapshot) } finally { searching = false }
        CapturedPhotoStore.markAutoCropDone(uri, turns)
        val now = CapturedPhotoStore.photo.value
        if (suggestion != null && now?.uri == uri && now.quarterTurns == turns && now.crop == null) {
            CapturedPhotoStore.setCrop(suggestion)
            cropping = true
            autoApplied = true
        }
    }
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
        val showFullAction = autoApplied && photo?.crop != null
        if (searching || showFullAction) {
            Row(
                Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (searching) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = c.ink2, strokeWidth = 2.dp)
                        Text(stringResource(R.string.preview_autocrop_searching), style = t.secondary, color = c.ink2)
                    }
                } else {
                    Text(stringResource(R.string.preview_autocrop_applied), style = t.secondary, color = c.ink2)
                    val fullCd = stringResource(R.string.preview_autocrop_full_cd)
                    Box(
                        Modifier
                            .height(44.dp)
                            .clickable(role = Role.Button) {
                                CapturedPhotoStore.setCrop(null)
                                cropping = false
                                autoApplied = false
                            }
                            .padding(horizontal = 12.dp)
                            .semantics { contentDescription = fullCd },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.preview_autocrop_full), style = t.bodyStrong, color = c.ink, textDecoration = TextDecoration.Underline)
                    }
                }
            }
        }
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
            // The photo is inset so that handles sitting on its border stay entirely inside the touchable area.
            val availW = (maxWidth - HandleInset * 2).coerceAtLeast(1.dp)
            val availH = (maxHeight - HandleInset * 2).coerceAtLeast(1.dp)
            val widthLimited = availW / availH <= ratio
            val photoW = if (widthLimited) availW else availH * ratio
            val photoH = if (widthLimited) availW / ratio else availH
            val image = remember(bmp) { bmp.asImageBitmap() }
            Box(Modifier.size(photoW + HandleInset * 2, photoH + HandleInset * 2)) {
                Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize().padding(HandleInset))
                if (cropping) CropOverlay(photo?.crop ?: CropRect.Full, image, { CapturedPhotoStore.setCrop(it) }, c.accent, Modifier.fillMaxSize())
                else photo?.crop?.let { CropOverlay(it, image, {}, c.accent, Modifier.fillMaxSize(), interactive = false) }
            }
        }
    }
}

private fun CropRect.toNormalized() = NormalizedRect(left, top, right, bottom)
private fun NormalizedRect.toCropRect() = CropRect(left, top, right, bottom)

/**
 * Crop frame (normalised in the photo area it covers) with a veil outside. Draggable by its four corners, by the
 * middle of its four edges (visible handles 22-24 dp, touch area 48 dp) and, from inside, as a whole. While a corner
 * or an edge is dragged, a magnifier shows the photo under the finger at x2. The overlay is larger than the photo
 * by [HandleInset] on every side so that handles on the photo border can be grabbed entirely; one gesture
 * detector on the whole overlay does the hit test, so handles moving under the finger cannot disturb the drag.
 * The logic (hit test, clamping, minimum size) is in [CropDrag].
 */
@Composable
private fun CropOverlay(
    crop: CropRect,
    image: ImageBitmap,
    onCrop: (CropRect) -> Unit,
    handle: Color,
    modifier: Modifier,
    interactive: Boolean = true,
) {
    val density = LocalDensity.current
    val insetPx = with(density) { HandleInset.toPx() }
    val touchRadiusPx = with(density) { (TouchTarget / 2).toPx() }
    val minSidePx = with(density) { MinFrame.toPx() }
    val current by rememberUpdatedState(crop)
    val currentOnCrop by rememberUpdatedState(onCrop)
    var active by remember { mutableStateOf<CropHandle?>(null) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier
            .onSizeChanged { overlaySize = it }
            .drawBehind {
                val ox = insetPx
                val oy = insetPx
                val pw = size.width - 2 * insetPx
                val ph = size.height - 2 * insetPx
                val l = ox + current.left * pw
                val t = oy + current.top * ph
                val r = ox + current.right * pw
                val b = oy + current.bottom * ph
                drawRect(CropVeil, Offset(ox, oy), Size(pw, t - oy))
                drawRect(CropVeil, Offset(ox, b), Size(pw, oy + ph - b))
                drawRect(CropVeil, Offset(ox, t), Size(l - ox, b - t))
                drawRect(CropVeil, Offset(r, t), Size(ox + pw - r, b - t))
                drawRect(CropOutline, Offset(l, t), Size(r - l, b - t), style = Stroke(1.5.dp.toPx()))
                if (!interactive) return@drawBehind

                val ring = Stroke(1.5.dp.toPx())
                fun grow(h: CropHandle) = if (active == h) 1.25f else 1f
                // Corners: 22 dp discs.
                for ((h, p) in listOf(
                    CropHandle.TopLeft to Offset(l, t), CropHandle.TopRight to Offset(r, t),
                    CropHandle.BottomLeft to Offset(l, b), CropHandle.BottomRight to Offset(r, b),
                )) {
                    val radius = 11.dp.toPx() * grow(h)
                    drawCircle(handle, radius, p)
                    drawCircle(CropOutline, radius, p, style = ring)
                }
                // Edges: 24 x 8 dp pills at the middle of each side.
                val long = 24.dp.toPx()
                val short = 8.dp.toPx()
                val corner = CornerRadius(short / 2)
                for ((h, p) in listOf(
                    CropHandle.Top to Offset((l + r) / 2, t), CropHandle.Bottom to Offset((l + r) / 2, b),
                    CropHandle.Left to Offset(l, (t + b) / 2), CropHandle.Right to Offset(r, (t + b) / 2),
                )) {
                    val horizontal = h == CropHandle.Top || h == CropHandle.Bottom
                    val s = grow(h)
                    val size2 = if (horizontal) Size(long * s, short * s) else Size(short * s, long * s)
                    val topLeft = Offset(p.x - size2.width / 2, p.y - size2.height / 2)
                    drawRoundRect(handle, topLeft, size2, corner)
                    drawRoundRect(CropOutline, topLeft, size2, corner, style = ring)
                }
            }
            .then(
                if (!interactive) Modifier
                else Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pw = size.width - 2 * insetPx
                        val ph = size.height - 2 * insetPx
                        if (pw <= 0f || ph <= 0f) return@awaitEachGesture
                        var rect = current.toNormalized()
                        val grabbed = CropDrag.hitTest(rect, down.position.x - insetPx, down.position.y - insetPx, pw, ph, touchRadiusPx)
                            ?: return@awaitEachGesture
                        down.consume()
                        active = grabbed
                        try {
                            val minW = CropDrag.minimumSize(pw, minSidePx)
                            val minH = CropDrag.minimumSize(ph, minSidePx)
                            drag(down.id) { change ->
                                val delta = change.positionChange()
                                change.consume()
                                rect = CropDrag.drag(rect, grabbed, delta.x / pw, delta.y / ph, minW, minH)
                                currentOnCrop(rect.toCropRect())
                            }
                        } finally {
                            active = null
                        }
                    }
                },
            ),
    ) {
        val grabbed = active
        if (interactive && grabbed != null && grabbed != CropHandle.Move && overlaySize.width > 0) {
            val (fx, fy) = CropDrag.focusPoint(current.toNormalized(), grabbed)
            Loupe(image, fx, fy, overlaySize.width.toFloat(), overlaySize.height.toFloat(), insetPx)
        }
    }
}

/**
 * Round magnifier (96 dp, x2) showing the photo around ([fx], [fy]) (0..1 of the photo area). It sits above the
 * finger, or below when there is no room above, and stays inside the overlay of size [overlayW] x [overlayH] px.
 */
@Composable
private fun Loupe(image: ImageBitmap, fx: Float, fy: Float, overlayW: Float, overlayH: Float, insetPx: Float) {
    val density = LocalDensity.current
    val loupePx = with(density) { LoupeSize.toPx() }
    val gapPx = with(density) { 44.dp.toPx() }
    val photoW = overlayW - 2 * insetPx
    val photoH = overlayH - 2 * insetPx
    val px = insetPx + fx * photoW
    val py = insetPx + fy * photoH
    val x = (px - loupePx / 2).coerceIn(0f, max(0f, overlayW - loupePx))
    val above = py - gapPx - loupePx
    val y = (if (above >= 0f) above else py + gapPx).coerceIn(0f, max(0f, overlayH - loupePx))
    Box(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(LoupeSize)
            .clip(CircleShape)
            .background(PhotoBackdrop)
            .border(2.dp, CropOutline, CircleShape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // The whole photo drawn at twice its on-screen size, shifted so that the focus is at the centre.
            drawImage(
                image,
                dstOffset = IntOffset((size.width / 2 - 2 * fx * photoW).roundToInt(), (size.height / 2 - 2 * fy * photoH).roundToInt()),
                dstSize = IntSize((2 * photoW).roundToInt(), (2 * photoH).roundToInt()),
            )
            val centre = Offset(size.width / 2, size.height / 2)
            drawCircle(CropOutline, 7.dp.toPx(), centre, style = Stroke(1.5.dp.toPx()))
            drawCircle(LoupeDot, 1.5.dp.toPx(), centre)
        }
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
