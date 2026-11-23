package fr.calevent.core.image

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A point in image pixels. */
data class Point(val x: Double, val y: Double)

/**
 * One cell of a detected table (possibly several grid cells merged when a ruling line is missing between
 * them, e.g. a title spanning all columns). Corners are the intersections of the ruling lines' centers;
 * [inner] is the axis-aligned rectangle inside the cell once the lines (half their thickness) are removed.
 */
data class TableCell(
    val row: Int,
    val col: Int,
    val rowSpan: Int,
    val colSpan: Int,
    val topLeft: Point,
    val topRight: Point,
    val bottomRight: Point,
    val bottomLeft: Point,
    val inner: IntBox,
)

/** Integer rectangle, [right] and [bottom] exclusive. */
data class IntBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** A ruled table found on the image: [rows] x [cols] grid cells, merged ones listed once in [cells]. */
data class TableGrid(val rows: Int, val cols: Int, val cells: List<TableCell>)

/**
 * Finds the ruled grid of a table schedule, so that each cell can be read on its own. Pure Kotlin, O(pixels).
 *
 * 1. Line pixels: pixels clearly darker than one of their neighbours a few pixels away (thin rules, light or
 *    dark, and the edges of tinted cells and dark bands), plus dark pixels in a thin dark run across the line
 *    (thick rules). The inside of filled blocks and faint show-through stay out.
 * 2. Long runs of line pixels along rows (and, transposed, along columns); a run is taken on the pixel's row OR
 *    its two neighbours, so a line tilted by a few degrees still gives long runs.
 * 3. Runs that touch in consecutive rows form segments; each gets a least-squares fit (position and slope) and a
 *    mean thickness. Filled blocks (too thick) and steep segments are dropped.
 * 4. Collinear segments (similar slope, same position) merge into ruling lines, gaps allowed (missing pieces).
 * 5. Lines that do not cross at least [MIN_CROSSINGS] drawn lines of the other direction, or that cover less
 *    than [MIN_SPAN_COVERAGE] of the table, are dropped, repeatedly (edges of text boxes inside cells, paper edges).
 * 6. Cells are the quadrilaterals between consecutive lines; neighbours are merged where the line between them
 *    is mostly missing (a title spanning several columns).
 *
 * Returns null when the table is not convincing (fewer than 3 x 3 cells): the caller reads the whole page.
 */
object GridDetector {

    /** Darker than this (0..255, after background division) is ink. Same level as the grid eraser. */
    const val DARK_THRESHOLD = 154

    /** A pixel lighter than this is never part of a line (show-through from the back of the sheet). */
    private const val LIGHT_LIMIT = 215

    /** Contrast a line pixel must have with one of its neighbours [STEP_DISTANCE_RATIO] away. */
    private const val STEP_CONTRAST = 40

    /** Distance at which the neighbours are sampled, fraction of the long side (4 px on 2000 px), at least 3 px. */
    private const val STEP_DISTANCE_RATIO = 0.002
    private const val STEP_MIN_DISTANCE = 3

    /** Minimum run, as a fraction of the long side (60 px on a 2000 px image). */
    private const val MIN_RUN_RATIO = 0.03

    /** Thicker than this (fraction of the long side) is a filled block, not a line. */
    private const val MAX_THICKNESS_RATIO = 0.012

    /** Max |slope| of a line (about 5 degrees). */
    private const val MAX_SLOPE = 0.09

    /** Max slope difference for two segments of the same line (about 1.5 degrees). */
    private const val MERGE_SLOPE = 0.027

    /** Max offset between two segments of the same line, fraction of the long side (6 px on 2000 px). */
    private const val MERGE_OFFSET_RATIO = 0.003

    /** Tolerance when testing whether two lines cross, fraction of the long side (20 px on 2000 px). */
    private const val CROSS_TOLERANCE_RATIO = 0.01

    private const val MIN_CROSSINGS = 3

    /** How close to a drawn part of a line a crossing must be, fraction of the long side (16 px on 2000 px). */
    private const val TOUCH_RATIO = 0.008

    /** A ruling line covers at least this fraction of the table (first to last line of the other direction). */
    private const val MIN_SPAN_COVERAGE = 0.6

