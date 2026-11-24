package fr.calevent.core.crop

import fr.calevent.core.parse.Box
import fr.calevent.core.parse.RecognizedText
import fr.calevent.core.parse.TextLine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Synthetic photos (gray paper, tinted cells, textures) for [TableFrame]. */
class TableFrameTest {

    private class Img(val w: Int, val h: Int, bg: Int = 200) {
        val px = IntArray(w * h) { bg }

        fun rect(x0: Int, y0: Int, x1: Int, y1: Int, v: Int) {
            for (y in max(0, y0) until min(h, y1)) for (x in max(0, x0) until min(w, x1)) px[y * w + x] = v
        }

        /** A word: a few dark strokes 12 px high. */
        fun word(x: Int, y: Int) {
            for (k in 0 until 5) rect(x + k * 10, y, x + k * 10 + 3, y + 12, 60)
        }
    }

    /** Table of [rows] x [cols] cells of [cw] x [ch] from ([x0], [y0]) with light rules ([rule] gray, 2 px). */
    private fun table(img: Img, x0: Int, y0: Int, rows: Int, cols: Int, cw: Int, ch: Int, rule: Int = 150) {
        for (i in 0..rows) img.rect(x0, y0 + i * ch - 1, x0 + cols * cw + 1, y0 + i * ch + 1, rule)
        for (j in 0..cols) img.rect(x0 + j * cw - 1, y0, x0 + j * cw + 1, y0 + rows * ch + 1, rule)
    }

    /** Text lines in the cells of the table, in the same pixel coordinates as the image. */
    private fun cellText(img: Img, x0: Int, y0: Int, rows: Int, cols: Int, cw: Int, ch: Int): List<TextLine> =
        (0 until rows).flatMap { i ->
            (0 until cols).map { j ->
                val x = x0 + j * cw + cw / 4
                val y = y0 + i * ch + ch / 2 - 6
                img.word(x, y)
                TextLine("08:00", Box(x.toDouble(), y.toDouble(), x + 45.0, y + 12.0))
            }
        }

    private fun assertFrame(expected: NormalizedRect, actual: NormalizedRect?, tol: Float = 0.02f) {
        val a = assertNotNull(actual, "no frame")
        val ok = abs(a.left - expected.left) <= tol && abs(a.top - expected.top) <= tol &&
            abs(a.right - expected.right) <= tol && abs(a.bottom - expected.bottom) <= tol
        assertTrue(ok, "expected $expected got $a")
    }

    @Test
    fun `table with coloured cells gives the whole table, not one cell`() {
        val img = Img(1600, 1200)
        val (x0, y0, rows, cols, cw, ch) = listOf(100, 150, 9, 6, 230, 100)
        table(img, x0, y0, rows, cols, cw, ch)
        // Tinted cells, a light gap of 2 px around each one, tints only 20..45 levels below the paper.
        val tints = intArrayOf(160, 175, 168, 155, 180)
        for (i in 0 until rows) for (j in 0 until cols) if ((i * 7 + j * 3) % 5 < 3) {
            img.rect(x0 + j * cw + 2, y0 + i * ch + 2, x0 + (j + 1) * cw - 2, y0 + (i + 1) * ch - 2, tints[(i + j) % tints.size])
        }
        // One strongly coloured cell in the middle, with a dark border: the old detector framed it alone.
        img.rect(x0 + 2 * cw, y0 + 4 * ch, x0 + 3 * cw, y0 + 5 * ch, 40)
        img.rect(x0 + 2 * cw + 4, y0 + 4 * ch + 4, x0 + 3 * cw - 4, y0 + 5 * ch - 4, 120)
        val text = RecognizedText(img.w, img.h, cellText(img, x0, y0, rows, cols, cw, ch))
        val frame = TableFrame.find(img.px, img.w, img.h, text)
        assertFrame(NormalizedRect(x0 / 1600f, y0 / 1200f, (x0 + cols * cw) / 1600f, (y0 + rows * ch) / 1200f), frame)
    }

