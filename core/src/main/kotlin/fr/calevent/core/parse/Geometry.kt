package fr.calevent.core.parse

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A piece of text the parser works on: one OCR line, or part of it when the line spans several
 * table cells (words far apart). [box] is in upright, deskewed working coordinates;
 * [source] is the same area in the original image coordinates (used for [fr.calevent.core.Region]).
 */
internal data class Token(
    val text: String,
    val box: Box,
    val source: Box,
    val confidence: Double,
)

/** Assumed confidence when the OCR engine gives none. */
internal const val DEFAULT_OCR_CONFIDENCE = 0.9

/** Tokens below this confidence are treated as noise (show-through, smudges). */
internal const val MIN_TOKEN_CONFIDENCE = 0.35

/** Words further apart than this many line heights belong to different cells. */
private const val CELL_GAP_IN_HEIGHTS = 1.5

/** Rotates [this] clockwise by a quarter-turn multiple inside an image of [w] x [h]. */
internal fun Box.rotateCw(degrees: Int, w: Double, h: Double): Box = when (degrees) {
    90 -> Box(h - bottom, left, h - top, right)
    180 -> Box(w - right, h - bottom, w - left, h - top)
    270 -> Box(top, w - right, bottom, w - left)
    else -> this
}

/** Image size after a clockwise rotation by [degrees]. */
internal fun rotatedSize(text: RecognizedText, degrees: Int): Pair<Double, Double> =
    if (degrees % 180 == 0) text.width.toDouble() to text.height.toDouble()
    else text.height.toDouble() to text.width.toDouble()

/** Quarter turn (0..3) closest to an OCR angle in degrees. */
private fun quarterOf(angle: Double): Int = ((Math.round(angle / 90.0) % 4 + 4) % 4).toInt()

/**
 * Clockwise rotations worth trying to read the text upright. The OCR angle only tells
 * "none / quarter / half turn" reliably (its sign convention is not trusted), so a quarter turn
 * yields both directions and the caller keeps the one that parses best.
 */
internal fun candidateRotations(text: RecognizedText): List<Int> {
    val lines = text.lines.filter { it.text.trim().length >= 2 }
    if (lines.isEmpty()) return listOf(0)
    val vertical = lines.count { it.box.height > 1.5 * it.box.width } * 2 > lines.size
    val angles = lines.mapNotNull { it.angle }
    if (angles.size * 2 >= lines.size) {
        val quarter = angles.groupingBy(::quarterOf).eachCount().maxBy { it.value }.key
        return when {
            quarter == 1 || quarter == 3 || vertical -> listOf(90, 270)
            quarter == 2 -> listOf(180)
            else -> listOf(0)
        }
    }
    return if (vertical) listOf(90, 270) else listOf(0, 180)
}