    /** A line separates two neighbour cells when it covers at least this fraction of their common side. */
    private const val MIN_COVERAGE = 0.5

    /** Extra margin removed inside each cell beyond the line's half thickness, pixels. */
    private const val INSET = 2

    /** Debug output of the candidate lines (local evaluation only). */
    internal var trace: ((String) -> Unit)? = null

    /**
     * @param img `width * height` gray values (0 = black), background-divided (see [OcrPreprocess]); not modified.
     */
    fun detect(img: IntArray, width: Int, height: Int): TableGrid? {
        require(img.size == width * height) { "bad image size" }
        val longSide = max(width, height)
        val minRun = max(8, (MIN_RUN_RATIO * longSide).roundToInt())
        val maxThickness = max(4.0, MAX_THICKNESS_RATIO * longSide)
        val mergeOffset = max(3.0, MERGE_OFFSET_RATIO * longSide)
        val tol = max(6.0, CROSS_TOLERANCE_RATIO * longSide)

        val k = max(STEP_MIN_DISTANCE, (STEP_DISTANCE_RATIO * longSide).roundToInt())
        val steps = stepMask(img, width, height, k)
        var hLines = mergeSegments(segments(img, steps, width, height, true, minRun, maxThickness), mergeOffset)
        var vLines = mergeSegments(segments(img, steps, width, height, false, minRun, maxThickness), mergeOffset)

        trace?.let { t ->
            for (l in hLines + vLines) t("${if (l.horizontal) "H" else "V"} at=%.0f b=%.3f %d..%d len=%d th=%.1f".format(l.a, l.b, l.minU, l.maxU, l.length, l.thickness))
        }
        // Keep the lines that cross enough lines of the other direction, then those that run along most of the
        // table, until stable (edges of text boxes inside cells, show-through and paper edges go away).
        val touch = max(4.0, TOUCH_RATIO * longSide)
        fun crossCount(l: Line, others: List<Line>) =
            others.count { o -> if (l.horizontal) crosses(l, o, tol, touch) else crosses(o, l, tol, touch) }
        fun spans(l: Line, others: List<Line>): Boolean {
            if (others.isEmpty()) return false
            // The table along the line: from the first to the last line of the other direction.
            val along = others.map { o -> if (l.horizontal) intersect(l, o).x else intersect(o, l).y }
            return l.coverage(along.min(), along.max()) >= MIN_SPAN_COVERAGE
        }
        while (true) {
            while (true) {
                val h2 = hLines.filter { crossCount(it, vLines) >= MIN_CROSSINGS }
                val v2 = vLines.filter { crossCount(it, h2) >= MIN_CROSSINGS }
                val stable = h2.size == hLines.size && v2.size == vLines.size
                hLines = h2
                vLines = v2
                if (stable) break
            }
            val h3 = hLines.filter { spans(it, vLines) }
            val v3 = vLines.filter { spans(it, hLines) }
            val stable = h3.size == hLines.size && v3.size == vLines.size
            hLines = h3
            vLines = v3
            if (stable) break
        }
        if (hLines.size < 4 || vLines.size < 4) return null

        // Order along the other axis, at the middle of the table.
        val midX = vLines.map { it.at(0.0) }.average()
        val midY = hLines.map { it.at(midX) }.average()
        hLines = hLines.sortedBy { it.at(midX) }
        vLines = vLines.sortedBy { it.at(midY) }
        // Two fits too close (a thick double rule split in two) would make empty slivers of cells.
        hLines = dedupe(hLines, midX, tol / 2)
        vLines = dedupe(vLines, midY, tol / 2)
        if (hLines.size < 4 || vLines.size < 4) return null

        val rows = hLines.size - 1
        val cols = vLines.size - 1
        val corner = Array(hLines.size) { i -> Array(vLines.size) { j -> intersect(hLines[i], vLines[j]) } }

        // Union-find over grid cells: merge across a line that is mostly missing between them.
        val parent = IntArray(rows * cols) { it }
        fun find(a: Int): Int {
            var x = a
            while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }
            return x
        }
        fun union(a: Int, b: Int) { parent[find(a)] = find(b) }
        for (i in 0 until rows) for (j in 1 until cols) {
            // Vertical line j between (i, j-1) and (i, j), over the row band i.
            val y0 = corner[i][j].y
            val y1 = corner[i + 1][j].y
            if (vLines[j].coverage(y0, y1) < MIN_COVERAGE) union(i * cols + j - 1, i * cols + j)
        }
        for (i in 1 until rows) for (j in 0 until cols) {
            val x0 = corner[i][j].x
            val x1 = corner[i][j + 1].x
            if (hLines[i].coverage(x0, x1) < MIN_COVERAGE) union((i - 1) * cols + j, i * cols + j)
        }

