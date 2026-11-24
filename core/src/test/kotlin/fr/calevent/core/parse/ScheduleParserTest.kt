package fr.calevent.core.parse

import fr.calevent.core.ShiftCodeTable
import fr.calevent.core.sampleCodeTable
import fr.calevent.core.Slot
import fr.calevent.core.SlotStatus
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** One test per real photo (anonymized fixture reproducing its structure). */
class ScheduleParserTest {

    private val today = LocalDate.of(2026, 11, 18)
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    /** (date, start, end, title) of each slot, for compact assertions. */
    private fun List<Slot>.brief() = map { listOf(it.date, it.start, it.end, it.title) }

    @Test
    fun `photo 1 - weekly team schedule with hours, no year`() {
        val r = ScheduleParser.parse(PhotoFixtures.weekHours(), today)
        assertEquals(ScheduleKind.TEAM_HOURS, r.kind, r.toString())
        assertEquals(PhotoFixtures.weekHoursPeople, r.people)
        assertTrue(r.warnings.isEmpty(), r.warnings.toString())
        assertTrue(r.slotsFor(null, sampleCodeTable()).isEmpty(), "nobody selected by default")
        assertEquals(32, r.slots.size)

        val aubert = r.slotsFor("mme aubert", sampleCodeTable())
        assertEquals(
            listOf(
                listOf(d(2026, 10, 19), t(8, 30), t(17, 30), "Dépôt Nord"),
                listOf(d(2026, 10, 22), t(8, 30), t(17, 30), "Quai 12"),
                listOf(d(2026, 10, 23), t(22), t(6), "Dépôt Nord"),
            ),
            aubert.brief(),
        )
        assertTrue(aubert.all { it.status == SlotStatus.KEPT && !it.dateUncertain && it.person == "Mme Aubert" })
        assertTrue(aubert[2].endsNextDay)
        assertEquals("Dépôt Nord", aubert[0].location)
        val region = assertNotNull(aubert[0].region)
        assertTrue(region.left in 0.24..0.32 && region.top in 0.27..0.31, region.toString())

        // "l4h à 22h": read as 14h, slightly less confident but still kept.
        val blanc = r.slotsFor("M. Blanc", sampleCodeTable()).first()
        assertEquals(listOf(d(2026, 10, 19), t(14), t(22), "Dépôt Sud"), listOf(blanc.date, blanc.start, blanc.end, blanc.title))
        assertEquals(SlotStatus.KEPT, blanc.status)
        assertTrue(blanc.confidence < aubert[0].confidence)

        // Badly printed place: to review with a written reason.
        val hamel = r.slotsFor("M. Hamel", sampleCodeTable())
        assertEquals(listOf(d(2026, 10, 22), d(2026, 10, 24)), hamel.map { it.date })
        assertEquals(SlotStatus.TO_REVIEW, hamel[1].status)
        assertEquals("Texte peu lisible", hamel[1].reviewReason)

        // The footer ("… avant 16h la veille") is neither a person nor a slot.
        assertTrue(r.slots.none { it.sourceText.contains("veille") })
    }

    @Test
    fun `photo 2 - weekly team schedule with colon hours and explicit year`() {
        val r = ScheduleParser.parse(PhotoFixtures.weekColon(), today)
        assertEquals(ScheduleKind.TEAM_HOURS, r.kind, r.toString())
        assertEquals(PhotoFixtures.weekColonPeople, r.people)
        val arnaud = r.slotsFor("Mme Arnaud", sampleCodeTable())
        assertEquals(
            listOf(
                listOf(d(2027, 11, 22), t(13), t(17), "Dépôt Sud"),
                listOf(d(2027, 11, 23), t(7, 30), t(12, 30), "Hangar Sud"),
                listOf(d(2027, 11, 26), t(13), t(17), "Atelier 2"),
                listOf(d(2027, 11, 28), t(22), t(6), "Dépôt Sud"),
            ),
            arnaud.brief(),
        )
        assertTrue(arnaud.last().endsNextDay)
        // Two cells merged by OCR into one line are split back into Friday and Saturday.
        val etienne = r.slotsFor("M. Etienne", sampleCodeTable())
        assertEquals(
            listOf(
                listOf(d(2027, 11, 22), t(7, 30), t(12, 30), "Atelier 2"),
                listOf(d(2027, 11, 23), t(8, 30), t(17, 30), "Chantier A7"),
                listOf(d(2027, 11, 24), t(5), t(13), "Chantier Gare"),
                listOf(d(2027, 11, 25), t(5), t(13), "Atelier 2"),
                listOf(d(2027, 11, 26), t(9), t(18), "Chantier A7"),
                listOf(d(2027, 11, 27), t(13), t(17), "Site Est"),
                listOf(d(2027, 11, 28), t(5), t(13), "Dépôt Sud"),
            ),
            etienne.brief(),
        )
        // The Sunday column is drawn higher (perspective) but stays on the right rows.
        assertEquals(listOf(d(2027, 11, 28), t(22), t(6)), r.slotsFor("M. Garnier", sampleCodeTable()).last().let { listOf(it.date, it.start, it.end) })
        val expected = PhotoFixtures.weekColonCells.sumOf { row -> row.count { it.isNotEmpty() } } + 2
        assertEquals(expected, r.slots.size)
        assertTrue(r.slots.all { it.status == SlotStatus.KEPT }, r.slots.filter { it.status != SlotStatus.KEPT }.toString())
    }

