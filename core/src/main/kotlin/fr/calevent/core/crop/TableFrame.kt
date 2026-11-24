package fr.calevent.core.crop

import fr.calevent.core.image.GridDetector
import fr.calevent.core.image.Point
import fr.calevent.core.parse.RecognizedText
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Finds the outer frame of the ruled table of a photographed schedule, for the automatic crop. Unlike
 * [GridDetector] (which must place every cell exactly for the cell-by-cell reading), this only needs the extent of
 * the table, but must not be fooled by tinted cells, light rules between coloured cells, thick week separators or
 * textures (wood grain, fabric). Pure Kotlin, O(pixels) on a copy reduced to [WORK_SIDE] pixels.
 *
 * 1. Edge pixels, per direction, on the plain gray image (no background division, which would flatten tinted
 *    cells): a pixel differing by [EDGE_CONTRAST] or more from its neighbour [EDGE_DISTANCE] px above or below
 *    (horizontal structures) or left or right (vertical ones). Thin rules, light gaps between coloured cells and
 *    the borders of tinted cells all give edges, whatever their polarity.
 * 2. Long runs of edge pixels become straight segments, collinear ones merge into lines ([GridDetector]).
 * 3. Horizontal and vertical lines that cross are linked; each connected group is a candidate table. The border
 *    of a coloured cell crosses the table's rules, so it belongs to the table's group, not to a group of its own.
 * 4. Candidates need [MIN_LINES] lines in each direction, [MIN_CROSSINGS] crossings and [MIN_AREA] of the image.
 *    Textures (lines packed closer than [MIN_SPACING]) are dropped, and so is a candidate with no recognised text
 *    inside when there is text elsewhere ([choose]).
 * 5. The best candidate (area x share of the text inside) absorbs the candidates touching or overlapping it (a
 *    roster split into blocks by thick separators).
 */
object TableFrame {
    /** Long side of the reduced copy the edges are searched on. */
    const val WORK_SIDE = 800

    /** Gray difference (0..255) between a pixel and its neighbour for an edge; tinted cells differ by 20..40. */
    const val EDGE_CONTRAST = 18

    /** Distance of the compared neighbour, pixels of the reduced copy. */
    const val EDGE_DISTANCE = 2

    /** Minimum run, fraction of the long side. */
    const val MIN_RUN_RATIO = 0.03

    /** Thicker than this (fraction of the long side, edge band included) is a filled block. */
    const val MAX_THICKNESS_RATIO = 0.018

    /** Crossing tolerance, fraction of the long side. */
    const val CROSS_TOLERANCE_RATIO = 0.012

    const val MIN_LINES = 3
    const val MIN_CROSSINGS = 6

    /** A candidate smaller than this share of the image is a cell or a stamp, not the table. */
    const val MIN_AREA = 0.04f

    /** Median spacing of the lines below this fraction of the long side: a texture, not a table. */
    const val MIN_SPACING = 0.015

    /** Candidates this close (fraction of the image side) to the chosen one are part of the same table. */
    const val MERGE_GAP = 0.03f

    /** With at least this many text lines on the photo, a candidate needs [MIN_TEXT_INSIDE] of them inside. */
    const val TEXT_EVIDENCE = 3
    const val MIN_TEXT_INSIDE = 2

    /** A group of crossing lines, in 0..1 coordinates of the image. */
    data class Candidate(
        val rect: NormalizedRect,
        val hLines: Int,
        val vLines: Int,
        val crossings: Int,
        /** Median gap between neighbouring lines, fraction of the long side. */
        val spacing: Double,
    ) {
        val texture: Boolean get() = spacing < MIN_SPACING
        val area: Float get() = (rect.right - rect.left) * (rect.bottom - rect.top)
    }

