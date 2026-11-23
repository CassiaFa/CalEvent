package fr.calevent.app.ui.screens.results

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.calevent.app.R
import fr.calevent.app.ui.components.PlanningPhotoPlaceholder
import fr.calevent.app.ui.components.PlanningPhotoVariant
import fr.calevent.app.ui.components.StatusChip
import fr.calevent.app.ui.components.slotTimeText
import fr.calevent.app.ui.icons.CalEventIcons
import fr.calevent.app.ui.theme.CalEventTheme
import fr.calevent.core.Slot
import fr.calevent.core.SlotStatus
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Colours of the photographed scene (table, paper, handwriting). Like the fake planning drawn by
 * PlanningPhotoPlaceholder, they are scene content, not UI theme, and are the same in light and dark.
 */
internal object ScenePaper {
    val Table = Color(0xFF3A322B)
    val Paper = Color(0xFFF3F0E7)
    val Ink = Color(0xFF2A2A2A)
    val Rule = Color(0xFF9A9488)
}

/** Height of one row of the fake sheet: a 44 dp touch target plus a 4 dp gap. */
private val ROW_PITCH = 48.dp
private val ZONE_HEIGHT = 44.dp
private val PAPER_TOP = 24.dp
private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d", Locale.FRENCH)

/** Fixed layout of a detected zone: depends only on its index, so it never moves. */
private fun zoneOffsetX(index: Int): Dp = (index % 3 * 6).dp
private fun zoneEndInset(index: Int): Dp = (index % 4 * 8).dp

/**
 * Photo tab: the fake planning photo with one rectangle per detected slot laid over it.
 * Each zone carries its status as style (solid / dashed outline), icon and a spoken description.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhotoView(slots: List<Slot>, onOpen: (String) -> Unit, modifier: Modifier = Modifier, photo: Bitmap? = null) {
    if (photo != null) {
        RealPhotoView(photo, slots, onOpen, modifier)
        return
    }
    val ordered = remember(slots) { slots.sortedWith(compareBy<Slot> { it.date }.thenBy { it.start }.thenBy { it.id }) }
    val sheetHeight = PAPER_TOP * 2 + ROW_PITCH * ordered.size.coerceAtLeast(6)
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ScenePaper.Table).padding(horizontal = 14.dp)) {
            Box(Modifier.fillMaxWidth().height(sheetHeight).padding(vertical = 10.dp)) {
                PlanningPhotoPlaceholder(PlanningPhotoVariant.Weekly, Modifier.fillMaxSize())
                ordered.forEachIndexed { i, slot ->
                    Zone(
                        slot = slot,
                        index = i,
                        onClick = { onOpen(slot.id) },
                        modifier = Modifier.offset(x = zoneOffsetX(i), y = PAPER_TOP - 10.dp + ROW_PITCH * i),
                    )
                }
            }
        }
        Text(
            stringResource(R.string.results_photo_hint),
            style = CalEventTheme.type.secondary,
            color = CalEventTheme.colors.ink2,
        )
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusChip(SlotStatus.KEPT)
            StatusChip(SlotStatus.TO_REVIEW)
            StatusChip(SlotStatus.IGNORED)
        }
    }
}

@Composable
private fun Zone(slot: Slot, index: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = CalEventTheme.colors
    val status = slot.status
    val (fg, tint, icon) = when (status) {
        SlotStatus.KEPT -> Triple(c.kept, c.keptBg, CalEventIcons.Check)
        SlotStatus.TO_REVIEW -> Triple(c.review, c.reviewBg, CalEventIcons.Warning)
        SlotStatus.IGNORED -> Triple(c.ignored, c.ignoredBg, CalEventIcons.EyeOff)
    }
    val shape = RoundedCornerShape(6.dp)
    val statusLabel = stringResource(
        when (status) {
            SlotStatus.KEPT -> R.string.status_kept
            SlotStatus.TO_REVIEW -> R.string.status_review
            SlotStatus.IGNORED -> R.string.status_ignored
        },
    )
    val description = stringResource(
        R.string.results_zone_cd,
        statusLabel,
        slot.date.format(DAY_FORMAT) + " " + slotTimeText(slot),
        slot.title,
    )
    val text = slot.sourceText.ifBlank { slot.title }
    Row(
        modifier = modifier
            .padding(end = zoneEndInset(index))
            .fillMaxWidth()
            .height(ZONE_HEIGHT)
            .clip(shape)
            .background(tint.copy(alpha = 0.55f), shape)
            .then(
                if (status == SlotStatus.KEPT) Modifier.border(2.dp, fg, shape)
                else Modifier.dashedBorder(fg, if (status == SlotStatus.TO_REVIEW) 2.5.dp else 2.dp)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(start = 10.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            slot.date.format(DAY_FORMAT),
            style = CalEventTheme.type.chip.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
            color = ScenePaper.Ink,
            maxLines = 1,
        )
        Text(
            text,
            style = CalEventTheme.type.chip.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            color = ScenePaper.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Status badge: icon on a theme surface, so it reads on the paper in both themes.
        Box(Modifier.size(28.dp).background(c.surface, CircleShape).border(1.dp, fg, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        }
    }
}

private fun Modifier.dashedBorder(color: Color, width: Dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(6.dp.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 3, w * 2.5f))),
    )
}

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/** Smallest touch target, also for tiny zones (a cell of a 31-day schedule). */
private val MIN_TOUCH = 44.dp

