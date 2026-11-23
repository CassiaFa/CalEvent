package fr.calevent.core.image

import fr.calevent.core.parse.Box
import fr.calevent.core.parse.RecognizedText
import fr.calevent.core.parse.TextElement
import fr.calevent.core.parse.TextLine
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The image of one table cell, ready for the OCR engine: cut from the cleaned page around its ink, upscaled so
 * the text is about [TableCells.TARGET_TEXT_HEIGHT] px tall and padded with white. [pixels] are gray 0..255.
 * A point (x, y) of the crop is at ([originX] + (x - [pad]) / [scale], [originY] + (y - [pad]) / [scale]) on the page.
 */
class CellCrop(
    val cell: TableCell,
    val pixels: IntArray,
    val width: Int,
    val height: Int,
    val originX: Int,
    val originY: Int,
    val scale: Double,
    val pad: Int,
    /** Number of text lines seen in the cell (ink bands): 1 lets the engine read it as a single line. */
    val textLines: Int,
) {
    /** Maps a box of the crop back to page coordinates. */
    fun toPage(b: Box): Box = Box(
        originX + (b.left - pad) / scale, originY + (b.top - pad) / scale,
        originX + (b.right - pad) / scale, originY + (b.bottom - pad) / scale,
    )
}

/**
 * Cell-by-cell reading of a ruled table: the OCR engine reads each non-empty cell alone (where it does much
 * better than on the whole page, which it splits into fragments and partly misses), then the words are mapped
 * back to page coordinates so the parser, which groups by geometry, is unchanged. The area outside the table
 * (title, month, notes) is read separately on [outside]. Pure Kotlin; the engine-specific part is in the app.
 */
object TableCells {

    /** Text band height (ascenders to descenders) the crops are scaled to, pixels. */
    const val TARGET_TEXT_HEIGHT = 36.0

    /** Upscaling is limited: beyond this the crop is mostly blur. Small text is never shrunk. */
    const val MAX_SCALE = 3.5

    /** Ink is darker than this on the cleaned image. */
    private const val INK = 128

    /** White border added around each crop, pixels (the engine reads poorly text touching the edge). */
    private const val PAD = 10

    /** Width of the border strip of a cell where rule remnants are looked for, pixels. */
    private const val EDGE = 12

    /** Gray level below which a pixel may belong to a rule remnant (remnants are often gray, not black). */
    private const val FAINT = 200

    /** Max thickness of a rule sliver cut by the cell border, pixels. */
    private const val SLIVER = 4

    /** A rule remnant runs along at least this fraction of the cell side. */
    private const val RULE_RUN = 0.4

    /** Bands of ink thinner than this are specks or line remnants, pixels. */
    private const val MIN_BAND = 4

    /** Crops of the non-empty cells of [grid], in reading order. [clean] is the cleaned page ([OcrPreprocess]). */
    fun crops(clean: IntArray, width: Int, height: Int, grid: TableGrid): List<CellCrop> =
        grid.cells.mapNotNull { crop(clean, width, it) }

    /** Copy of [clean] with the table painted white, for reading what is around it (titles, notes). */
    fun outside(clean: IntArray, width: Int, height: Int, grid: TableGrid): IntArray {
        val out = clean.copyOf()
        for (c in grid.cells) {
            val xs = listOf(c.topLeft.x, c.topRight.x, c.bottomRight.x, c.bottomLeft.x)
            val ys = listOf(c.topLeft.y, c.topRight.y, c.bottomRight.y, c.bottomLeft.y)
            val x0 = xs.min().toInt().coerceIn(0, width)
            val x1 = (xs.max().toInt() + 1).coerceIn(0, width)
            val y0 = ys.min().toInt().coerceIn(0, height)
            val y1 = (ys.max().toInt() + 1).coerceIn(0, height)
            for (y in y0 until y1) java.util.Arrays.fill(out, y * width + x0, y * width + x1, 255)
        }
        return out
    }

    /**
     * Assembles the page text: [outside] lines are already in page coordinates; each cell's lines (crop
     * coordinates) are mapped back with [CellCrop.toPage].
     */
    fun assemble(width: Int, height: Int, outside: List<TextLine>, cells: List<Pair<CellCrop, List<TextLine>>>): RecognizedText {
        val lines = ArrayList<TextLine>(outside)
        for ((crop, cellLines) in cells) for (l in cellLines) {
            lines += l.copy(
                box = crop.toPage(l.box),
                elements = l.elements.map { e -> e.copy(box = crop.toPage(e.box)) },
            )
        }
        return RecognizedText(width, height, lines)
    }

