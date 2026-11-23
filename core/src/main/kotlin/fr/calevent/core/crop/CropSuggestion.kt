package fr.calevent.core.crop

import fr.calevent.core.image.TableGrid
import kotlin.math.max
import kotlin.math.min

/**
 * Decides the automatic crop proposed for a photographed schedule, independently of the OCR engine.
 *
 * The ruled table found by [fr.calevent.core.image.GridDetector] is the primary signal: the frame is the outer
 * bounds of the grid plus a small margin. The title band just above the table is included when the recognised
 * text ([TextBounds]) reaches up there; otherwise the top margin is simply larger. Without a convincing grid the
 * text bounds are used as they are. Pure computation, no Android dependency.
 */
object CropSuggestion {
    /** Margin added left, right and below the grid, fraction of the image side. */
    const val SIDE_MARGIN = 0.02f

    /** Margin above the grid when no title text is found there. */
    const val TOP_MARGIN = 0.04f

    /** A text block this far above the grid (fraction of the image height) or less is taken as its title. */
    const val TITLE_MAX_GAP = 0.15f

    /** The text block must overlap at least this share of the grid width to count as its title. */
    const val MIN_TITLE_OVERLAP = 0.5f

    /** A grid smaller than this share of the image is probably a false detection. */
    const val MIN_GRID_COVERAGE = 0.10f

    /** A frame covering more than this share of the image is not a useful crop. */
    const val MAX_COVERAGE = 0.95f

    /** Outer bounds of the cell corners of [grid], 0..1 of the [width] x [height] image; null if degenerate. */
    fun gridBounds(grid: TableGrid, width: Int, height: Int): NormalizedRect? {
        if (width <= 0 || height <= 0) return null
        val points = grid.cells.flatMap { listOf(it.topLeft, it.topRight, it.bottomRight, it.bottomLeft) }
        if (points.isEmpty()) return null
        val left = points.minOf { it.x } / width
        val right = points.maxOf { it.x } / width
        val top = points.minOf { it.y } / height
        val bottom = points.maxOf { it.y } / height
        val rect = NormalizedRect(
            left.coerceIn(0.0, 1.0).toFloat(), top.coerceIn(0.0, 1.0).toFloat(),
            right.coerceIn(0.0, 1.0).toFloat(), bottom.coerceIn(0.0, 1.0).toFloat(),
        )
        return if (rect.right > rect.left && rect.bottom > rect.top) rect else null
    }

    /**
     * Frame around the grid, or null when the grid is implausible (too small) or the frame would cover
     * nearly the whole image. [text] is the optional main text block ([TextBounds.suggestCrop]).
     */
    fun fromGrid(grid: TableGrid, width: Int, height: Int, text: NormalizedRect? = null): NormalizedRect? {
        val g = gridBounds(grid, width, height) ?: return null
        if (area(g) < MIN_GRID_COVERAGE) return null

        val gridWidth = g.right - g.left
        val overlap = if (text == null) 0f else min(text.right, g.right) - max(text.left, g.left)
        val titleTop = text?.takeIf {
            it.top < g.top && g.top - it.top <= TITLE_MAX_GAP && overlap >= MIN_TITLE_OVERLAP * gridWidth
        }?.top

        val rect = NormalizedRect(
            left = (g.left - SIDE_MARGIN).coerceIn(0f, 1f),
            top = (titleTop ?: (g.top - TOP_MARGIN)).coerceIn(0f, 1f),
            right = (g.right + SIDE_MARGIN).coerceIn(0f, 1f),
            bottom = (g.bottom + SIDE_MARGIN).coerceIn(0f, 1f),
        )
        return if (area(rect) > MAX_COVERAGE) null else rect
    }

    /** The grid frame when there is one, otherwise the text bounds (which may be null too). */
    fun suggest(grid: TableGrid?, width: Int, height: Int, text: NormalizedRect?): NormalizedRect? =
        grid?.let { fromGrid(it, width, height, text) } ?: text

    private fun area(r: NormalizedRect): Float = (r.right - r.left) * (r.bottom - r.top)
}