    @Test
    fun `photo 3 - month wall calendar`() {
        val r = ScheduleParser.parse(PhotoFixtures.wallCalendar(), today)
        assertEquals(ScheduleKind.WALL_CALENDAR, r.kind, r.toString())
        assertEquals(PhotoFixtures.wallEvents.values.sumOf { it.size }, r.slots.size)
        assertTrue(r.slots.none { it.dateUncertain })
        fun on(day: Int) = r.slots.filter { it.date == d(2025, 9, day) }

        assertEquals(setOf(listOf(t(17), "Séminaire"), listOf(t(7), "Entretien")), on(2).map { listOf(it.start, it.title) }.toSet())
        val yoga = on(6).single { it.title == "Yoga" }
        assertEquals(t(12, 45), yoga.start)
        assertNull(yoga.end)
        assertEquals(SlotStatus.KEPT, yoga.status)
        assertEquals(3, on(14).size, "day 14 read as \"l4\" still holds its events")
        assertEquals(listOf(t(9, 30)), on(25).map { it.start }, "row with an unread day number")

        val meeting = on(17).single { it.allDay }
        assertEquals("Réunion équipe", meeting.title)
        assertEquals(SlotStatus.TO_REVIEW, meeting.status)
        assertEquals("Aucune heure lue : journée entière", meeting.reviewReason)
        assertEquals(t(10, 30), on(17).single { !it.allDay }.start)
        assertEquals(listOf("RDV médecin"), on(29).map { it.title })
    }

    @Test
    fun `photo 4 - team codes over January and February, upside down`() {
        val r = ScheduleParser.parse(PhotoFixtures.janFebUpsideDown(), today, PhotoFixtures.careCodes)
        assertEquals(ScheduleKind.TEAM_CODES, r.kind, r.toString())
        assertEquals(180, r.rotationDegrees)
        assertEquals(listOf(ParseWarning.UPSIDE_DOWN), r.warnings)
        assertEquals(PhotoFixtures.janFebPeople.map { it.replace('|', ' ') }, r.people)
        val team = assertNotNull(r.team)
        assertEquals(PhotoFixtures.janFebCells.sumOf { row -> row.count { it.isNotEmpty() } }, team.cells.size)
        assertEquals(d(2027, 1, 25)..d(2027, 2, 8), r.period)

        val paul = r.slotsFor("Garnier Paul", PhotoFixtures.careCodes)
        assertEquals(14, paul.size)
        assertEquals(listOf(d(2027, 1, 25), t(9), t(17), "Journée (J09)"), paul[0].let { listOf(it.date, it.start, it.end, it.title) })
        assertEquals(listOf(d(2027, 1, 26), true, "Repos"), paul[1].let { listOf(it.date, it.allDay, it.title) })
        assertEquals(d(2027, 1, 28), paul[2].date, "empty cell on the 27th skipped")
        val night = paul.single { it.date == d(2027, 1, 30) }
        assertEquals("Nuit (N08)", night.title)
        assertTrue(night.endsNextDay)
        // Monday 1 February: its header has the week number instead of the month.
        assertEquals("Matin (M06)", paul.single { it.date == d(2027, 2, 1) }.title)
        assertTrue(paul.all { it.status == SlotStatus.KEPT && it.region != null })

        // "GO3" read on Sophie Leblanc's row on 2 February.
        val corrected = r.slotsFor("Leblanc Sophie", PhotoFixtures.careCodes).single { it.date == d(2027, 2, 2) }
        assertEquals("Garde (G03)", corrected.title)
        // Sick leave never shows its code.
        val sick = r.slotsFor("Roux Antoine", PhotoFixtures.careCodes).first()
        assertEquals(listOf("Absence", ""), listOf(sick.title, sick.sourceText))
        // With the default table the care-home codes are unknown: to review, reason written.
        val unknown = r.slotsFor("Garnier Paul", sampleCodeTable()).first { it.date == d(2027, 1, 28) }
        assertEquals(SlotStatus.TO_REVIEW, unknown.status)
        assertEquals("Code inconnu : G03", unknown.reviewReason)
    }