        // A group that is not a rectangle (an L shape) would overlap other cells: its cells stay separate.
        val groups = (0 until rows * cols).groupBy { find(it) }.values.flatMap { members ->
            val spanR = members.maxOf { it / cols } - members.minOf { it / cols } + 1
            val spanC = members.maxOf { it % cols } - members.minOf { it % cols } + 1
            if (spanR * spanC == members.size) listOf(members) else members.map { listOf(it) }
        }
        val cells = groups.mapNotNull { members ->
            val r0 = members.minOf { it / cols }
            val r1 = members.maxOf { it / cols } + 1
            val c0 = members.minOf { it % cols }
            val c1 = members.maxOf { it % cols } + 1
            val tl = corner[r0][c0]
            val tr = corner[r0][c1]
            val br = corner[r1][c1]
            val bl = corner[r1][c0]
            val left = max(tl.x, bl.x) + halfWidth(vLines[c0])
            val right = min(tr.x, br.x) - halfWidth(vLines[c1])
            val top = max(tl.y, tr.y) + halfWidth(hLines[r0])
            val bottom = min(bl.y, br.y) - halfWidth(hLines[r1])
            val box = IntBox(
                left.roundToInt().coerceIn(0, width), top.roundToInt().coerceIn(0, height),
                right.roundToInt().coerceIn(0, width), bottom.roundToInt().coerceIn(0, height),
            )
            if (box.width < 4 || box.height < 4) null
            else TableCell(r0, c0, r1 - r0, c1 - c0, tl, tr, br, bl, box)
        }.sortedWith(compareBy({ it.row }, { it.col }))
        if (cells.size < 9) return null
        return TableGrid(rows, cols, cells)
    }

    // ---- lines ---------------------------------------------------------------------------------------------

    /**
     * A straight line `across = a + b * along` (horizontal: y = a + b x; vertical: x = a + b y), with the
     * pixel moments it was fitted on, its mean [thickness] and the [intervals] (along the line) it covers.
     */
    internal class Line(
        val horizontal: Boolean,
        var n: Double, var su: Double, var sv: Double, var suu: Double, var suv: Double,
        val intervals: MutableList<IntRange>,
    ) {
        var a = 0.0
        var b = 0.0
        val minU: Int get() = intervals.minOf { it.first }
        val maxU: Int get() = intervals.maxOf { it.last }
        val length: Int get() = intervals.sumOf { it.last - it.first + 1 }
        val thickness: Double get() = n / length.coerceAtLeast(1)

        init { refit() }

        fun refit() {
            val det = n * suu - su * su
            b = if (abs(det) < 1e-9) 0.0 else (n * suv - su * sv) / det
            a = (sv - b * su) / n
        }

        /** Position across the line at [u] along it. */
        fun at(u: Double): Double = a + b * u

        fun absorb(o: Line) {
            n += o.n; su += o.su; sv += o.sv; suu += o.suu; suv += o.suv
            intervals += o.intervals
            normalize()
            refit()
        }

        fun normalize() {
            intervals.sortBy { it.first }
            val out = ArrayList<IntRange>()
            for (r in intervals) {
                val last = out.lastOrNull()
                if (last != null && r.first <= last.last + 1) out[out.size - 1] = last.first..max(last.last, r.last)
                else out += r
            }
            intervals.clear()
            intervals += out
        }

        /** Fraction of [from]..[to] (along the line) covered by its intervals. */
        fun coverage(from: Double, to: Double): Double {
            val lo = min(from, to)
            val hi = max(from, to)
            if (hi - lo < 1) return 1.0
            var covered = 0.0
            for (r in intervals) covered += max(0.0, min(hi, r.last + 1.0) - max(lo, r.first.toDouble()))
            return covered / (hi - lo)
        }
    }

    /** Half the drawn width of [l] plus a margin (its thickness includes the 2 px of the neighbour rule). */
    private fun halfWidth(l: Line): Double = max(1.0, (l.thickness - 2) / 2) + INSET

    /** Intersection of a horizontal and a vertical line. */
    private fun intersect(h: Line, v: Line): Point {
        // y = ah + bh x ; x = av + bv y
        val x = (v.a + v.b * h.a) / (1 - v.b * h.b)
        return Point(x, h.at(x))
    }

    /**
     * True when [h] and [v] meet: the intersection is on [v] (within [tol] of its ends: a rule may stop just
     * short of the table border) and on a drawn part of [h] (within [touch]).
     */
    private fun crosses(h: Line, v: Line, tol: Double, touch: Double): Boolean {
        val p = intersect(h, v)
        if (p.y < v.minU - tol || p.y > v.maxU + tol) return false
        if (p.x < h.minU - tol || p.x > h.maxU + tol) return false
        // Each line's runs stop at the other line's body (and its halo, for a dark band): allow its half width.
        val th = touch + v.thickness / 2
        val tv = touch + h.thickness / 2
        return h.intervals.any { p.x >= it.first - th && p.x <= it.last + th } &&
            v.intervals.any { p.y >= it.first - tv && p.y <= it.last + tv }
    }

    private fun dedupe(lines: List<Line>, mid: Double, minGap: Double): List<Line> {
        val out = ArrayList<Line>()
        for (l in lines) {
            val last = out.lastOrNull()
            if (last != null && l.at(mid) - last.at(mid) < minGap) {
                if (l.length > last.length) out[out.size - 1] = l
            } else out += l
        }
        return out
    }

    /** Merges collinear segments into lines, longest first. */
    private fun mergeSegments(segments: List<Line>, maxOffset: Double): List<Line> {
        val lines = ArrayList<Line>()
        for (s in segments.sortedByDescending { it.length }) {
            val mid = (s.minU + s.maxU) / 2.0
            val target = lines.firstOrNull { l ->
                abs(l.b - s.b) <= MERGE_SLOPE && abs(l.at(mid) - s.at(mid)) <= maxOffset + (l.thickness + s.thickness) / 2
            }
            if (target != null) target.absorb(s) else lines += s
        }
        return lines
    }

    /**
     * Segments of long dark runs. [horizontal]: runs along rows; otherwise along columns (the image is read
     * transposed). A pixel counts as dark when it or one of its two neighbours across the line is dark.
     */
    private fun segments(img: IntArray, steps: BooleanArray, w: Int, h: Int, horizontal: Boolean, minRun: Int, maxThickness: Double): List<Line> {
        val lenU = if (horizontal) w else h // along the line
        val lenV = if (horizontal) h else w // across
        val mask = lineMask(img, steps, w, h, horizontal, maxThickness.toInt())
        fun dark(u: Int, v: Int): Boolean {
            if (v < 0 || v >= lenV) return false
            return mask[if (horizontal) v * w + u else u * w + v]
        }

        // Runs: across position, start, end (inclusive) along; component id via union-find.
        val runV = IntArrayList()
        val runS = IntArrayList()
        val runE = IntArrayList()
        val parent = IntArrayList()
        fun find(a: Int): Int {
            var x = a
            while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }
            return x
        }
        var prevFrom = 0
        var prevTo = 0
        for (v in 0 until lenV) {
            val from = runV.size
            var start = -1
            for (u in 0..lenU) {
                val d = u < lenU && (dark(u, v) || dark(u, v - 1) || dark(u, v + 1))
                if (d) {
                    if (start < 0) start = u
                } else if (start >= 0) {
                    if (u - start >= minRun) {
                        val id = runV.size
                        runV.add(v); runS.add(start); runE.add(u - 1); parent.add(id)
                        // Connect to touching runs of the previous across-position.
                        for (k in prevFrom until prevTo) {
                            if (runS[k] <= u && runE[k] >= start - 1) {
                                val ra = find(k)
                                val rb = find(id)
                                if (ra != rb) parent[ra] = rb
                            }
                        }
                    }
                    start = -1
                }
            }
            prevFrom = from
            prevTo = runV.size
        }

        // Moments per component.
        val byRoot = HashMap<Int, Line>()
        for (k in 0 until runV.size) {
            val root = find(k)
            val s = runS[k].toDouble()
            val e = runE[k].toDouble()
            val n = e - s + 1
            val su = n * (s + e) / 2
            val suu = (e * (e + 1) * (2 * e + 1) - (s - 1) * s * (2 * s - 1)) / 6
            val v = runV[k].toDouble()
            val line = byRoot[root]
            if (line == null) {
                byRoot[root] = Line(horizontal, n, su, n * v, suu, su * v, mutableListOf(runS[k]..runE[k]))
            } else {
                line.n += n; line.su += su; line.sv += n * v; line.suu += suu; line.suv += su * v
                line.intervals += runS[k]..runE[k]
            }
        }
        return byRoot.values.mapNotNull { l ->
            l.normalize()
            l.refit()
            // The +-1 neighbour rule adds about 2 px to the thickness.
            if (abs(l.b) > MAX_SLOPE || l.thickness - 2 > maxThickness) null else l
        }
    }

    /**
     * Pixels that may belong to a ruling line along [horizontal] (or vertical) direction: the [steps] (see
     * [stepMask]), plus dark pixels whose dark run across the line is at most [maxThickness] (thick rules).
     * The inside of filled blocks (dark headers, markers) is left out.
     */
    private fun lineMask(img: IntArray, steps: BooleanArray, w: Int, h: Int, horizontal: Boolean, maxThickness: Int): BooleanArray {
        val mask = steps.copyOf()
        // Thin dark runs across the line: along columns for horizontal lines, along rows for vertical ones.
        val lenAcross = if (horizontal) h else w
        val lenAlong = if (horizontal) w else h
        for (u in 0 until lenAlong) {
            var start = -1
            for (v in 0..lenAcross) {
                val idx = if (horizontal) v * w + u else u * w + v
                val dark = v < lenAcross && img[idx] < DARK_THRESHOLD
                if (dark) {
                    if (start < 0) start = v
                } else if (start >= 0) {
                    if (v - start <= maxThickness) for (t in start until v) mask[if (horizontal) t * w + u else u * w + t] = true
                    start = -1
                }
            }
        }
        return mask
    }

    /**
     * Pixels clearly darker than one of the 8 pixels [k] px away (straight and diagonal): thin rules, light or
     * dark, the edges of tinted cells and dark bands, and the crossings of two light rules (only the diagonal
     * neighbours are off the rules there).
     */
    private fun stepMask(img: IntArray, w: Int, h: Int, k: Int): BooleanArray {
        val mask = BooleanArray(w * h)
        for (y in 0 until h) {
            val up = y >= k
            val down = y + k < h
            for (x in 0 until w) {
                val i = y * w + x
                val p = img[i]
                if (p >= LIGHT_LIMIT) continue
                val left = x >= k
                val right = x + k < w
                var m = p
                if (up) {
                    m = max(m, img[i - k * w])
                    if (left) m = max(m, img[i - k * w - k])
                    if (right) m = max(m, img[i - k * w + k])
                }
                if (down) {
                    m = max(m, img[i + k * w])
                    if (left) m = max(m, img[i + k * w - k])
                    if (right) m = max(m, img[i + k * w + k])
                }
                if (left) m = max(m, img[i - k])
                if (right) m = max(m, img[i + k])
                mask[i] = m - p >= STEP_CONTRAST
            }
        }
        return mask
    }

    /** Growable int list without boxing. */
    private class IntArrayList {
        private var data = IntArray(1024)
        var size = 0
            private set

        fun add(x: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = x
        }

        operator fun get(i: Int): Int = data[i]
        operator fun set(i: Int, x: Int) { data[i] = x }
    }
}
