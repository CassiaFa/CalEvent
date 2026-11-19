package fr.calevent.core.image

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Cleans a grayscale photo of a printed planning before text recognition: evens out the lighting and tinted
 * cells, then erases the table grid so the OCR does not read ruled lines as characters (`I`, `l`, `|`).
 *
 * 1. Background division: `out = img / blur(img)`, then levels 40 %..100 % (below 0.4 -> black, above 1 -> white).
 *    A flat tinted cell becomes white whatever its tint; dark text stays dark.
 * 2. Grid removal: pixels darker than 60 % gray that belong to a horizontal or vertical dark run of at least
 *    [lineLengthRatio] of the long side are painted white (the mask is grown by 3 px to take the
 *    anti-aliased borders). Short strokes (letters, digits) are never that long, so they are kept.
 *
 * Pure Kotlin, O(pixels): separable box blur (three passes approximate a Gaussian) and run-length line detection.
 * Lengths scale with the image: [sigmaRatio] and [lineLengthRatio] are fractions of the long side
 * (1 % and 6 %, i.e. 20 px and 120 px on a 2000 px image).
 */
object OcrPreprocess {

    const val DEFAULT_SIGMA_RATIO = 0.01
    const val DEFAULT_LINE_LENGTH_RATIO = 0.06

    private const val LEVEL_LOW = 0.4
    private const val DARK_THRESHOLD = 154 // value <= 60 % of 255 (153), as ImageMagick -threshold 60%
    private const val MIN_LINE_LENGTH = 8

    /**
     * The mask is grown by 3 px on each side: the reference ImageMagick experiment used `-morphology Dilate
     * square:3`, whose number is a radius (a 7x7 window), and that is what made the recognition better.
     */
    private const val GROW_RADIUS = 3

    /**
     * @param gray `width * height` values in 0..255, row-major; not modified.
     * @return a new array of the same size and layout (0 = black, 255 = white).
     */
    fun process(
        gray: IntArray,
        width: Int,
        height: Int,
        sigmaRatio: Double = DEFAULT_SIGMA_RATIO,
        lineLengthRatio: Double = DEFAULT_LINE_LENGTH_RATIO,
    ): IntArray {
        require(width > 0 && height > 0 && gray.size == width * height) { "bad image size" }
        val longSide = max(width, height)
        val flat = divideByBackground(gray, width, height, sigmaRatio * longSide)
        val lineLength = max(MIN_LINE_LENGTH, (lineLengthRatio * longSide).roundToInt())
        eraseLines(flat, width, height, lineLength)
        return flat
    }

    // ---- 1. background division ---------------------------------------------------------------------------

    private fun divideByBackground(gray: IntArray, w: Int, h: Int, sigma: Double): IntArray {
        val blurred = FloatArray(w * h) { gray[it].toFloat() }
        val tmp = FloatArray(w * h)
        val radii = boxRadii(sigma)
        for (r in radii) {
            if (r <= 0) continue
            blurHorizontal(blurred, tmp, w, h, r)
            blurVertical(tmp, blurred, w, h, r)
        }
        return IntArray(w * h) { i ->
            val bg = blurred[i]
            val v = if (bg <= 0.5f) 1.0 else gray[i] / bg.toDouble()
            val level = (v - LEVEL_LOW) / (1.0 - LEVEL_LOW)
            (level.coerceIn(0.0, 1.0) * 255.0 + 0.5).toInt()
        }
    }

    /** Radii of three box blurs whose combined variance approximates a Gaussian of [sigma] (Kovesi's method). */
    internal fun boxRadii(sigma: Double): IntArray {
        if (sigma < 0.5) return intArrayOf(0, 0, 0)
        val n = 3
        val ideal = sqrt(12.0 * sigma * sigma / n + 1.0)
        var wl = ideal.toInt()
        if (wl % 2 == 0) wl--
        val wu = wl + 2
        val m = ((12.0 * sigma * sigma - n * wl * wl - 4.0 * n * wl - 3.0 * n) / (-4.0 * wl - 4.0)).roundToInt()
        return IntArray(n) { i -> ((if (i < m) wl else wu) - 1) / 2 }
    }