    @Test
    fun `photo 5 - team codes for January, quarter turn`() {
        val r = ScheduleParser.parse(PhotoFixtures.januaryQuarterTurn(), today, PhotoFixtures.careCodes)
        assertEquals(ScheduleKind.TEAM_CODES, r.kind, r.toString())
        assertTrue(ParseWarning.ROTATED_QUARTER_TURN in r.warnings)
        assertEquals(PhotoFixtures.januaryPeople.filter { it.isNotEmpty() }.map { it.replace('|', ' ') }, r.people)
        val team = assertNotNull(r.team)
        assertEquals(PhotoFixtures.januaryCells.sumOf { row -> row.count { it.isNotEmpty() } }, team.cells.size)
        assertEquals(d(2027, 1, 4)..d(2027, 1, 18), r.period)

        val agnes = r.slotsFor("Rey-Duval Agnès", PhotoFixtures.careCodes)
        assertEquals(15, agnes.size)
        assertEquals(listOf(d(2027, 1, 4), "Matin (M06)"), listOf(agnes[0].date, agnes[0].title))
        assertEquals(listOf("Absence"), agnes.filter { it.date in d(2027, 1, 5)..d(2027, 1, 8) }.map { it.title }.distinct())
        assertEquals(listOf(d(2027, 1, 18), "Soir (S14)"), listOf(agnes.last().date, agnes.last().title))

        // "GO5" and "NO8" on Mathis Lacroix's row.
        val mathis = r.slotsFor("Lacroix Mathis", PhotoFixtures.careCodes)
        assertEquals("Garde (G05)", mathis.single { it.date == d(2027, 1, 6) }.title)
        assertEquals("Nuit (N08)", mathis.single { it.date == d(2027, 1, 11) }.title)
        assertFalse(mathis.any { it.date == d(2027, 1, 10) })

        // A person with only two shifts, at the end of the period.
        assertEquals(listOf(d(2027, 1, 17), d(2027, 1, 18)), r.slotsFor("Noël Inès", PhotoFixtures.careCodes).map { it.date })
    }

    @Test
    fun `photo 6 - team codes for August, quarter turn without OCR angles`() {
        val r = ScheduleParser.parse(PhotoFixtures.augustQuarterTurnNoAngles(), today, PhotoFixtures.careCodes)
        assertEquals(ScheduleKind.TEAM_CODES, r.kind, r.toString())
        assertTrue(ParseWarning.ROTATED_QUARTER_TURN in r.warnings)
        assertEquals(PhotoFixtures.augustPeople.map { it.replace('|', ' ') }, r.people)
        assertEquals(d(2027, 8, 16)..d(2027, 8, 30), r.period)

        val lucie = r.slotsFor("Joly Inès", PhotoFixtures.careCodes)
        assertEquals(14, lucie.size)
        assertEquals(listOf(d(2027, 8, 16), "Nuit (N08)"), listOf(lucie[0].date, lucie[0].title))
        val training = lucie.single { it.date == d(2027, 8, 25) }
        assertEquals("FOR", training.title)
        assertEquals(SlotStatus.TO_REVIEW, training.status)
        assertEquals("Code inconnu : FOR", training.reviewReason)
        assertEquals(listOf(d(2027, 8, 29), d(2027, 8, 30)), lucie.takeLast(2).map { it.date })
        assertEquals("Congés", lucie.last().title)
    }
}
