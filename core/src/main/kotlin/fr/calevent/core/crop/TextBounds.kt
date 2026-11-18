package fr.calevent.core.crop

import fr.calevent.core.parse.Box
import fr.calevent.core.parse.RecognizedText
import fr.calevent.core.parse.TextLine
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Rectangle in 0..1 coordinates of the image (origin top-left). */
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Suggests a crop around the main block of recognised text, to pre-crop a photographed schedule.
 * Pure computation on OCR line boxes: no Android dependency.
 */
object TextBounds {
    /** Fewer usable lines than this: no suggestion. */
    const val MIN_LINES = 3

    /** Margin added around the text, as a fraction of the image width / height. */
    const val MARGIN = 0.03

    /** A result covering more than this share of the image is not a useful crop. */
    const val MAX_COVERAGE = 0.95

    /** Lines further apart than this many median line heights belong to different clusters. */
    const val CLUSTER_GAP_HEIGHTS = 6.0

    /** Short lines (at most [SHORT_LINE_CHARS] characters) below this confidence are noise. */
    const val MIN_SHORT_CONFIDENCE = 0.4
    const val SHORT_LINE_CHARS = 3

    /** Returns the suggested crop, or null when there is nothing worth cropping. */
    fun suggestCrop(text: RecognizedText): NormalizedRect? {
        if (text.width <= 0 || text.height <= 0) return null
        val usable = text.lines.filter { it.box.width > 0 && it.box.height > 0 && !it.isNoise() }
        if (usable.size < MIN_LINES) return null

        val main = mainCluster(usable)
        if (main.size < MIN_LINES) return null

        val union = main.map { it.box }.reduce(Box::union)
        val mx = MARGIN * text.width
        val my = MARGIN * text.height
        val rect = NormalizedRect(
            left = ((union.left - mx) / text.width).coerceIn(0.0, 1.0).toFloat(),
            top = ((union.top - my) / text.height).coerceIn(0.0, 1.0).toFloat(),
            right = ((union.right + mx) / text.width).coerceIn(0.0, 1.0).toFloat(),
            bottom = ((union.bottom + my) / text.height).coerceIn(0.0, 1.0).toFloat(),
        )
        val coverage = (rect.right - rect.left).toDouble() * (rect.bottom - rect.top)
        return if (coverage > MAX_COVERAGE) null else rect
    }

    private fun TextLine.isNoise(): Boolean =
        text.trim().length <= SHORT_LINE_CHARS && (confidence ?: 1.0) < MIN_SHORT_CONFIDENCE

    /** Single-linkage clusters of boxes (gap relative to median height); keeps the one with the largest text area. */
    private fun mainCluster(lines: List<TextLine>): List<TextLine> {
        val heights = lines.map { it.box.height }.sorted()
        val gap = CLUSTER_GAP_HEIGHTS * heights[heights.size / 2]

        val parent = IntArray(lines.size) { it }
        fun find(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            var x = i
            while (parent[x] != r) { val n = parent[x]; parent[x] = r; x = n }
            return r
        }
        for (i in lines.indices) for (j in i + 1 until lines.size) {
            if (distance(lines[i].box, lines[j].box) <= gap) parent[find(i)] = find(j)
        }
        return lines.indices.groupBy { find(it) }.values
            .maxBy { idx -> idx.sumOf { lines[it].box.width * lines[it].box.height } }
            .map { lines[it] }
    }

    /** Gap between two rectangles (0 when they touch or overlap). */
    private fun distance(a: Box, b: Box): Double {
        val dx = max(0.0, max(a.left, b.left) - min(a.right, b.right))
        val dy = max(0.0, max(a.top, b.top) - min(a.bottom, b.bottom))
        return hypot(dx, dy)
    }
}
