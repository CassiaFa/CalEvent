package fr.calevent.core.image

import fr.calevent.core.Region
import kotlin.math.max
import kotlin.math.min

/**
 * Zones highlighted on the photo of the results: a slot's [Region] is the box of the text it was read from, a thin
 * band that a status badge easily hides. The zone is the table cell holding that text when the ruled grid of the
 * photo is known ([cellsOf]), otherwise the text box padded generously. Pure computation, 0..1 coordinates.
 */
object PhotoZones {
    /** A cell larger than this share of the photo (a merged title band, a whole block) is not used as a zone. */
    const val MAX_CELL_AREA = 0.12

    /** Share of the text box that must lie inside the cell. */
    const val MIN_INSIDE = 0.6

    /** Padding around the text without a cell: this many text heights (the short side of the box) ... */
    const val PAD_HEIGHTS_Y = 0.6
    const val PAD_HEIGHTS_X = 0.5

    /** ... plus this fraction of the photo's long side. */
    const val PAD_RATIO = 0.004

    /** The cells of [grid] (found on an image of [width] x [height]) as 0..1 rectangles around their corners. */
    fun cellsOf(grid: TableGrid, width: Int, height: Int): List<Region> = grid.cells.map { c ->
        val xs = listOf(c.topLeft.x, c.topRight.x, c.bottomRight.x, c.bottomLeft.x)
        val ys = listOf(c.topLeft.y, c.topRight.y, c.bottomRight.y, c.bottomLeft.y)
        Region(
            (xs.min() / width).coerceIn(0.0, 1.0), (ys.min() / height).coerceIn(0.0, 1.0),
            (xs.max() / width).coerceIn(0.0, 1.0), (ys.max() / height).coerceIn(0.0, 1.0),
        )
    }

    /**
     * Zone for the text box [text] on a photo of [width] x [height]: the smallest of [cells] holding its centre
     * and most of it, or the text box padded.
     */
    fun zone(text: Region, cells: List<Region>, width: Int, height: Int): Region {
        val cx = (text.left + text.right) / 2
        val cy = (text.top + text.bottom) / 2
        val cell = cells
            .filter { c -> cx >= c.left && cx <= c.right && cy >= c.top && cy <= c.bottom && area(c) <= MAX_CELL_AREA }
            .filter { c -> inside(text, c) >= MIN_INSIDE * area(text) }
            .minByOrNull { area(it) }
        return cell ?: padded(text, width, height)
    }

    private fun padded(t: Region, width: Int, height: Int): Region {
        if (width <= 0 || height <= 0) return t
        val wPx = (t.right - t.left) * width
        val hPx = (t.bottom - t.top) * height
        val textHeight = min(wPx, hPx) // the short side: also right for text read turned a quarter
        val base = PAD_RATIO * max(width, height)
        // Along the text and across it: vertical text (a quarter turn) swaps the two paddings.
        val vertical = hPx > wPx
        val across = PAD_HEIGHTS_Y * textHeight + base
        val along = PAD_HEIGHTS_X * textHeight + base
        val padX = (if (vertical) across else along) / width
        val padY = (if (vertical) along else across) / height
        return Region(
            (t.left - padX).coerceIn(0.0, 1.0), (t.top - padY).coerceIn(0.0, 1.0),
            (t.right + padX).coerceIn(0.0, 1.0), (t.bottom + padY).coerceIn(0.0, 1.0),
        )
    }

    private fun area(r: Region) = max(0.0, r.right - r.left) * max(0.0, r.bottom - r.top)

    private fun inside(a: Region, b: Region): Double =
        max(0.0, min(a.right, b.right) - max(a.left, b.left)) * max(0.0, min(a.bottom, b.bottom) - max(a.top, b.top))
}
