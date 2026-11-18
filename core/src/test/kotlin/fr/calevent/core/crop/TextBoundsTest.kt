package fr.calevent.core.crop

import fr.calevent.core.parse.Box
import fr.calevent.core.parse.RecognizedText
import fr.calevent.core.parse.TextLine
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextBoundsTest {
    private fun line(l: Double, t: Double, r: Double, b: Double, text: String = "Lundi 12", conf: Double? = 0.9) =
        TextLine(text, Box(l, t, r, b), conf)

    private fun text(vararg lines: TextLine) = RecognizedText(1000, 1000, lines.toList())

    private fun near(expected: Float, actual: Float) = assertTrue(abs(expected - actual) < 1e-4f, "expected $expected got $actual")

    private val block = arrayOf(
        line(200.0, 300.0, 500.0, 330.0), line(200.0, 340.0, 700.0, 370.0), line(300.0, 380.0, 600.0, 410.0),
    )

    @Test
    fun `union of lines plus margin`() {
        val r = assertNotNull(TextBounds.suggestCrop(text(*block)))
        near(0.17f, r.left); near(0.27f, r.top); near(0.73f, r.right); near(0.44f, r.bottom)
    }

    @Test
    fun `far footer line is dropped`() {
        val r = assertNotNull(TextBounds.suggestCrop(text(*block, line(200.0, 950.0, 400.0, 980.0, "Page 1 sur 2"))))
        assertTrue(r.bottom < 0.5f)
    }

    @Test
    fun `main cluster wins over a small distant group`() {
        val r = assertNotNull(
            TextBounds.suggestCrop(
                text(
                    line(100.0, 100.0, 600.0, 130.0), line(100.0, 140.0, 600.0, 170.0), line(100.0, 180.0, 600.0, 210.0),
                    line(100.0, 220.0, 600.0, 250.0), line(850.0, 800.0, 950.0, 830.0, "Notes"),
                ),
            ),
        )
        assertTrue(r.right < 0.7f && r.bottom < 0.3f)
    }

    @Test
    fun `low confidence short line is ignored`() {
        val r = assertNotNull(TextBounds.suggestCrop(text(*block, line(20.0, 330.0, 40.0, 360.0, "x", conf = 0.2))))
        near(0.17f, r.left)
    }

    @Test
    fun `no lines gives null`() {
        assertNull(TextBounds.suggestCrop(text()))
    }

    @Test
    fun `too few lines gives null`() {
        assertNull(TextBounds.suggestCrop(text(block[0], block[1])))
    }

    @Test
    fun `margin is clamped to the image`() {
        val r = assertNotNull(
            TextBounds.suggestCrop(text(line(5.0, 5.0, 600.0, 35.0), line(5.0, 45.0, 600.0, 75.0), line(5.0, 85.0, 600.0, 115.0))),
        )
        assertEquals(0f, r.left); assertEquals(0f, r.top)
        near(0.63f, r.right)
    }

    @Test
    fun `text covering nearly everything gives null`() {
        assertNull(
            TextBounds.suggestCrop(
                text(line(10.0, 10.0, 990.0, 300.0), line(10.0, 310.0, 990.0, 600.0), line(10.0, 610.0, 990.0, 990.0)),
            ),
        )
    }
}