    /** Box blur of radius [r] along rows, edge pixels repeated. */
    private fun blurHorizontal(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val inv = 1.0 / (2 * r + 1)
        for (y in 0 until h) {
            val row = y * w
            var sum = 0.0
            for (k in -r..r) sum += src[row + min(max(k, 0), w - 1)]
            for (x in 0 until w) {
                dst[row + x] = (sum * inv).toFloat()
                sum += src[row + min(x + r + 1, w - 1)] - src[row + max(x - r, 0)]
            }
        }
    }

    /** Box blur of radius [r] along columns, edge pixels repeated; walks row by row to stay cache friendly. */
    private fun blurVertical(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val inv = 1.0 / (2 * r + 1)
        val sums = DoubleArray(w)
        for (k in -r..r) {
            val row = min(max(k, 0), h - 1) * w
            for (x in 0 until w) sums[x] += src[row + x]
        }
        for (y in 0 until h) {
            val row = y * w
            val add = min(y + r + 1, h - 1) * w
            val sub = max(y - r, 0) * w
            for (x in 0 until w) {
                dst[row + x] = (sums[x] * inv).toFloat()
                sums[x] += src[add + x] - src[sub + x]
            }
        }
    }

    // ---- 2. grid removal ----------------------------------------------------------------------------------

    /** Paints white (in place) the dark horizontal and vertical runs of at least [minLength] pixels, plus 1 px. */
    private fun eraseLines(img: IntArray, w: Int, h: Int, minLength: Int) {
        val mask = BooleanArray(w * h)

        // Horizontal runs.
        for (y in 0 until h) {
            val row = y * w
            var start = -1
            for (x in 0..w) {
                val dark = x < w && img[row + x] < DARK_THRESHOLD
                if (dark) {
                    if (start < 0) start = x
                } else if (start >= 0) {
                    if (x - start >= minLength) java.util.Arrays.fill(mask, row + start, row + x, true)
                    start = -1
                }
            }
        }

        // Vertical runs: start row of the current dark run in each column.
        val starts = IntArray(w) { -1 }
        for (y in 0..h) {
            val row = y * w
            for (x in 0 until w) {
                val dark = y < h && img[row + x] < DARK_THRESHOLD
                val s = starts[x]
                if (dark) {
                    if (s < 0) starts[x] = y
                } else if (s >= 0) {
                    if (y - s >= minLength) for (k in s until y) mask[k * w + x] = true
                    starts[x] = -1
                }
            }
        }

        // Grow the mask by GROW_RADIUS pixels in every direction (square window, separably), then paint white.
        val grown = growHorizontally(mask, w, h)
        growVertically(grown, w, h)
        for (i in img.indices) if (grown[i]) img[i] = 255
    }

    /** Copy of [mask] where every pixel within [GROW_RADIUS] of a set pixel of the same row is set. */
    private fun growHorizontally(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val out = mask.copyOf()
        for (y in 0 until h) {
            val row = y * w
            var last = Int.MIN_VALUE / 2
            for (x in 0 until w) {
                if (mask[row + x]) last = x else if (x - last <= GROW_RADIUS) out[row + x] = true
            }
            last = Int.MAX_VALUE / 2
            for (x in w - 1 downTo 0) {
                if (mask[row + x]) last = x else if (last - x <= GROW_RADIUS) out[row + x] = true
            }
        }
        return out
    }

    /** Same along columns, in place on [m] (reads are made on a snapshot of the original column state). */
    private fun growVertically(m: BooleanArray, w: Int, h: Int) {
        val src = m.copyOf()
        val last = IntArray(w) { Int.MIN_VALUE / 2 }
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if (src[row + x]) last[x] = y else if (y - last[x] <= GROW_RADIUS) m[row + x] = true
            }
        }
        java.util.Arrays.fill(last, Int.MAX_VALUE / 2)
        for (y in h - 1 downTo 0) {
            val row = y * w
            for (x in 0 until w) {
                if (src[row + x]) last[x] = y else if (last[x] - y <= GROW_RADIUS) m[row + x] = true
            }
        }
    }
}