/**
 * The real photo with each slot's [Slot.region] laid over it. A zone is its status as outline style
 * (solid / dashed), a light tint and a badge icon; its touch area is at least 44 dp.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RealPhotoView(photo: Bitmap, slots: List<Slot>, onOpen: (String) -> Unit, modifier: Modifier) {
    val image = remember(photo) { photo.asImageBitmap() }
    val withZone = slots.filter { it.region != null }
    val photoCd = stringResource(R.string.results_photo_cd)
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ScenePaper.Table)) {
            val w = maxWidth
            val h = w * photo.height / photo.width
            val density = LocalDensity.current
            val viewW = with(density) { w.toPx() }
            val viewH = with(density) { h.toPx() }
            var scale by remember { mutableFloatStateOf(1f) }
            var shift by remember { mutableStateOf(Offset.Zero) }
            // Keeps the zoomed photo covering the viewport (no empty margin at the edges).
            fun clamp(o: Offset, s: Float) = Offset(o.x.coerceIn(viewW * (1 - s), 0f), o.y.coerceIn(viewH * (1 - s), 0f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(h)
                    .pointerInput(viewW, viewH) {
                        detectTapGestures(onDoubleTap = { tap ->
                            if (scale > 1.01f) {
                                scale = 1f
                                shift = Offset.Zero
                            } else {
                                scale = DOUBLE_TAP_SCALE
                                shift = clamp(tap - tap * DOUBLE_TAP_SCALE, DOUBLE_TAP_SCALE)
                            }
                        })
                    }
                    .pointerInput(viewW, viewH) {
                        // Pinch always; a one-finger drag only while zoomed, so the page still scrolls at 1x.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val fingers = event.changes.count { it.pressed }
                                if (fingers > 1 || scale > 1.001f) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    if (zoom != 1f || pan != Offset.Zero) {
                                        val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                        val anchored = if (centroid.isSpecified) centroid - (centroid - shift) / scale * newScale else shift
                                        scale = newScale
                                        shift = clamp(anchored + pan, newScale)
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            ) {
              Box(
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = shift.x
                    translationY = shift.y
                    transformOrigin = TransformOrigin(0f, 0f)
                },
              ) {
                Image(
                    bitmap = image,
                    contentDescription = photoCd,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
                withZone.forEach { slot ->
                    val r = slot.region!!
                    val zoneW = w * (r.right - r.left).toFloat()
                    val zoneH = h * (r.bottom - r.top).toFloat()
                    val touchW = maxOf(zoneW, MIN_TOUCH)
                    val touchH = maxOf(zoneH, MIN_TOUCH)
                    val cx = w * ((r.left + r.right) / 2).toFloat()
                    val cy = h * ((r.top + r.bottom) / 2).toFloat()
                    PhotoZone(
                        slot = slot,
                        zoneWidth = zoneW,
                        zoneHeight = zoneH,
                        onClick = { onOpen(slot.id) },
                        modifier = Modifier
                            .offset(x = (cx - touchW / 2).coerceIn(0.dp, (w - touchW).coerceAtLeast(0.dp)), y = (cy - touchH / 2).coerceIn(0.dp, (h - touchH).coerceAtLeast(0.dp)))
                            .size(touchW, touchH),
                    )
                }
              }
            }
        }
        Text(stringResource(R.string.results_photo_hint), style = CalEventTheme.type.secondary, color = CalEventTheme.colors.ink2)
        if (withZone.size < slots.size) {
            Text(stringResource(R.string.results_photo_no_zone), style = CalEventTheme.type.secondary, color = CalEventTheme.colors.ink2)
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip(SlotStatus.KEPT)
            StatusChip(SlotStatus.TO_REVIEW)
            StatusChip(SlotStatus.IGNORED)
        }
    }
}

@Composable
private fun PhotoZone(slot: Slot, zoneWidth: Dp, zoneHeight: Dp, onClick: () -> Unit, modifier: Modifier) {
    val c = CalEventTheme.colors
    val (fg, tint, icon) = when (slot.status) {
        SlotStatus.KEPT -> Triple(c.kept, c.keptBg, CalEventIcons.Check)
        SlotStatus.TO_REVIEW -> Triple(c.review, c.reviewBg, CalEventIcons.Warning)
        SlotStatus.IGNORED -> Triple(c.ignored, c.ignoredBg, CalEventIcons.EyeOff)
    }
    val statusLabel = stringResource(
        when (slot.status) {
            SlotStatus.KEPT -> R.string.status_kept
            SlotStatus.TO_REVIEW -> R.string.status_review
            SlotStatus.IGNORED -> R.string.status_ignored
        },
    )
    val description = stringResource(R.string.results_zone_cd, statusLabel, slot.date.format(DAY_FORMAT) + " " + slotTimeText(slot), slot.title)
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier.clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(zoneWidth, zoneHeight)
                // Light tint so the paper stays readable under the zone.
                .background(tint.copy(alpha = 0.35f), shape)
                .then(if (slot.status == SlotStatus.KEPT) Modifier.border(2.dp, fg, shape) else Modifier.dashedBorder(fg, 2.dp)),
        )
        // Status badge: icon on a theme surface so that it reads on any paper colour.
        Box(
            Modifier.align(Alignment.TopEnd).size(18.dp).background(c.surface, CircleShape).border(1.dp, fg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
        }
    }
}
