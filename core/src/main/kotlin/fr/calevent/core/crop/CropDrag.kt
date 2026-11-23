package fr.calevent.core.crop

import kotlin.math.abs
import kotlin.math.hypot

/** What a finger grabbed in the crop frame: a corner, the middle of an edge, or the whole frame. */
enum class CropHandle { TopLeft, TopRight, BottomLeft, BottomRight, Top, Bottom, Left, Right, Move }

/** Touch logic of the crop frame (hit test, drag, minimum size), pure so it can be unit tested. */
object CropDrag {
    /** Smallest side of the frame, fraction of the photo side, whatever the screen. */
    const val MIN_FRACTION = 0.15f

    /** Minimum side as a fraction: [minPx] on screen, never below [MIN_FRACTION] nor above one half. */
    fun minimumSize(sidePx: Float, minPx: Float): Float {
        if (sidePx <= 0f) return MIN_FRACTION
        return (minPx / sidePx).coerceIn(MIN_FRACTION, 0.5f)
    }

    /**
     * Which part of [rect] a touch at ([x], [y]) grabs; coordinates in pixels of a [width] x [height] photo area,
     * [touchRadius] in pixels (half of the touch target). Corners win over edges, edges over the inside;
     * null when the touch is nowhere near the frame.
     */
    fun hitTest(rect: NormalizedRect, x: Float, y: Float, width: Float, height: Float, touchRadius: Float): CropHandle? {
        val l = rect.left * width
        val t = rect.top * height
        val r = rect.right * width
        val b = rect.bottom * height

        val corners = listOf(
            CropHandle.TopLeft to hypot(x - l, y - t),
            CropHandle.TopRight to hypot(x - r, y - t),
            CropHandle.BottomLeft to hypot(x - l, y - b),
            CropHandle.BottomRight to hypot(x - r, y - b),
        )
        corners.filter { it.second <= touchRadius }.minByOrNull { it.second }?.let { return it.first }

        val inX = x >= l && x <= r
        val inY = y >= t && y <= b
        val edges = buildList {
            if (inX && abs(y - t) <= touchRadius) add(CropHandle.Top to abs(y - t))
            if (inX && abs(y - b) <= touchRadius) add(CropHandle.Bottom to abs(y - b))
            if (inY && abs(x - l) <= touchRadius) add(CropHandle.Left to abs(x - l))
            if (inY && abs(x - r) <= touchRadius) add(CropHandle.Right to abs(x - r))
        }
        edges.minByOrNull { it.second }?.let { return it.first }

        return if (inX && inY) CropHandle.Move else null
    }

    /**
     * New frame after dragging [handle] by ([dx], [dy]), given as fractions of the photo size. The frame stays
     * inside 0..1 and keeps at least [minWidth] x [minHeight]; [CropHandle.Move] keeps its size.
     */
    fun drag(rect: NormalizedRect, handle: CropHandle, dx: Float, dy: Float, minWidth: Float, minHeight: Float): NormalizedRect {
        var (l, t, r, b) = listOf(rect.left, rect.top, rect.right, rect.bottom)
        when (handle) {
            CropHandle.Move -> {
                val w = r - l
                val h = b - t
                l = (l + dx).coerceIn(0f, 1f - w)
                t = (t + dy).coerceIn(0f, 1f - h)
                r = l + w
                b = t + h
            }
            else -> {
                val moveLeft = handle == CropHandle.TopLeft || handle == CropHandle.BottomLeft || handle == CropHandle.Left
                val moveRight = handle == CropHandle.TopRight || handle == CropHandle.BottomRight || handle == CropHandle.Right
                val moveTop = handle == CropHandle.TopLeft || handle == CropHandle.TopRight || handle == CropHandle.Top
                val moveBottom = handle == CropHandle.BottomLeft || handle == CropHandle.BottomRight || handle == CropHandle.Bottom
                if (moveLeft) l = (l + dx).coerceIn(0f, r - minWidth)
                if (moveRight) r = (r + dx).coerceIn(l + minWidth, 1f)
                if (moveTop) t = (t + dy).coerceIn(0f, b - minHeight)
                if (moveBottom) b = (b + dy).coerceIn(t + minHeight, 1f)
            }
        }
        return NormalizedRect(l, t, r, b)
    }

    /** Point of the frame under the finger for [handle] (used to centre the magnifier), 0..1 coordinates. */
    fun focusPoint(rect: NormalizedRect, handle: CropHandle): Pair<Float, Float> {
        val cx = (rect.left + rect.right) / 2
        val cy = (rect.top + rect.bottom) / 2
        val x = when (handle) {
            CropHandle.TopLeft, CropHandle.BottomLeft, CropHandle.Left -> rect.left
            CropHandle.TopRight, CropHandle.BottomRight, CropHandle.Right -> rect.right
            else -> cx
        }
        val y = when (handle) {
            CropHandle.TopLeft, CropHandle.TopRight, CropHandle.Top -> rect.top
            CropHandle.BottomLeft, CropHandle.BottomRight, CropHandle.Bottom -> rect.bottom
            else -> cy
        }
        return x to y
    }
}
