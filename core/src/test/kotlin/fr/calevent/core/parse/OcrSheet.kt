package fr.calevent.core.parse

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Test helper producing ML Kit-like output: lines of words with boxes and confidences, laid out by
 * hand on an upright sheet, then distorted like a photo ([build]: tilt, quarter or half turn).
 * Glyph width is approximated as 0.55 × text height.
 */
internal class OcrSheet(private val width: Int, private val height: Int) {

    private class Word(val text: String, val left: Double, val cy: Double, val h: Double, val conf: Double) {
        val w: Double get() = max(1, text.length) * 0.55 * h
    }

    private val lines = ArrayList<MutableList<Word>>()

    /** Places [text] with its left edge at [x] and its vertical center at [y]. */
    fun text(text: String, x: Double, y: Double, h: Double = 14.0, conf: Double = 0.95): OcrSheet {
        val words = ArrayList<Word>()
        var cursor = x
        text.split(' ').filter { it.isNotEmpty() }.forEach { s ->
            val word = Word(s, cursor, y, h, conf)
            words += word
            cursor += word.w + 0.35 * h
        }
        lines += words
        return this
    }

    fun centered(text: String, cx: Double, y: Double, h: Double = 14.0, conf: Double = 0.95): OcrSheet =
        text(text, cx - textWidth(text, h) / 2, y, h, conf)

    fun right(text: String, rx: Double, y: Double, h: Double = 14.0, conf: Double = 0.95): OcrSheet =
        text(text, rx - textWidth(text, h), y, h, conf)

    /** Merges the last [n] lines into one, as OCR does with neighbouring table cells on a row. */
    fun mergeLast(n: Int): OcrSheet {
        val merged = lines.takeLast(n).flatten().sortedBy { it.left }.toMutableList()
        repeat(n) { lines.removeAt(lines.lastIndex) }
        lines += merged
        return this
    }

    private fun textWidth(text: String, h: Double): Double {
        val words = text.split(' ').filter { it.isNotEmpty() }
        return words.sumOf { max(1, it.length) * 0.55 * h } + (words.size - 1) * 0.35 * h
    }

    /**
     * [rotation]: clockwise quarter turns applied to the sheet (90 = text reads top to bottom).
     * [tiltDegrees]: rows go down to the right when positive. [withAngles]: report OCR angles.
     */
    fun build(rotation: Int = 0, tiltDegrees: Double = 0.0, withAngles: Boolean = true): RecognizedText {
        val w = width.toDouble()
        val h = height.toDouble()
        val t = Math.toRadians(tiltDegrees)
        fun place(x: Double, y: Double): Pair<Double, Double> {
            val dx = x - w / 2
            val dy = y - h / 2
            val tx = w / 2 + dx * cos(t) - dy * sin(t)
            val ty = h / 2 + dx * sin(t) + dy * cos(t)
            return when (rotation) {
                90 -> (h - ty) to tx
                180 -> (w - tx) to (h - ty)
                270 -> ty to (w - tx)
                else -> tx to ty
            }
        }
        fun boxOf(word: Word): Box {
            val corners = listOf(
                place(word.left, word.cy - word.h / 2), place(word.left + word.w, word.cy - word.h / 2),
                place(word.left, word.cy + word.h / 2), place(word.left + word.w, word.cy + word.h / 2),
            )
            return Box(corners.minOf { it.first }, corners.minOf { it.second }, corners.maxOf { it.first }, corners.maxOf { it.second })
        }
        var angle = (tiltDegrees + rotation) % 360.0
        if (angle > 180) angle -= 360
        val out = lines.filter { it.isNotEmpty() }.map { words ->
            val elements = words.map { TextElement(it.text, boxOf(it), it.conf) }
            TextLine(
                text = words.joinToString(" ") { it.text },
                box = elements.map { it.box }.reduce { a, b -> Box(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom)) },
                confidence = words.map { it.conf }.average(),
                angle = if (withAngles) angle else null,
                elements = elements,
            )
        }
        val (ow, oh) = if (rotation % 180 == 0) width to height else height to width
        return RecognizedText(ow, oh, out)
    }
}

/** Layout of a team table, in upright sheet pixels. */
internal data class TableSpec(
    val labelX: Double,
    val firstColX: Double,
    val pitch: Double,
    val headerY: Double,
    val headerLineGap: Double,
    val headerH: Double,
    val firstRowY: Double,
    val rowPitch: Double,
    val textH: Double,
    val subH: Double,
    /** Code tables: surname at the row center, first name below; otherwise one centered line. */
    val stackedNames: Boolean,
    /** Extra vertical shift per column (perspective). */
    val columnDy: (Int) -> Double = { 0.0 },
)

/**
 * Writes a team table: [headers] one entry per column, lines separated by '|'; [people] "Nom|Prénom"
 * or a single line; [cells] one list per person, lines separated by '|', "" for an empty cell.
 * [conf] gives the confidence of a cell's words.
 */
internal fun OcrSheet.teamTable(
    spec: TableSpec,
    cornerLabel: String?,
    headers: List<String>,
    people: List<String>,
    cells: List<List<String>>,
    conf: (row: Int, col: Int, line: Int) -> Double = { _, _, _ -> 0.95 },
): OcrSheet {
    cornerLabel?.let { text(it, spec.labelX, spec.headerY, spec.headerH) }
    headers.forEachIndexed { c, header ->
        header.split('|').forEachIndexed { i, line ->
            centered(line, spec.firstColX + c * spec.pitch, spec.headerY + i * spec.headerLineGap + spec.columnDy(c), spec.headerH)
        }
    }
    people.forEachIndexed { r, person ->
        val y = spec.firstRowY + r * spec.rowPitch
        val parts = person.split('|')
        if (spec.stackedNames && parts.size > 1) {
            text(parts[0], spec.labelX, y, spec.textH)
            text(parts[1], spec.labelX, y + 1.4 * spec.textH, spec.subH)
        } else if (person.isNotEmpty()) {
            text(parts.joinToString(" "), spec.labelX, y, spec.textH)
        }
        cells.getOrNull(r)?.forEachIndexed { c, cell ->
            if (cell.isEmpty()) return@forEachIndexed
            val cx = spec.firstColX + c * spec.pitch
            val cy = y + spec.columnDy(c)
            val lines = cell.split('|')
            if (lines.size == 1) centered(lines[0], cx, cy, spec.textH, conf(r, c, 0))
            else {
                centered(lines[0], cx, cy - 0.6 * spec.textH, spec.textH, conf(r, c, 0))
                centered(lines[1], cx, cy + 0.75 * spec.textH, spec.subH, conf(r, c, 1))
            }
        }
    }
    return this
}
