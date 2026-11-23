package fr.calevent.core.crop

import fr.calevent.core.image.IntBox
import fr.calevent.core.image.Point
import fr.calevent.core.image.TableCell
import fr.calevent.core.image.TableGrid
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
    fun `suggest falls back to the text bounds without a grid`() {
        val text = NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f)
        assertEquals(text, CropSuggestion.suggest(null, 1000, 1000, text))
        assertNull(CropSuggestion.suggest(null, 1000, 1000, null))
    }

    @Test
    fun `suggest falls back to the text bounds when the grid is rejected`() {
        val text = NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f)
        assertEquals(text, CropSuggestion.suggest(grid(400.0, 400.0, 500.0, 500.0), 1000, 1000, text))
    }

    @Test
    fun `suggest prefers the grid over the text`() {
        val text = NormalizedRect(0.0f, 0.0f, 0.5f, 0.5f)
        val r = assertNotNull(CropSuggestion.suggest(grid(100.0, 300.0, 900.0, 800.0), 1000, 1000, text))
        near(0.08f, r.left)
    }
}
