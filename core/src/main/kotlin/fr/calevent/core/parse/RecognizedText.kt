package fr.calevent.core.parse

import kotlin.math.max
import kotlin.math.min

/** Axis-aligned rectangle in image pixels, y pointing down. */
data class Box(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val centerX: Double get() = (left + right) / 2
    val centerY: Double get() = (top + bottom) / 2

    infix fun union(other: Box): Box = Box(
        min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom),
    )
}

/** One word as returned by the OCR engine. [confidence] is 0..1 when the engine provides it. */
data class TextElement(val text: String, val box: Box, val confidence: Double? = null)

/**
 * One line of recognized text. [angle] is the rotation reported by the OCR engine, in degrees,
 * when available (ML Kit: `Text.Line.getAngle()`).
 */
data class TextLine(
    val text: String,
    val box: Box,
    val confidence: Double? = null,
    val angle: Double? = null,
    val elements: List<TextElement> = emptyList(),
)

/**
 * OCR output independent of the engine. Coordinates are pixels of an image of
 * [width] x [height], as seen by the engine (after any rotation given to it).
 */
data class RecognizedText(val width: Int, val height: Int, val lines: List<TextLine>)
