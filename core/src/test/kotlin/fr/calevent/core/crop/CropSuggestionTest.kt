package fr.calevent.core.crop

import fr.calevent.core.image.IntBox
import fr.calevent.core.image.Point
import fr.calevent.core.image.TableCell
import fr.calevent.core.image.TableGrid
import fr.calevent.core.parse.Box
import fr.calevent.core.parse.RecognizedText
import fr.calevent.core.parse.TextLine
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CropSuggestionTest {
    /** A 3 x 3 lattice covering the pixel rectangle ([x0], [y0])..([x1], [y1]). */
    private fun grid(x0: Double, y0: Double, x1: Double, y1: Double): TableGrid {
        val cells = (0 until 3).flatMap { row ->
            (0 until 3).map { col ->
                val l = x0 + (x1 - x0) * col / 3
                val r = x0 + (x1 - x0) * (col + 1) / 3
                val t = y0 + (y1 - y0) * row / 3
                val b = y0 + (y1 - y0) * (row + 1) / 3
                TableCell(
                    row, col, 1, 1, Point(l, t), Point(r, t), Point(r, b), Point(l, b),
                    IntBox(l.toInt() + 2, t.toInt() + 2, r.toInt() - 2, b.toInt() - 2),
                )
            }
        }
        return TableGrid(3, 3, cells)
    }

    private fun near(expected: Float, actual: Float) = assertTrue(abs(expected - actual) < 1e-4f, "expected $expected got $actual")

    @Test
    fun `grid bounds are the outer corners`() {
        val b = assertNotNull(CropSuggestion.gridBounds(grid(100.0, 300.0, 900.0, 800.0), 1000, 1000))
        near(0.1f, b.left); near(0.3f, b.top); near(0.9f, b.right); near(0.8f, b.bottom)
    }

    @Test
    fun `no text gives margin on every side with a larger top`() {
        val r = assertNotNull(CropSuggestion.fromGrid(grid(100.0, 300.0, 900.0, 800.0), 1000, 1000))
        near(0.08f, r.left); near(0.26f, r.top); near(0.92f, r.right); near(0.82f, r.bottom)
    }

    @Test
    fun `title text just above the grid raises the top`() {
        val text = NormalizedRect(0.1f, 0.2f, 0.9f, 0.7f)
        val r = assertNotNull(CropSuggestion.fromGrid(grid(100.0, 300.0, 900.0, 800.0), 1000, 1000, text))
        near(0.2f, r.top)
        near(0.08f, r.left) // sides still come from the grid
    }

    @Test
    fun `text far above the grid is not a title`() {
        val text = NormalizedRect(0.1f, 0.02f, 0.9f, 0.7f)
        val r = assertNotNull(CropSuggestion.fromGrid(grid(100.0, 300.0, 900.0, 800.0), 1000, 1000, text))
        near(0.26f, r.top)
    }

    @Test
    fun `text beside the grid is not a title`() {
        val text = NormalizedRect(0.0f, 0.2f, 0.2f, 0.28f)
        val r = assertNotNull(CropSuggestion.fromGrid(grid(100.0, 300.0, 900.0, 800.0), 1000, 1000, text))
        near(0.26f, r.top)
    }

    @Test
    fun `text starting inside the grid does not move the top`() {
        val text = NormalizedRect(0.1f, 0.4f, 0.9f, 0.7f)
        val r = assertNotNull(CropSuggestion.fromGrid(grid(100.0, 300.0, 900.0, 800.0), 1000, 1000, text))
        near(0.26f, r.top)
    }

    @Test
    fun `margins are clamped to the image`() {
        val r = assertNotNull(CropSuggestion.fromGrid(grid(10.0, 20.0, 600.0, 700.0), 1000, 1000))
        assertEquals(0f, r.left); assertEquals(0f, r.top)
        near(0.62f, r.right); near(0.72f, r.bottom)
    }

    @Test
    fun `tiny grid is rejected`() {
        assertNull(CropSuggestion.fromGrid(grid(400.0, 400.0, 500.0, 500.0), 1000, 1000))
    }

    @Test
    fun `grid filling the photo gives no crop`() {
        assertNull(CropSuggestion.fromGrid(grid(5.0, 5.0, 995.0, 995.0), 1000, 1000))
    }

    @Test
    fun `suggest falls back to the text bounds without a table`() {
        val text = text(List(4) { Box(200.0, 200.0 + 100 * it, 800.0, 230.0 + 100 * it) })
        val r = assertNotNull(CropSuggestion.suggest(null, text))
        near(0.17f, r.left); near(0.17f, r.top)
        assertNull(CropSuggestion.suggest(null, null))
    }

    @Test
    fun `suggest prefers the table over the text`() {
        val text = text(List(4) { Box(0.0, 100.0 * it, 500.0, 30.0 + 100 * it) })
        val r = assertNotNull(CropSuggestion.suggest(NormalizedRect(0.1f, 0.3f, 0.9f, 0.8f), text))
        near(0.08f, r.left); near(0.92f, r.right)
    }

    @Test
    fun `a title line just above the table raises the top, text above it does not`() {
        val lines = listOf(
            Box(300.0, 20.0, 700.0, 50.0), // header far above: not part of the title
            Box(300.0, 230.0, 700.0, 270.0), // title
            Box(150.0, 320.0, 400.0, 340.0), Box(500.0, 420.0, 800.0, 440.0),
        )
        val r = assertNotNull(CropSuggestion.suggest(NormalizedRect(0.1f, 0.3f, 0.9f, 0.8f), text(lines)))
        near(0.22f, r.top)
    }

    @Test
    fun `text beside the table is not a title`() {
        val lines = listOf(Box(0.0, 230.0, 80.0, 270.0), Box(150.0, 320.0, 400.0, 340.0), Box(500.0, 420.0, 800.0, 440.0))
        val r = assertNotNull(CropSuggestion.suggest(NormalizedRect(0.1f, 0.3f, 0.9f, 0.8f), text(lines)))
        near(0.26f, r.top)
    }

    private fun text(boxes: List<Box>) = RecognizedText(1000, 1000, boxes.map { b -> TextLine("Lun 12", b) })
}
