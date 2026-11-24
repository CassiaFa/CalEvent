package fr.calevent.core.image

import fr.calevent.core.Region
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoZonesTest {
    private fun near(e: Double, a: Double) = assertTrue(abs(e - a) < 1e-6, "expected $e got $a")

    /** A 4 x 3 lattice of cells over the whole 0..1 square. */
    private val cells = (0 until 3).flatMap { r -> (0 until 4).map { c -> Region(c / 4.0, r / 3.0, (c + 1) / 4.0, (r + 1) / 3.0) } }

    @Test
    fun `text inside a cell gets the whole cell, even at the bottom of a tall cell`() {
        val text = Region(0.30, 0.60, 0.40, 0.64) // second column, second row, near its bottom
        assertEquals(Region(0.25, 1 / 3.0, 0.5, 2 / 3.0), PhotoZones.zone(text, cells, 1000, 1000))
    }

    @Test
    fun `cells covering much of the photo are not used`() {
        val big = listOf(Region(0.0, 0.0, 0.6, 0.6))
        val text = Region(0.30, 0.30, 0.40, 0.33)
        val z = PhotoZones.zone(text, big, 1000, 1000)
        assertTrue(z.right - z.left < 0.2)
    }

    @Test
    fun `text straddling two cells is padded instead`() {
        val text = Region(0.20, 0.10, 0.32, 0.13) // across the first vertical rule
        val z = PhotoZones.zone(text, cells, 1000, 1000)
        assertTrue(z.left < 0.20 && z.right > 0.32 && z.right < 0.4)
    }

    @Test
    fun `without a grid the text box is padded by a share of its height`() {
        val z = PhotoZones.zone(Region(0.40, 0.50, 0.50, 0.52), emptyList(), 1000, 1000)
        // Text 20 px high: 0.6 x 20 + 4 = 16 px above and below, 0.5 x 20 + 4 = 14 px on the sides.
        near(0.484, z.top); near(0.536, z.bottom); near(0.386, z.left); near(0.514, z.right)
    }

    @Test
    fun `vertical text is padded across its own direction and clamped to the photo`() {
        val z = PhotoZones.zone(Region(0.0, 0.40, 0.02, 0.50), emptyList(), 1000, 1000)
        near(0.0, z.left); near(0.036, z.right) // across: 0.6 x 20 + 4 = 16 px
        near(0.386, z.top); near(0.514, z.bottom) // along: 14 px
    }

    @Test
    fun `cells of a grid are the bounds of their corners`() {
        val cell = TableCell(0, 0, 1, 1, Point(10.0, 20.0), Point(110.0, 22.0), Point(108.0, 70.0), Point(12.0, 68.0), IntBox(14, 24, 106, 66))
        val r = PhotoZones.cellsOf(TableGrid(1, 1, listOf(cell)), 200, 100).single()
        near(0.05, r.left); near(0.2, r.top); near(0.55, r.right); near(0.7, r.bottom)
    }
}
