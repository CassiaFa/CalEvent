package fr.calevent.core.parse

import fr.calevent.core.ShiftCodeTable
import fr.calevent.core.sampleCodeTable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextPatternsTest {

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test
    fun `hour formats`() {
        assertEquals(listOf(TimeRange(t(6), null, false)), parseTimes("6h").ranges)
        assertEquals(listOf(TimeRange(t(6), null, false)), parseTimes("06:00").ranges)
        assertEquals(listOf(TimeRange(t(6, 30), t(14), false)), parseTimes("6h30-14h").ranges)
        assertEquals(listOf(TimeRange(t(13), t(17), false)), parseTimes("13:00 à 17:00").ranges)
        assertEquals(listOf(TimeRange(t(8, 30), t(17, 30), false)), parseTimes("de 8h30 à 17h30").ranges)
    }

    @Test
    fun `split shift gives two ranges and keeps the title`() {
        val r = parseTimes("8h–12h / 14h–18h Bureau")
        assertEquals(listOf(TimeRange(t(8), t(12), false), TimeRange(t(14), t(18), false)), r.ranges)
        assertEquals("Bureau", r.rest)
    }

    @Test
    fun `night shift ends the next day`() {
        val r = parseTimes("22h à 6h")
        assertEquals(listOf(TimeRange(t(22), t(6), true)), r.ranges)
        assertTrue(parseTimes("22:00 a 06:00").ranges.single().endsNextDay)
    }

    @Test
    fun `OCR look-alike letters are read as digits and flagged`() {
        val r = parseTimes("l4h à 22h3O Quai B")
        assertEquals(listOf(TimeRange(t(14), t(22, 30), false)), r.ranges)
        assertEquals("Quai B", r.rest)
        assertTrue(r.corrected)
        assertFalse(parseTimes("14h à 22h").corrected)
    }

    @Test
    fun `start time and title`() {
        val r = parseTimes("10h30 Match de foot")
        assertEquals(listOf(TimeRange(t(10, 30), null, false)), r.ranges)
        assertEquals("Match de foot", r.rest)
        assertFalse(r.missingEnd)
    }

    @Test
    fun `range without end is flagged`() {
        val r = parseTimes("8h à")
        assertEquals(t(8), r.ranges.single().start)
        assertNull(r.ranges.single().end)
        assertTrue(r.missingEnd)
    }

    @Test
    fun `text without hours`() {
        assertTrue(parseTimes("Réunion équipe").ranges.isEmpty())
        assertTrue(parseTimes("Quai 12").ranges.isEmpty())
        assertTrue(parseTimes("Oh la la").ranges.isEmpty())
        assertTrue(parseTimes("25h").ranges.isEmpty())
        assertTrue(parseTimes("M06").ranges.isEmpty())
    }

    @Test
    fun `column headers`() {
        assertEquals(HeaderDate(setOf(DayOfWeek.MONDAY), 19, 10, null, null, true), parseHeaderDate("LUNDI 19/10"))
        assertEquals(HeaderDate(setOf(DayOfWeek.MONDAY), 22, 11, null, null, true), parseHeaderDate("Lun 22 nov."))
        assertEquals(HeaderDate(setOf(DayOfWeek.MONDAY), 25, null, null, 4, false), parseHeaderDate("L25 S04"))
        assertEquals(HeaderDate(setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY), 2, 2, null, null, true), parseHeaderDate("M02 Fév"))
        assertEquals(HeaderDate(setOf(DayOfWeek.FRIDAY), 15, 8, null, null, true), parseHeaderDate("V15 Aoû"))
        assertEquals(HeaderDate(setOf(DayOfWeek.MONDAY), 16, null, null, null, true), parseHeaderDate("lun. 16"))
        assertEquals(setOf(DayOfWeek.TUESDAY), parseHeaderDate("Mar").weekdays)
        assertEquals(3, parseHeaderDate("22 mar.").month)
        assertEquals(8, parseHeaderDate("LO8").day)
        assertEquals(18, parseHeaderDate("Ll8").day)
    }

    @Test
    fun `titles give months, years, a start day and a week number`() {
        val two = parseTitle(listOf("Planning EHPAD — Janvier 2027 / Février 2027"))
        assertEquals(listOf(1 to 2027, 2 to 2027), two.months)
        val start = parseTitle(listOf("Planning du 22 novembre 2027"))
        assertEquals(Triple(22, 11, 2027), start.firstDay)
        assertEquals(43, parseTitle(listOf("PLANNING SEMAINE 43")).isoWeek)
        assertEquals(listOf(8 to 2027), parseTitle(listOf("Planning Clinique — Août 2027")).months)
    }

    @Test
    fun `columns spanning December to January without a written year`() {
        val headers = listOf("J31 Déc", "V01 Jan", "S02 Jan", "D03", "L04 S01").map(::parseHeaderDate)
        val dates = resolveColumnDates(headers, TitleInfo(), LocalDate.of(2026, 12, 20))
        assertEquals(
            listOf(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 2), LocalDate.of(2027, 1, 3), LocalDate.of(2027, 1, 4)),
            dates.map { it.date },
        )
        assertTrue(dates.none { it.uncertain })
    }

    @Test
    fun `Monday columns without month take it from their neighbours`() {
        val headers = listOf("S30 Jan", "D31 Jan", "L01 S05", "M02 Fév").map(::parseHeaderDate)
        val dates = resolveColumnDates(headers, parseTitle(listOf("Janvier 2027 / Février 2027")), LocalDate.of(2026, 11, 18))
        assertEquals(LocalDate.of(2027, 2, 1), dates[2].date)
        assertFalse(dates[2].uncertain)
    }

    @Test
    fun `weekday contradicting the date is uncertain`() {
        val headers = listOf("LUNDI 19/10", "MARDI 20/10", "JEUDI 21/10").map(::parseHeaderDate)
        val dates = resolveColumnDates(headers, TitleInfo(), LocalDate.of(2026, 11, 18))
        assertEquals(LocalDate.of(2026, 10, 21), dates[2].date)
        assertTrue(dates[2].uncertain)
        assertEquals("Date incohérente avec le jour de la semaine", dates[2].reason)
        assertFalse(dates[0].uncertain)
    }

    @Test
    fun `weekdays only use the title week number, else the coming week`() {
        val headers = listOf("Lundi", "Mardi", "Mercredi").map(::parseHeaderDate)
        val week = resolveColumnDates(headers, TitleInfo(isoWeek = 43, years = listOf(2026)), LocalDate.of(2026, 11, 18))
        assertEquals(LocalDate.of(2026, 10, 19), week[0].date)
        assertFalse(week[0].uncertain)
        val guessed = resolveColumnDates(headers, TitleInfo(), LocalDate.of(2026, 11, 18))
        assertEquals(listOf(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 25)), guessed.map { it.date })
        assertTrue(guessed.all { it.uncertain })
    }

    @Test
    fun `codes are normalized`() {
        val table = sampleCodeTable()
        assertEquals("M06" to true, fixCode("MO6", table))
        assertEquals("N08" to false, fixCode("N08", table))
        assertEquals("RH" to false, fixCode("rh", table))
        assertTrue(looksLikeCode("G03", table))
        assertTrue(looksLikeCode("MAL", table))
        assertFalse(looksLikeCode("8h à 16h", table))
        assertFalse(looksLikeCode("Quai B", table))
    }

    @Test
    fun `JSON form round-trips`() {
        val text = PhotoFixtures.weekHours()
        assertEquals(text, RecognizedTextJson.read(RecognizedTextJson.write(text)))
        val tricky = RecognizedText(10, 20, listOf(TextLine("a \"b\" \\ é\n", Box(0.0, 1.0, 2.0, 3.5))))
        assertEquals(tricky, RecognizedTextJson.read(RecognizedTextJson.write(tricky)))
    }

    @Test
    fun `range word glued to a time is still a range`() {
        for (raw in listOf("22h à6h", "22hà 6h", "22hà6h", "22h à 6h")) {
            val r = parseTimes(raw)
            assertEquals(listOf(TimeRange(t(22), t(6), true)), r.ranges, raw)
            assertFalse(r.corrected, raw)
        }
        assertEquals(listOf(TimeRange(t(8), t(16), false)), parseTimes("8hà16h Quai 12").ranges)
        assertEquals("Quai 12", parseTimes("8hà16h Quai 12").rest)
    }

    @Test
    fun `first hour read as a letter is repaired and flagged`() {
        val five = parseTimes("Sh à 13h Site Val-Fleuri")
        assertEquals(listOf(TimeRange(t(5), t(13), false)), five.ranges)
        assertTrue(five.corrected)
        assertEquals(listOf(TimeRange(t(8), t(16), false)), parseTimes("&h à 16h").ranges)
        assertEquals(listOf(TimeRange(t(8, 30), t(17, 30), false)), parseTimes("Bh30 à 17h30").ranges)
        // Ordinary words are left alone.
        assertEquals("Shop", parseTimes("Shop").rest)
        assertTrue(parseTimes("Bureau").ranges.isEmpty())
    }
}