    /** Candidate tables of the `width * height` gray image [gray] (0 = black), largest first. */
    fun candidates(gray: IntArray, width: Int, height: Int): List<Candidate> {
        require(width > 0 && height > 0 && gray.size == width * height) { "bad image size" }
        val f = max(1, ceil(max(width, height).toDouble() / WORK_SIDE).toInt())
        val w = width / f
        val h = height / f
        if (w < 16 || h < 16) return emptyList()
        val img = reduce(gray, width, w, h, f)
        val longSide = max(w, h)
        val minRun = max(8, (MIN_RUN_RATIO * longSide).roundToInt())
        val maxThickness = max(6.0, MAX_THICKNESS_RATIO * longSide)
        val mergeOffset = max(3.0, GridDetector.MERGE_OFFSET_RATIO * longSide)
        val tol = max(5.0, CROSS_TOLERANCE_RATIO * longSide)

        val hl = GridDetector.mergeSegments(GridDetector.segmentsOf(edges(img, w, h, true), w, h, true, minRun, maxThickness), mergeOffset)
        val vl = GridDetector.mergeSegments(GridDetector.segmentsOf(edges(img, w, h, false), w, h, false, minRun, maxThickness), mergeOffset)

        val crossings = ArrayList<Triple<Int, Int, Point>>()
        for (i in hl.indices) for (j in vl.indices) {
            if (GridDetector.crosses(hl[i], vl[j], tol, tol)) crossings += Triple(i, j, GridDetector.intersect(hl[i], vl[j]))
        }

        // Union-find over the lines (horizontal first), linked when they cross.
        val parent = IntArray(hl.size + vl.size) { it }
        fun find(a: Int): Int {
            var x = a
            while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }
            return x
        }
        for ((i, j) in crossings) {
            val a = find(i)
            val b = find(hl.size + j)
            if (a != b) parent[a] = b
        }
        val byGroup = crossings.groupBy { find(it.first) }
        return byGroup.values.mapNotNull { cs ->
            val hs = cs.map { it.first }.distinct().map { hl[it] }
            val vs = cs.map { it.second }.distinct().map { vl[it] }
            if (hs.size < MIN_LINES || vs.size < MIN_LINES || cs.size < MIN_CROSSINGS) return@mapNotNull null
            val pts = cs.map { it.third }
            val rect = NormalizedRect(
                (pts.minOf { it.x } / w).coerceIn(0.0, 1.0).toFloat(), (pts.minOf { it.y } / h).coerceIn(0.0, 1.0).toFloat(),
                (pts.maxOf { it.x } / w).coerceIn(0.0, 1.0).toFloat(), (pts.maxOf { it.y } / h).coerceIn(0.0, 1.0).toFloat(),
            )
            val midX = vs.map { it.at(0.0) }.average()
            val midY = hs.map { it.at(midX) }.average()
            val gaps = gaps(hs.map { it.at(midX) }) + gaps(vs.map { it.at(midY) })
            val spacing = if (gaps.isEmpty()) 0.0 else gaps.sorted()[gaps.size / 2] / longSide
            Candidate(rect, hs.size, vs.size, cs.size, spacing).takeIf { it.area >= MIN_AREA }
        }.sortedByDescending { it.area }
    }

    /**
     * The table frame among [candidates], or null when none is credible. [text] (any size; only positions are used)
     * rejects candidates without text inside when the photo has text elsewhere, and weighs the others.
     */
    fun choose(candidates: List<Candidate>, text: RecognizedText?): NormalizedRect? {
        val centers = text?.takeIf { it.width > 0 && it.height > 0 }?.lines
            ?.filter { it.box.width > 0 && it.box.height > 0 }
            ?.map { (it.box.left + it.box.right) / 2 / text.width to (it.box.top + it.box.bottom) / 2 / text.height }
            .orEmpty()
        val hasText = centers.size >= TEXT_EVIDENCE
        fun inside(r: NormalizedRect) = centers.count { (x, y) -> x >= r.left && x <= r.right && y >= r.top && y <= r.bottom }
        val valid = candidates.filter { !it.texture && (!hasText || inside(it.rect) >= MIN_TEXT_INSIDE) }
        if (valid.isEmpty()) return null
        fun score(c: Candidate) = c.area * (if (hasText) (inside(c.rect) + 1f) / (centers.size + 1f) else 1f)
        var frame = valid.maxBy { score(it) }.rect
        val rest = valid.map { it.rect }.toMutableList().apply { remove(frame) }
        while (true) {
            val near = rest.filter { touches(frame, it) }
            if (near.isEmpty()) break
            rest -= near.toSet()
            frame = near.fold(frame) { a, b -> NormalizedRect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom)) }
        }
        return frame
    }

    /** Frame of the table of [gray] ([candidates] then [choose]), or null. */
    fun find(gray: IntArray, width: Int, height: Int, text: RecognizedText?): NormalizedRect? =
        choose(candidates(gray, width, height), text)

    /** True when [b] overlaps [a] or lies next to it (gap up to [MERGE_GAP]) along at least half its side. */
    private fun touches(a: NormalizedRect, b: NormalizedRect): Boolean {
        val gapX = max(a.left, b.left) - min(a.right, b.right)
        val gapY = max(a.top, b.top) - min(a.bottom, b.bottom)
        if (gapX > MERGE_GAP || gapY > MERGE_GAP) return false
        val overlapX = min(a.right, b.right) - max(a.left, b.left)
        val overlapY = min(a.bottom, b.bottom) - max(a.top, b.top)
        // Side by side: share most of the height; one above the other: share most of the width; or they overlap.
        return (overlapX > 0 && overlapY > 0) ||
            overlapY >= 0.5f * min(a.bottom - a.top, b.bottom - b.top) ||
            overlapX >= 0.5f * min(a.right - a.left, b.right - b.left)
    }

    /** Gaps between consecutive sorted positions, the near-duplicates (under 2 px) left out. */
    private fun gaps(positions: List<Double>): List<Double> =
        positions.sorted().zipWithNext { a, b -> b - a }.filter { it >= 2.0 }

    /** Box-average reduction by the integer factor [f]. */
    private fun reduce(gray: IntArray, width: Int, w: Int, h: Int, f: Int): IntArray {
        if (f == 1) return gray.copyOf(w * h)
        val out = IntArray(w * h)
        val area = f * f
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0
            for (dy in 0 until f) {
                val row = (y * f + dy) * width + x * f
                for (dx in 0 until f) s += gray[row + dx]
            }
            out[y * w + x] = s / area
        }
        return out
    }

    /**
     * Edge pixels for lines along rows ([horizontal]: compared with the pixels above and below) or along columns:
     * the difference with a neighbour [EDGE_DISTANCE] px away is at least [EDGE_CONTRAST], either way.
     */
    private fun edges(img: IntArray, w: Int, h: Int, horizontal: Boolean): BooleanArray {
        val k = EDGE_DISTANCE
        val mask = BooleanArray(w * h)
        val step = if (horizontal) k * w else k
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val p = img[i]
            val lo = if (horizontal) y >= k else x >= k
            val hi = if (horizontal) y + k < h else x + k < w
            val d = max(if (lo) abs(p - img[i - step]) else 0, if (hi) abs(p - img[i + step]) else 0)
            mask[i] = d >= EDGE_CONTRAST
        }
        // Where two rules cross, a pixel equals its neighbours across the line (both on the other rule): close
        // the short gaps between two longer pieces along the line, so a rule is not cut into pieces shorter than a
        // run at every crossing. Gaps between short pieces (text) stay open.
        val lenAlong = if (horizontal) w else h
        val lenAcross = if (horizontal) h else w
        fun idx(u: Int, v: Int) = if (horizontal) v * w + u else u * w + v
        for (v in 0 until lenAcross) {
            var prevStart = -1
            var prevEnd = -1 // exclusive
            var u = 0
            while (u < lenAlong) {
                if (!mask[idx(u, v)]) { u++; continue }
                val start = u
                while (u < lenAlong && mask[idx(u, v)]) u++
                if (prevEnd >= 0 && start - prevEnd <= MAX_GAP && prevEnd - prevStart >= MIN_BRIDGED && u - start >= MIN_BRIDGED) {
                    for (g in prevEnd until start) mask[idx(g, v)] = true
                    prevEnd = u // the joined piece goes on
                } else {
                    prevStart = start
                    prevEnd = u
                }
            }
        }
        return mask
    }

    /** Pieces at least this long (pixels) on both sides of a gap are joined. */
    private const val MIN_BRIDGED = 5

    /** Longest gap (pixels) closed along a line. */
    private const val MAX_GAP = 3
}
