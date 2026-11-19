package fr.calevent.core.image

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OcrPreprocessTest {

    private class Img(val w: Int, val h: Int, bg: Int) {
        val px = IntArray(w * h) { bg }
        operator fun get(x: Int, y: Int) = px[y * w + x]
        fun rect(x0: Int, y0: Int, x1: Int, y1: Int, v: Int) {
            for (y in y0 until y1) for (x in x0 until x1) px[y * w + x] = v
        }
        fun run() = OcrPreprocess.process(px, w, h)
    }

    @Test
    fun `flat tinted cell becomes white whatever its tint`() {
        for (tint in intArrayOf(120, 180, 235)) {
            val out = Img(300, 200, tint).run()
            assertTrue(out.all { it == 255 }, "tint $tint")
        }
    }

    @Test
    fun `dark text on a tinted background keeps the text and whitens the background`() {
        val img = Img(300, 200, 170)
        // An "H": two bars and a crossbar, strokes of 4 px, 14 px high.
        img.rect(100, 90, 104, 104, 40)
        img.rect(112, 90, 116, 104, 40)
        img.rect(104, 96, 112, 99, 40)
        val out = img.run()
        assertTrue(out[95 * 300 + 102] < 60, "left bar kept")
        assertTrue(out[95 * 300 + 114] < 60, "right bar kept")
        assertTrue(out[97 * 300 + 108] < 60, "crossbar kept")
        assertEquals(255, out[20 * 300 + 20], "background far from the text")
        assertEquals(255, out[150 * 300 + 250], "background far from the text")
    }

    @Test
    fun `a long horizontal line is removed`() {
        val img = Img(400, 200, 220)
        img.rect(50, 100, 350, 102, 30)
        val out = img.run()
        assertTrue(out.all { it == 255 })
    }

    @Test
    fun `a long vertical line is removed`() {
        val img = Img(200, 400, 220)
        img.rect(100, 40, 102, 360, 30)
        val out = img.run()
        assertTrue(out.all { it == 255 })
    }

    @Test
    fun `short strokes are kept while the grid around them goes`() {
        val img = Img(400, 300, 210)
        img.rect(0, 150, 400, 152, 30) // grid line
        img.rect(200, 0, 202, 300, 30) // grid line
        img.rect(260, 60, 263, 74, 30) // 14 px stroke: shorter than the 24 px minimum
        val out = img.run()
        assertEquals(255, out[150 * 400 + 100])
        assertEquals(255, out[100 * 400 + 201])
        assertTrue(out[66 * 400 + 261] < 60)
    }

    @Test
    fun `a stroke is kept when its length is under the scaled threshold, removed above`() {
        val img = Img(1000, 700, 220) // long side 1000 -> lines of 60 px or more are removed
        img.rect(100, 100, 150, 102, 30) // 50 px
        img.rect(100, 200, 170, 202, 30) // 70 px
        val out = img.run()
        assertTrue(out[100 * 1000 + 125] < 100)
        assertEquals(255, out[200 * 1000 + 135])
    }

    @Test
    fun `input is not modified and output has the same size`() {
        val img = Img(120, 80, 200)
        img.rect(10, 10, 100, 12, 20)
        val copy = img.px.copyOf()
        val out = img.run()
        assertEquals(copy.toList(), img.px.toList())
        assertEquals(img.px.size, out.size)
        assertTrue(out.all { it in 0..255 })
    }

    @Test
    fun `tiny and degenerate images do not crash`() {
        for ((w, h) in listOf(1 to 1, 1 to 7, 7 to 1, 2 to 2, 3 to 5, 9 to 9, 20 to 3)) {
            val img = Img(w, h, 128)
            img.rect(0, 0, w / 2 + 1, h / 2 + 1, 10)
            val out = img.run()
            assertEquals(w * h, out.size)
            assertTrue(out.all { it in 0..255 })
        }
    }

    @Test
    fun `box radii approximate the requested gaussian`() {
        for (sigma in doubleArrayOf(5.0, 10.0, 20.0, 40.0)) {
            val variance = OcrPreprocess.boxRadii(sigma).sumOf { r -> val w = 2.0 * r + 1; (w * w - 1) / 12.0 }
            assertTrue(abs(variance - sigma * sigma) / (sigma * sigma) < 0.15, "sigma $sigma variance $variance")
        }
    }
}