    @Test
    fun `wood texture below the sheet is not taken for the table`() {
        val img = Img(1200, 1600)
        // Wood: dark planks with grain every 6 px and seams every 150 px, over the bottom third.
        img.rect(0, 1050, 1200, 1600, 110)
        for (y in 1050 until 1600 step 6) img.rect(0, y, 1200, y + 2, 70)
        for (x in 0 until 1200 step 150) img.rect(x, 1050, x + 4, 1600, 40)
        val (x0, y0, rows, cols, cw, ch) = listOf(100, 100, 12, 5, 200, 70)
        table(img, x0, y0, rows, cols, cw, ch)
        val lines = cellText(img, x0, y0, rows, cols, cw, ch)
        val expected = NormalizedRect(x0 / 1200f, y0 / 1600f, (x0 + cols * cw) / 1200f, (y0 + rows * ch) / 1600f)
        assertFrame(expected, TableFrame.find(img.px, img.w, img.h, RecognizedText(img.w, img.h, lines)))
        // Without any recognised text the texture is still recognised by its packed lines.
        assertFrame(expected, TableFrame.find(img.px, img.w, img.h, null))
    }

    @Test
    fun `wood alone gives no frame`() {
        val img = Img(1200, 900, bg = 110)
        for (y in 0 until 900 step 6) img.rect(0, y, 1200, y + 2, 70)
        for (x in 0 until 1200 step 150) img.rect(x, 0, x + 4, 900, 40)
        assertNull(TableFrame.find(img.px, img.w, img.h, null))
    }

    @Test
    fun `roster split by thick week separators gives the whole roster`() {
        val img = Img(1600, 1200)
        val x0 = 60
        val y0 = 300
        val cw = 48
        val ch = 50
        val rows = 10
        // Four blocks of 7 days, separated by 14 px dark bands; the rules of a block stop at the bands.
        var x = x0
        for (b in 0 until 4) {
            table(img, x, y0, rows, 7, cw, ch)
            cellText(img, x, y0, rows, 7, cw, ch)
            x += 7 * cw
            if (b < 3) {
                img.rect(x + 4, y0, x + 18, y0 + rows * ch + 1, 30)
                x += 22
            }
        }
        val frame = TableFrame.find(img.px, img.w, img.h, null)
        assertFrame(NormalizedRect(x0 / 1600f, y0 / 1200f, x / 1600f, (y0 + rows * ch) / 1200f), frame)
    }

    @Test
    fun `separate blocks side by side are merged into one frame`() {
        val img = Img(1600, 1200)
        table(img, 100, 200, 8, 5, 130, 80)
        table(img, 100 + 5 * 130 + 30, 200, 8, 5, 130, 80) // 30 px of blank paper between them
        val frame = TableFrame.find(img.px, img.w, img.h, null)
        assertFrame(NormalizedRect(100 / 1600f, 200 / 1200f, (130 + 10 * 130 + 30) / 1600f, (200 + 8 * 80) / 1200f), frame)
    }

    @Test
    fun `a lone coloured block or a frame of four lines is not a table`() {
        val img = Img(1600, 1200)
        img.rect(500, 400, 900, 700, 120)
        img.rect(100, 100, 1500, 103, 60); img.rect(100, 1000, 1500, 1003, 60)
        img.rect(100, 100, 103, 1003, 60); img.rect(1497, 100, 1500, 1003, 60)
        assertNull(TableFrame.find(img.px, img.w, img.h, null))
    }

    @Test
    fun `a grid with no text inside is rejected when the photo has text elsewhere`() {
        val img = Img(1600, 1200)
        table(img, 100, 100, 6, 6, 100, 80)
        val lines = List(5) { TextLine("Lundi 12", Box(900.0, 200.0 + 60 * it, 1300.0, 220.0 + 60 * it)) }
        assertNull(TableFrame.find(img.px, img.w, img.h, RecognizedText(img.w, img.h, lines)))
    }

    private operator fun <T> List<T>.component6(): T = this[5]
}