    /** Words of a line in reading order joined into a [TextLine] (box = union, confidence = mean). */
    fun line(words: List<TextElement>): TextLine {
        val confs = words.mapNotNull { it.confidence }
        return TextLine(
            text = words.joinToString(" ") { it.text },
            box = words.map { it.box }.reduce { a, b -> a union b },
            confidence = confs.takeIf { it.isNotEmpty() }?.average(),
            elements = words,
        )
    }

    /** Characters of shift codes and day headers ("M06", "L25"), for the second reading of code-like words. */
    const val CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    private val CODE_LIKE = Regex("""^\|?\s?[\p{L}0-9]{2,5}$""")
    private val CLEAN_CODE = Regex("""^[A-Z]{1,3}[0-9]{1,2}$""")
    private val TIME_LIKE = Regex("""[0-9][hH][0-9]?""")

    /**
     * True for a word that looks like a short code read with doubt ("MoO2", "So5", "506", "NOB8"): letters and
     * digits only, at least one digit, but not the clean shape letters-then-digits. Such words are read again
     * alone with [CODE_CHARS] only.
     */
    fun looksLikeMisreadCode(word: String): Boolean {
        val t = word.trim()
        return CODE_LIKE.matches(t) && t.trimStart('|', ' ').first().isLetter() && t.any { it.isDigit() } &&
            !CLEAN_CODE.matches(t) && !TIME_LIKE.containsMatchIn(t)
    }

    /** Area of [crop] (crop pixels) to read again for the word at [box] (crop coordinates), with a margin. */
    fun rereadArea(crop: CellCrop, box: Box): IntBox {
        val m = 0.3 * box.height
        return IntBox(
            (box.left - m).toInt().coerceIn(0, crop.width), (box.top - m).toInt().coerceIn(0, crop.height),
            (box.right + m + 1).toInt().coerceIn(0, crop.width), (box.bottom + m + 1).toInt().coerceIn(0, crop.height),
        )
    }

    /**
     * Replaces in [lines] (crop coordinates) each word for which [looksLikeMisreadCode] holds by its second
     * reading [reread] (text and confidence of the area [rereadArea], null when nothing was read).
     */
    fun applyRereads(crop: CellCrop, lines: List<TextLine>, reread: (IntBox) -> TextElement?): List<TextLine> =
        lines.map { l ->
            if (l.elements.none { looksLikeMisreadCode(it.text) }) l
            else line(l.elements.map { e ->
                if (!looksLikeMisreadCode(e.text)) e
                else reread(rereadArea(crop, e.box))?.takeIf { it.text.isNotBlank() }?.let { r -> e.copy(text = r.text) } ?: e
            })
        }

    internal fun crop(clean: IntArray, width: Int, cell: TableCell): CellCrop? {
        val box = cell.inner
        val bw = box.width
        val bh = box.height
        if (bw <= 0 || bh <= 0) return null
        val local = IntArray(bw * bh) { i -> clean[(box.top + i / bw) * width + box.left + i % bw] }
        removeRuleRemnants(local, bw, bh)

        // Ink bands along the rows of the cell.
        val rowInk = IntArray(bh)
        for (y in 0 until bh) {
            var n = 0
            for (x in 0 until bw) if (local[y * bw + x] < INK) n++
            rowInk[y] = n
        }
        val bands = ArrayList<IntRange>()
        var start = -1
        for (y in 0..bh) {
            val ink = y < bh && rowInk[y] > 0
            if (ink) {
                if (start < 0) start = y
            } else if (start >= 0) {
                val last = bands.lastOrNull()
                // A 1 px gap inside a line (thin stroke junctions) does not split it.
                if (last != null && start - last.last <= 2) bands[bands.size - 1] = last.first until y
                else bands += start until y
                start = -1
            }
        }
        val text = bands.filter { b ->
            b.last - b.first + 1 >= MIN_BAND && (b.first..b.last).sumOf { rowInk[it] } >= 2 * MIN_BAND * MIN_BAND
        }
        if (text.isEmpty()) return null

        // Ink extent over the text bands.
        var x0 = bw
        var x1 = -1
        for (b in text) for (y in b) for (x in 0 until bw) if (local[y * bw + x] < INK) {
            if (x < x0) x0 = x
            if (x > x1) x1 = x
        }
        val bandHeight = text.map { it.last - it.first + 1 }.sorted()[(text.size - 1) / 2].toDouble()
        val margin = max(2, (bandHeight / 2).roundToInt())
        val area = IntBox(
            max(0, x0 - margin), max(0, text.first().first - margin),
            min(bw, x1 + 1 + margin), min(bh, text.last().last + 1 + margin),
        )
        val scale = (TARGET_TEXT_HEIGHT / bandHeight).coerceIn(1.0, MAX_SCALE)
        val (pixels, w, h) = scaledPadded(local, bw, area, scale)
        return CellCrop(cell, pixels, w, h, box.left + area.left, box.top + area.top, scale, PAD, text.size)
    }

