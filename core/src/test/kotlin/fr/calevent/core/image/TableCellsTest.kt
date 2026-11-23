package fr.calevent.core.image

import fr.calevent.core.parse.Box
import fr.calevent.core.parse.TextElement
import fr.calevent.core.parse.TextLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TableCellsTest {

    private val w = 1200
    private val h = 900

    /** White page, 5 x 6 grid of 150 x 100 cells from (100, 100), 3 px rules. */
    private fun page(): IntArray {
        val px = IntArray(w * h) { 255 }
        fun rect(x0: Int, y0: Int, x1: Int, y1: Int) { for (y in y0 until y1) for (x in x0 until x1) px[y * w + x] = 30 }
        for (i in 0..5) rect(99, 99 + i * 100, 1002, 102 + i * 100)
        for (j in 0..6) rect(99 + j * 150, 99, 102 + j * 150, 602)
        return px
    }

    private fun glyph(px: IntArray, x: Int, y: Int) {
        fun rect(x0: Int, y0: Int, x1: Int, y1: Int) { for (yy in y0 until y1) for (xx in x0 until x1) px[yy * w + xx] = 30 }
        rect(x, y, x + 4, y + 14)
        rect(x + 12, y, x + 16, y + 14)
        rect(x + 4, y + 6, x + 12, y + 9)
    }

    @Test
    fun `only inked cells are cropped, upscaled, and map back onto the page`() {
        val px = page()
        glyph(px, 560, 340) // cell (2, 3): x 550..700, y 300..400
        glyph(px, 580, 340)
        val prepared = OcrPreprocess.prepare(px, w, h)
        val grid = assertNotNull(prepared.grid)
        val crops = TableCells.crops(prepared.pixels, w, h, grid)
        assertEquals(1, crops.size)
        val c = crops.single()
        assertEquals(2, c.cell.row)
        assertEquals(3, c.cell.col)
        assertEquals(1, c.textLines)
        assertTrue(c.scale > 2.0, "14 px text is upscaled, scale ${c.scale}")
        // The whole crop maps back to a page area around the glyphs, inside the cell.
        val back = c.toPage(Box(0.0, 0.0, c.width.toDouble(), c.height.toDouble()))
        assertTrue(back.left in 545.0..560.0 && back.right in 596.0..610.0, "x $back")
        assertTrue(back.top in 325.0..340.0 && back.bottom in 354.0..370.0, "y $back")
        // A glyph pixel of the crop is dark.
        val gx = ((562 - c.originX) * c.scale + c.pad).toInt()
        val gy = ((345 - c.originY) * c.scale + c.pad).toInt()
        assertTrue(c.pixels[gy * c.width + gx] < 100)
    }

    @Test
    fun `a rule remnant along the cell side is left out of the crop`() {
        val px = page()
        glyph(px, 600, 340)
        val prepared = OcrPreprocess.prepare(px, w, h)
        val grid = assertNotNull(prepared.grid)
        val clean = prepared.pixels.copyOf()
        // A slanted 2 px bar right inside the left side of cell (2, 3), as an unerased bold rule leaves.
        for (y in 310 until 360) for (dx in 0 until 2) clean[y * w + 556 + (y - 310) / 25 + dx] = 20
        val c = TableCells.crops(clean, w, h, grid).single()
        assertTrue(c.originX > 570, "crop starts at the text, not at the bar: ${c.originX}")
    }

    @Test
    fun `outside image keeps only what is around the table`() {
        val px = page()
        glyph(px, 300, 40) // a title above the table
        glyph(px, 560, 340)
        val prepared = OcrPreprocess.prepare(px, w, h)
        val out = TableCells.outside(prepared.pixels, w, h, assertNotNull(prepared.grid))
        assertTrue(out[45 * w + 302] < 100, "title kept")
        assertEquals(255, out[345 * w + 562], "table content removed")
    }

    @Test
    fun `cell lines are mapped back to page coordinates`() {
        val px = page()
        glyph(px, 560, 340)
        val prepared = OcrPreprocess.prepare(px, w, h)
        val c = TableCells.crops(prepared.pixels, w, h, assertNotNull(prepared.grid)).single()
        val word = TextElement("RH", Box(c.pad.toDouble(), c.pad.toDouble(), c.pad + 10 * c.scale, c.pad + 14 * c.scale), 0.9)
        val title = TextLine("Planning", Box(10.0, 10.0, 90.0, 30.0))
        val text = TableCells.assemble(w, h, listOf(title), listOf(c to listOf(TableCells.line(listOf(word)))))
        assertEquals(2, text.lines.size)
        val e = text.lines[1].elements.single().box
        assertEquals(c.originX.toDouble(), e.left, 1e-6)
        assertEquals(c.originY + 14.0, e.bottom, 1e-6)
        assertEquals(title, text.lines[0])
    }

    @Test
    fun `code-like words read with doubt are flagged for a second reading, times and names are not`() {
        for (t in listOf("MoO2", "So5", "NO8B", "| D0", "l08x")) assertTrue(TableCells.looksLikeMisreadCode(t), t)
        for (t in listOf("M06", "RH", "S14", "8h30", "17h30", "13:00", "Martin", "Fév", "19/10", "506", "6h")) {
            assertFalse(TableCells.looksLikeMisreadCode(t), t)
        }
    }

    @Test
    fun `second reading replaces the doubtful word and keeps its first confidence`() {
        val px = page()
        glyph(px, 560, 340)
        val prepared = OcrPreprocess.prepare(px, w, h)
        val c = TableCells.crops(prepared.pixels, w, h, assertNotNull(prepared.grid)).single()
        val line = TableCells.line(listOf(
            TextElement("MoO2", Box(10.0, 10.0, 60.0, 40.0), 0.45),
            TextElement("Fév", Box(70.0, 10.0, 110.0, 40.0), 0.97),
        ))
        val asked = ArrayList<IntBox>()
        val out = TableCells.applyRereads(c, listOf(line)) { area -> asked += area; TextElement("MO2", Box(0.0, 0.0, 1.0, 1.0), 0.02) }
        assertEquals(1, asked.size)
        assertTrue(asked[0].left <= 10 && asked[0].right >= 60, "area around the word: ${asked[0]}")
        assertEquals("MO2 Fév", out.single().text)
        assertEquals(0.45, out.single().elements[0].confidence)
    }
}