/** Residual tilt reported by the OCR engine once quarter turns are removed, in degrees. */
internal fun ocrTiltDegrees(text: RecognizedText): Double {
    val residuals = text.lines.mapNotNull { it.angle }.map { a ->
        val r = a - quarterOf(a) * 90.0
        ((r + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
    }
    return if (residuals.isEmpty()) 0.0 else abs(residuals.median())
}

/**
 * Splits OCR lines into tokens in the frame rotated clockwise by [rotation]. Words separated by a
 * wide gap become separate tokens (OCR engines often merge neighbouring table cells into a line).
 */
internal fun tokenize(text: RecognizedText, rotation: Int): List<Token> {
    val w = text.width.toDouble()
    val h = text.height.toDouble()
    val tokens = ArrayList<Token>()
    for (line in text.lines) {
        val words = line.elements.ifEmpty { listOf(TextElement(line.text, line.box, line.confidence)) }
        val placed = words.map { it to it.box.rotateCw(rotation, w, h) }.sortedBy { it.second.left }
        val lineHeight = placed.map { it.second.height }.median()
        var group = ArrayList<Pair<TextElement, Box>>()
        fun flush() {
            if (group.isEmpty()) return
            val conf = group.map { it.first.confidence ?: line.confidence ?: DEFAULT_OCR_CONFIDENCE }.average()
            tokens += Token(
                text = group.joinToString(" ") { it.first.text.trim() }.trim(),
                box = group.map { it.second }.reduce(Box::union),
                source = group.map { it.first.box }.reduce(Box::union),
                confidence = conf.coerceIn(0.0, 1.0),
            )
            group = ArrayList()
        }
        for (p in placed) {
            val last = group.lastOrNull()
            if (last != null && p.second.left - last.second.right > CELL_GAP_IN_HEIGHTS * lineHeight) flush()
            group += p
        }
        flush()
    }
    return tokens.filter { t -> t.confidence >= MIN_TOKEN_CONFIDENCE && t.text.any { it.isLetterOrDigit() } }
}

/**
 * Dominant slope of the text rows, in degrees (positive: rows go down to the right).
 * Mode of the angles between tokens and the tokens to their right; works with or without OCR angles.
 */
internal fun estimateSkewDegrees(tokens: List<Token>, imageWidth: Double): Double {
    if (tokens.size < 4) return 0.0
    val sorted = tokens.sortedBy { it.box.centerX }
    val maxDx = imageWidth * 0.3
    val angles = ArrayList<Double>()
    for (i in sorted.indices) {
        val a = sorted[i].box
        for (j in i + 1 until sorted.size) {
            val b = sorted[j].box
            val dx = b.centerX - a.centerX
            if (dx > maxDx) break
            if (dx < 2 * max(a.height, b.height)) continue
            val deg = Math.toDegrees(atan2(b.centerY - a.centerY, dx))
            if (abs(deg) <= 20.0) angles += deg
        }
    }
    if (angles.size < 6) return 0.0
    val bins = IntArray(81) // 0.5 degree bins over -20..20
    angles.forEach { bins[((it + 20.0) * 2).roundToInt().coerceIn(0, 80)]++ }
    val smoothed = (0..80).map { k -> (k - 1..k + 1).sumOf { bins.getOrElse(it) { 0 } } }
    val mode = smoothed.indices.maxBy { smoothed[it] } / 2.0 - 20.0
    val near = angles.filter { abs(it - mode) <= 1.0 }
    return if (near.isEmpty()) mode else near.average()
}

/** Rotates tokens by -[skewDegrees] around ([cx], [cy]) so that text rows become horizontal. */
internal fun deskew(tokens: List<Token>, skewDegrees: Double, cx: Double, cy: Double): List<Token> {
    if (abs(skewDegrees) < 0.3) return tokens
    val t = Math.toRadians(-skewDegrees)
    val c = cos(t)
    val s = sin(t)
    val ac = abs(c)
    val asn = abs(s)
    val denom = ac * ac - asn * asn
    return tokens.map { tok ->
        val b = tok.box
        val x = cx + (b.centerX - cx) * c - (b.centerY - cy) * s
        val y = cy + (b.centerX - cx) * s + (b.centerY - cy) * c
        // Size of the text before tilt, recovered from its axis-aligned bounding box.
        var w = (b.width * ac - b.height * asn) / denom
        var h = (b.height * ac - b.width * asn) / denom
        if (w <= 0 || h <= b.height * 0.3) { w = b.width; h = b.height * ac.coerceAtLeast(0.5) }
        tok.copy(box = Box(x - w / 2, y - h / 2, x + w / 2, y + h / 2))
    }
}

/** Groups items whose [key] values are within [tolerance] of the running group mean. */
internal fun <T> clusterBy(items: List<T>, tolerance: Double, key: (T) -> Double): List<List<T>> {
    val result = ArrayList<MutableList<T>>()
    var mean = 0.0
    for (item in items.sortedBy(key)) {
        val k = key(item)
        val current = result.lastOrNull()
        if (current != null && k - mean <= tolerance) {
            current += item
            mean += (k - mean) / current.size
        } else {
            result += mutableListOf(item)
            mean = k
        }
    }
    return result
}

internal fun List<Double>.median(): Double {
    if (isEmpty()) return 0.0
    val s = sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}

/** Union of the source boxes, as fractions of the original image. */
internal fun regionOf(tokens: List<Token>, width: Int, height: Int): fr.calevent.core.Region? {
    if (tokens.isEmpty() || width <= 0 || height <= 0) return null
    val b = tokens.map { it.source }.reduce(Box::union)
    fun fx(v: Double) = (v / width).coerceIn(0.0, 1.0)
    fun fy(v: Double) = (v / height).coerceIn(0.0, 1.0)
    return fr.calevent.core.Region(fx(b.left), fy(b.top), fx(b.right), fy(b.bottom))
}
