package fr.calevent.core.image

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GridDetectorTest {

    private class Img(val w: Int, val h: Int, bg: Int = 255) {
        val px = IntArray(w * h) { bg }

        fun rect(x0: Int, y0: Int, x1: Int, y1: Int, v: Int) {
            for (y in max(0, y0) until min(h, y1)) for (x in max(0, x0) until min(w, x1)) px[y * w + x] = v
        }

        /** Segment of the given thickness (pixels whose center is within half of it). */
        fun line(x0: Double, y0: Double, x1: Double, y1: Double, thick: Double, v: Int) {
            val len = hypot(x1 - x0, y1 - y0)
            val r = thick / 2
            for (y in max(0, (min(y0, y1) - r - 1).toInt()) until min(h, (max(y0, y1) + r + 2).toInt()))
                for (x in max(0, (min(x0, x1) - r - 1).toInt()) until min(w, (max(x0, x1) + r + 2).toInt())) {
                    val t = (((x - x0) * (x1 - x0) + (y - y0) * (y1 - y0)) / (len * len)).coerceIn(0.0, 1.0)
                    if (hypot(x - (x0 + t * (x1 - x0)), y - (y0 + t * (y1 - y0))) <= r) px[y * w + x] = v
                }
        }

        /** A small "H" (14 px high), standing for a word. */
        fun glyph(x: Int, y: Int, v: Int = 30) {
            rect(x, y, x + 4, y + 14, v)
            rect(x + 12, y, x + 16, y + 14, v)
            rect(x + 4, y + 6, x + 12, y + 9, v)
        }
    }

    /**
     * Grid of [rows] x [cols] cells of [cw] x [ch] px from ([x0], [y0]), rotated by [degrees] around its
     * top-left corner. [skip] leaves out pieces of lines: ("v" or "h", line index, cell index along it).
     */
    private fun grid(
        img: Img, rows: Int, cols: Int, x0: Double = 100.0, y0: Double = 100.0, cw: Double = 150.0, ch: Double = 100.0,
        degrees: Double = 0.0, thick: Double = 3.0, v: Int = 30, skip: Set<Triple<String, Int, Int>> = emptySet(),
    ) {
        val a = degrees * PI / 180
        fun p(gx: Double, gy: Double) = Pair(x0 + gx * cos(a) - gy * sin(a), y0 + gx * sin(a) + gy * cos(a))
        for (i in 0..rows) for (j in 0 until cols) {
            if (Triple("h", i, j) in skip) continue
            val (ax, ay) = p(j * cw, i * ch)
            val (bx, by) = p((j + 1) * cw, i * ch)
            img.line(ax, ay, bx, by, thick, v)
        }
        for (j in 0..cols) for (i in 0 until rows) {
            if (Triple("v", j, i) in skip) continue
            val (ax, ay) = p(j * cw, i * ch)
            val (bx, by) = p(j * cw, (i + 1) * ch)
            img.line(ax, ay, bx, by, thick, v)
        }
    }

    private fun detect(img: Img) = GridDetector.detect(img.px, img.w, img.h)

    @Test
    fun `straight grid gives every cell with its inner box between the lines`() {
        val img = Img(1200, 900)
        grid(img, rows = 5, cols = 6)
        val g = assertNotNull(detect(img))
        assertEquals(5, g.rows)
        assertEquals(6, g.cols)
        assertEquals(30, g.cells.size)
        val c = g.cells.single { it.row == 2 && it.col == 3 }
        assertEquals(1, c.rowSpan)
        assertEquals(1, c.colSpan)
        // Cell (2, 3) spans x 550..700, y 300..400; its inner box is inside, a few pixels off the lines.
        assertTrue(c.inner.left in 552..558 && c.inner.right in 692..698, "x ${c.inner}")
        assertTrue(c.inner.top in 302..308 && c.inner.bottom in 392..398, "y ${c.inner}")
        assertTrue(abs(c.topLeft.x - 550) < 2 && abs(c.topLeft.y - 300) < 2, "corner ${c.topLeft}")
    }

    @Test
    fun `slightly tilted grid is found with corners on the tilted lines`() {
        for (deg in doubleArrayOf(-2.0, 1.5)) {
            val img = Img(1200, 900)
            grid(img, rows = 5, cols = 6, y0 = 150.0, degrees = deg)
            val g = assertNotNull(detect(img), "tilt $deg")
            assertEquals(5, g.rows, "tilt $deg")
            assertEquals(6, g.cols, "tilt $deg")
            assertEquals(30, g.cells.size, "tilt $deg")
            val a = deg * PI / 180
            val c = g.cells.single { it.row == 4 && it.col == 5 }
            // Bottom-right corner of the grid: (900, 500) in grid coordinates.
            val ex = 100 + 900 * cos(a) - 500 * sin(a)
            val ey = 150 + 900 * sin(a) + 500 * cos(a)
            assertTrue(abs(c.bottomRight.x - ex) < 3 && abs(c.bottomRight.y - ey) < 3, "tilt $deg: ${c.bottomRight} vs $ex,$ey")
        }
    }

    @Test
    fun `light rules on white are found`() {
        val img = Img(1200, 900)
        grid(img, rows = 4, cols = 5, v = 185, thick = 2.0)
        val g = assertNotNull(detect(img))
        assertEquals(4, g.rows)
        assertEquals(5, g.cols)
    }

    @Test
    fun `a missing piece of rule merges the two cells it separated, a short gap does not`() {
        val img = Img(1200, 900)
        // Vertical line 3 missing along row 1: cells (1, 2) and (1, 3) are one cell.
        grid(img, rows = 5, cols = 6, skip = setOf(Triple("v", 3, 1)))
        // A 20 px gap in horizontal line 4 under column 1: still two cells.
        img.rect(270, 495, 290, 506, 255)
        val g = assertNotNull(detect(img))
        assertEquals(5, g.rows)
        assertEquals(6, g.cols)
        assertEquals(29, g.cells.size)
        val merged = g.cells.single { it.row == 1 && it.col == 2 }
        assertEquals(2, merged.colSpan)
        assertEquals(1, merged.rowSpan)
        assertTrue(g.cells.any { it.row == 3 && it.col == 1 } && g.cells.any { it.row == 4 && it.col == 1 })
    }

    @Test
    fun `a row without vertical rules becomes one wide cell`() {
        val img = Img(1200, 900)
        grid(img, rows = 5, cols = 6, skip = (1 until 6).map { Triple("v", it, 2) }.toSet())
        val g = assertNotNull(detect(img))
        val wide = g.cells.single { it.row == 2 }
        assertEquals(6, wide.colSpan)
        assertEquals(1 + 4 * 6, g.cells.size)
    }

    @Test
    fun `a title row whose top rule crosses only the frame stays outside the grid`() {
        val img = Img(1200, 900)
        grid(img, rows = 5, cols = 6, skip = (1 until 6).map { Triple("v", it, 0) }.toSet())
        val g = assertNotNull(detect(img))
        assertEquals(4, g.rows)
        assertTrue(g.cells.minOf { it.topLeft.y } > 190, "grid starts under the title row")
    }

    @Test
    fun `boxes inside cells are not taken for rules`() {
        val img = Img(1200, 900)
        grid(img, rows = 5, cols = 6)
        // Tinted event boxes inside some cells of row 2 (wall calendar), 10 px from the rules.
        for (j in intArrayOf(1, 2, 4)) img.rect(100 + j * 150 + 10, 330, 100 + (j + 1) * 150 - 10, 350, 170)
        val g = assertNotNull(detect(img))
        assertEquals(5, g.rows)
        assertEquals(30, g.cells.size)
    }

    @Test
    fun `no grid without rules`() {
        val img = Img(1200, 900)
        for (r in 0 until 10) for (k in 0 until 12) img.glyph(100 + k * 80, 100 + r * 60)
        assertNull(detect(img))
    }

    @Test
    fun `two lines and a frame are not a table`() {
        val img = Img(1200, 900)
        grid(img, rows = 2, cols = 2, cw = 400.0, ch = 300.0)
        assertNull(detect(img))
    }

    @Test
    fun `a filled dark band is not a rule but its edges are`() {
        val img = Img(1200, 900)
        grid(img, rows = 5, cols = 6, y0 = 160.0)
        // A 60 px dark title band right above the table: its lower edge is the table's top line.
        img.rect(100, 100, 1000, 160, 40)
        val g = assertNotNull(detect(img))
        assertEquals(6, g.cols)
        assertTrue(g.rows in 5..6, "rows ${g.rows}")
    }

    @Test
    fun `preprocessing finds the grid before erasing it`() {
        val img = Img(1200, 900)
        grid(img, rows = 5, cols = 6)
        img.glyph(560, 340)
        val prepared = OcrPreprocess.prepare(img.px, img.w, img.h)
        val g = assertNotNull(prepared.grid)
        assertEquals(30, g.cells.size)
        assertEquals(255, prepared.pixels[300 * 1200 + 600], "rule erased")
        assertTrue(prepared.pixels[345 * 1200 + 562] < 100, "glyph kept")
    }
}