    /**
     * Paints white (in place) what is left of the ruling lines inside a cell image: a thick or slanted rule the
     * page-wide eraser missed, a line found a few pixels off. Such a remnant is an ink component near a side of
     * the cell, long along that side (over [RULE_RUN] of it), thin across, and longer than the letters; or a thin
     * sliver touching the border. Components are taken at [FAINT] so that gray remnants go too.
     */
    private fun removeRuleRemnants(px: IntArray, w: Int, h: Int) {
        val label = IntArray(w * h) { -1 }
        val boxes = ArrayList<IntArray>() // left, top, right, bottom (inclusive)
        val stack = IntArrayStack()
        for (i in px.indices) {
            if (px[i] >= FAINT || label[i] >= 0) continue
            val id = boxes.size
            val b = intArrayOf(i % w, i / w, i % w, i / w)
            label[i] = id
            stack.push(i)
            while (stack.size > 0) {
                val p = stack.pop()
                val x = p % w
                val y = p / w
                if (x < b[0]) b[0] = x
                if (x > b[2]) b[2] = x
                if (y < b[1]) b[1] = y
                if (y > b[3]) b[3] = y
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val q = ny * w + nx
                    if (px[q] < FAINT && label[q] < 0) { label[q] = id; stack.push(q) }
                }
            }
            boxes += b
        }
        if (boxes.isEmpty()) return
        // Letter size: median over the components that are not long along a side.
        val letters = boxes.filter { (it[3] - it[1] + 1) < RULE_RUN * h && (it[2] - it[0] + 1) < RULE_RUN * w }
        val medianH = letters.map { it[3] - it[1] + 1 }.sorted().let { if (it.isEmpty()) 0 else it[it.size / 2] }
        val medianW = letters.map { it[2] - it[0] + 1 }.sorted().let { if (it.isEmpty()) 0 else it[it.size / 2] }
        val remnant = BooleanArray(boxes.size) { id ->
            val b = boxes[id]
            val cw = b[2] - b[0] + 1
            val ch = b[3] - b[1] + 1
            val vertical = (b[0] < EDGE || b[2] >= w - EDGE) && ch >= RULE_RUN * h && 2 * ch >= 3 * medianH && cw * 3 <= ch
            val horizontal = (b[1] < EDGE || b[3] >= h - EDGE) && cw >= RULE_RUN * w && 2 * cw >= 3 * medianW && ch * 3 <= cw
            // A sliver cut by the cell border: thin, along the side it touches.
            val sliver = ((b[0] == 0 || b[2] == w - 1) && cw <= SLIVER && ch >= 3 * cw) ||
                ((b[1] == 0 || b[3] == h - 1) && ch <= SLIVER && cw >= 3 * ch)
            vertical || horizontal || sliver
        }
        if (remnant.none { it }) return
        for (i in px.indices) if (label[i] >= 0 && remnant[label[i]]) px[i] = 255
    }

    /** Growable int stack without boxing. */
    private class IntArrayStack {
        private var data = IntArray(256)
        var size = 0
            private set

        fun push(x: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = x
        }

        fun pop(): Int = data[--size]
    }

    /** Bilinear upscale of the [box] of [src] by [scale], with a white border of [PAD] px. */
    private fun scaledPadded(src: IntArray, srcWidth: Int, box: IntBox, scale: Double): Triple<IntArray, Int, Int> {
        val sw = box.width
        val sh = box.height
        val w = max(1, (sw * scale).roundToInt())
        val h = max(1, (sh * scale).roundToInt())
        val outW = w + 2 * PAD
        val outH = h + 2 * PAD
        val out = IntArray(outW * outH) { 255 }
        for (y in 0 until h) {
            val fy = ((y + 0.5) / scale - 0.5).coerceIn(0.0, (sh - 1).toDouble())
            val y0 = fy.toInt()
            val y1 = min(y0 + 1, sh - 1)
            val ty = fy - y0
            val r0 = (box.top + y0) * srcWidth + box.left
            val r1 = (box.top + y1) * srcWidth + box.left
            val orow = (y + PAD) * outW + PAD
            for (x in 0 until w) {
                val fx = ((x + 0.5) / scale - 0.5).coerceIn(0.0, (sw - 1).toDouble())
                val x0 = fx.toInt()
                val x1 = min(x0 + 1, sw - 1)
                val tx = fx - x0
                val top = src[r0 + x0] * (1 - tx) + src[r0 + x1] * tx
                val bot = src[r1 + x0] * (1 - tx) + src[r1 + x1] * tx
                out[orow + x] = (top * (1 - ty) + bot * ty + 0.5).toInt()
            }
        }
        return Triple(out, outW, outH)
    }
}
