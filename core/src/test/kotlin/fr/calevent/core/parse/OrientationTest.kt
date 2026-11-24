package fr.calevent.core.parse

import fr.calevent.core.ShiftCodeTable
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrientationTest {
    private val today = LocalDate.of(2026, 5, 20)

    private fun text(vararg lines: String) = RecognizedText(
        1000, 800,
        lines.mapIndexed { i, s -> TextLine(s, Box(100.0, 50.0 + 40 * i, 600.0, 75.0 + 40 * i), confidence = 0.9) },
    )

    /** A weekly planning read the right way up: day header and a time range under each day. */
    private val upright = RecognizedText(
        1000, 800,
        listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi").flatMapIndexed { i, day ->
            val x = 100.0 + 170 * i
            listOf(
                TextLine(day, Box(x, 100.0, x + 120, 125.0), confidence = 0.95),
                TextLine("${18 + i}/05", Box(x, 140.0, x + 60, 165.0), confidence = 0.95),
                TextLine("08:00 - 16:00", Box(x, 220.0, x + 140, 245.0), confidence = 0.9),
            )
        },
    )

    /** The same planning upside down: what an engine makes of text turned over. */
    private val gibberish = text("00:91 - 00:80", "ipunl", "Z0/8l", "ipJew", "00:9l 00:80", ":", "l")

    @Test
    fun `readable words count times, dates, day names and codes, not gibberish`() {
        assertTrue(Orientation.readableWords(text("Lundi 12/05", "08:00 M06", "Planning")) >= 3.0)
        assertTrue(Orientation.readableWords(gibberish) < 1.0)
    }

    @Test
    fun `an upright planning is kept without reading other orientations`() {
        var reads = 0
        val chosen = Orientation.detect(upright, today, ShiftCodeTable()) { reads++; null }
        assertEquals(0, chosen)
        assertEquals(0, reads)
    }

    @Test
    fun `the orientation where the text reads best is chosen`() {
        val readings = mapOf(0 to gibberish, 90 to text(), 180 to upright, 270 to text(":"))
        assertEquals(180, Orientation.detect(gibberish, today, ShiftCodeTable()) { readings[it] })
    }

    @Test
    fun `small or relative gains keep the photo as it is`() {
        assertEquals(0, Orientation.choose(mapOf(0 to 2.0, 90 to 2.2, 180 to 0.0, 270 to 0.1)))
        assertEquals(0, Orientation.choose(mapOf(0 to 5.0, 90 to 8.0)))
        assertEquals(0, Orientation.choose(mapOf(0 to 0.0, 180 to 0.2)))
        assertEquals(270, Orientation.choose(mapOf(0 to 0.1, 90 to 0.0, 180 to 0.0, 270 to 4.0)))
        assertEquals(0, Orientation.choose(emptyMap()))
    }

    @Test
    fun `skipped readings (time budget) compare only what was read`() {
        val readings = mapOf(0 to gibberish, 180 to upright)
        assertEquals(0, Orientation.detect(gibberish, today, ShiftCodeTable()) { if (it == 0) readings[0] else null })
    }
}
