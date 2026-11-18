package fr.calevent.core.parse

import fr.calevent.core.ShiftCodeTable
import fr.calevent.core.SlotStatus
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScheduleParserEdgeCasesTest {

    private val today = LocalDate.of(2026, 11, 18)
    private val codes = ShiftCodeTable.default()
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    private val smallCodeSpec = TableSpec(
        labelX = 60.0, firstColX = 290.0, pitch = 88.0, headerY = 110.0, headerLineGap = 26.0, headerH = 16.0,
        firstRowY = 175.0, rowPitch = 110.0, textH = 16.0, subH = 13.0, stackedNames = true,
    )

    private fun yearEnd(title: String, headers: List<String>) = OcrSheet(1000, 600)
        .text(title, 60.0, 50.0, 16.0)
        .teamTable(
            smallCodeSpec, "NOM / PRENOM", headers,
            listOf("Valdez|Alice", "Ferrand|Bruno", "Lopez|Chloé"),
            listOf(
                "M06 M06 N08 RH RH CA CA".split(' '),
                "S14 S14 S14 RH M06 M06 M06".split(' '),
                listOf("RH", "RH", "", "J08", "J08", "", "N08"),
            ),
        ).build(tiltDegrees = 0.5)

    private val yearEndHeaders = PhotoFixtures.codeHeaders(d(2026, 12, 28), 7, mapOf(12 to "Déc", 1 to "Jan"))

    @Test
    fun `team codes from December to January with years in the title`() {
        val r = ScheduleParser.parse(yearEnd("Planning — Décembre 2026 / Janvier 2027", yearEndHeaders), today, codes)
        assertEquals(ScheduleKind.TEAM_CODES, r.kind, r.toString())
        val alice = r.slotsFor("Valdez Alice", codes)
        assertEquals((28..31).map { d(2026, 12, it) } + (1..3).map { d(2027, 1, it) }, alice.map { it.date })
        val night = alice[2]
        assertEquals(listOf("Nuit (N08)", t(21), t(7), true), listOf(night.title, night.start, night.end, night.endsNextDay))
        assertEquals(listOf("Congés", "Congés"), alice.takeLast(2).map { it.title })
        assertTrue(alice.all { it.status == SlotStatus.KEPT })
        assertEquals(listOf(d(2026, 12, 28), d(2026, 12, 29), d(2026, 12, 31), d(2027, 1, 1), d(2027, 1, 3)), r.slotsFor("Lopez Chloé", codes).map { it.date })
    }

    @Test
    fun `December to January without any written year`() {
        val r = ScheduleParser.parse(yearEnd("Planning des fêtes", yearEndHeaders), d(2026, 12, 20), codes)
        val bruno = r.slotsFor("Ferrand Bruno", codes)
        assertEquals((28..31).map { d(2026, 12, it) } + (1..3).map { d(2027, 1, it) }, bruno.map { it.date })
        assertTrue(bruno.none { it.dateUncertain })
    }

    @Test
    fun `a header contradicting its weekday makes the column to review`() {
        // "V01" misread as "J01": 1 January 2027 is a Friday.
        val headers = yearEndHeaders.map { if (it.startsWith("V01")) "J01|Jan" else it }
        val r = ScheduleParser.parse(yearEnd("Planning — Décembre 2026 / Janvier 2027", headers), today, codes)
        val slot = r.slotsFor("Valdez Alice", codes).single { it.date == d(2027, 1, 1) }
        assertTrue(slot.dateUncertain)
        assertEquals(SlotStatus.TO_REVIEW, slot.status)
        assertEquals("Date incohérente avec le jour de la semaine", slot.reviewReason)
        // Even read through TeamSchedule directly, the cell is not pre-selected.
        assertFalse(assertNotNull(r.team).slotsFor("Valdez Alice", codes).single { it.date == d(2027, 1, 1) }.isSelected)
    }

    @Test
    fun `team hours spanning October and November`() {
        val spec = TableSpec(
            labelX = 60.0, firstColX = 300.0, pitch = 150.0, headerY = 100.0, headerLineGap = 26.0, headerH = 12.0,
            firstRowY = 180.0, rowPitch = 80.0, textH = 12.0, subH = 9.0, stackedNames = false,
        )
        val text = OcrSheet(1400, 500).teamTable(
            spec, "Équipe",
            listOf("LUNDI|26/10", "MARDI|27/10", "MERCREDI|28/10", "JEUDI|29/10", "VENDREDI|30/10", "SAMEDI|31/10", "DIMANCHE|01/11"),
            listOf("Mme Roche", "M. Lenoir"),
            listOf(
                listOf("8h à 16h|Quai 1", "", "", "", "", "22h à 6h|Quai 2", "6h-14h|Quai 1"),
                listOf("", "9h à 17h|Atelier", "", "", "", "", ""),
            ),
        ).build()
        val r = ScheduleParser.parse(text, today, codes)
        assertEquals(ScheduleKind.TEAM_HOURS, r.kind, r.toString())
        val roche = r.slotsFor("Mme Roche", codes)
        assertEquals(listOf(d(2026, 10, 26), d(2026, 10, 31), d(2026, 11, 1)), roche.map { it.date })
        assertEquals(listOf(t(6), t(14), "Quai 1"), roche.last().let { listOf(it.start, it.end, it.title) })
        assertTrue(roche[1].endsNextDay)
    }

    @Test
    fun `weekly planning read from a JSON dump`() {
        val json = javaClass.getResource("/ocr/weekly-planning.json")!!.readText()
        val r = ScheduleParser.parse(RecognizedTextJson.read(json), today, codes)
        assertEquals(ScheduleKind.WEEKLY, r.kind, r.toString())
        assertEquals(
            listOf(
                listOf(d(2026, 11, 16), t(8), t(12), "Bureau", SlotStatus.KEPT),
                listOf(d(2026, 11, 16), t(14), t(18), "Bureau", SlotStatus.KEPT),
                listOf(d(2026, 11, 17), t(9, 30), null, "Dentiste", SlotStatus.KEPT),
                listOf(d(2026, 11, 18), null, null, "Réunion parents", SlotStatus.TO_REVIEW),
                listOf(d(2026, 11, 19), t(6, 30), t(14), "Inventaire", SlotStatus.KEPT),
                listOf(d(2026, 11, 20), t(8), null, "Événement", SlotStatus.TO_REVIEW),
                listOf(d(2026, 11, 20), t(20), null, "Cinéma", SlotStatus.KEPT),
            ),
            r.slots.map { listOf(it.date, it.start, it.end, it.title, it.status) },
        )
        assertEquals("Heure de fin incertaine", r.slots.single { it.title == "Événement" }.reviewReason)
        assertTrue(r.slots.single { it.title == "Réunion parents" }.allDay)
    }

    @Test
    fun `strongly tilted photo is still read and flagged`() {
        val r = ScheduleParser.parse(PhotoFixtures.weekHours(tilt = 12.0), today, codes)
        assertEquals(ScheduleKind.TEAM_HOURS, r.kind, r.toString())
        assertTrue(ParseWarning.TILTED in r.warnings)
        assertEquals(12.0, r.skewDegrees, 1.0)
        assertEquals(
            listOf(d(2026, 10, 19), d(2026, 10, 22), d(2026, 10, 23)),
            r.slotsFor("Mme Aubert", codes).map { it.date },
        )
        assertEquals(32, r.slots.size)
    }

    @Test
    fun `text that is not a schedule gives no slot`() {
        val sheet = OcrSheet(1200, 900)
        listOf(
            "Il était une fois un petit village", "au bord d'une rivière tranquille.", "Chaque matin, le boulanger",
            "ouvrait sa boutique avant l'aube", "et saluait les passants.", "Les enfants couraient vers l'école",
            "en riant aux éclats.", "Fin du premier chapitre",
        ).forEachIndexed { i, s -> sheet.text(s, 100.0, 100.0 + 40 * i, 18.0) }
        val r = ScheduleParser.parse(sheet.build(), today, codes)
        assertEquals(ScheduleKind.UNKNOWN, r.kind)
        assertEquals(listOf(ParseWarning.NO_SLOTS), r.warnings)
        assertTrue(r.slots.isEmpty() && r.people.isEmpty())
    }

    @Test
    fun `blurred photo with little text`() {
        val text = OcrSheet(1200, 900)
            .text("Plann1ng", 300.0, 200.0, 20.0, conf = 0.45)
            .text("l8h", 500.0, 400.0, 20.0, conf = 0.4)
            .build()
        val r = ScheduleParser.parse(text, today, codes)
        assertEquals(listOf(ParseWarning.LOW_TEXT, ParseWarning.NO_SLOTS), r.warnings)
        assertEquals(ScheduleKind.UNKNOWN, r.kind)
    }

    @Test
    fun `nothing recognized at all`() {
        val r = ScheduleParser.parse(RecognizedText(1200, 900, emptyList()), today, codes)
        assertEquals(listOf(ParseWarning.LOW_TEXT, ParseWarning.NO_SLOTS), r.warnings)
    }

    @Test
    fun `31 days by 12 people is parsed well under a second`() {
        val people = (1..12).map { "Nom$it|Prénom".replace(Regex("\\d+")) { m -> "ABCDEFGHIJKL"[m.value.toInt() - 1].toString() } }
        val cycle = listOf("M06", "M07", "S14", "J08", "N08", "RH", "CA")
        val cells = people.indices.map { p -> (0 until 31).map { day -> cycle[(p + day) % cycle.size] } }
        val spec = TableSpec(
            labelX = 40.0, firstColX = 260.0, pitch = 80.0, headerY = 100.0, headerLineGap = 26.0, headerH = 16.0,
            firstRowY = 170.0, rowPitch = 110.0, textH = 16.0, subH = 13.0, stackedNames = true,
        )
        val text = OcrSheet(2800, 1600)
            .text("Planning du service — Janvier 2027", 40.0, 40.0, 18.0)
            .teamTable(spec, "NOM / PRENOM", PhotoFixtures.codeHeaders(d(2027, 1, 1), 31, mapOf(1 to "Jan")), people, cells)
            .build(rotation = 90, tiltDegrees = 0.7)
        ScheduleParser.parse(text, today, codes) // warm-up (class loading, JIT)
        val start = System.nanoTime()
        val r = ScheduleParser.parse(text, today, codes)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue(ms < 1000, "took $ms ms")
        assertEquals(ScheduleKind.TEAM_CODES, r.kind, r.toString())
        assertEquals(12, r.people.size)
        assertEquals(31 * 12, assertNotNull(r.team).cells.size)
        val first = r.slotsFor(r.people[0], codes)
        assertEquals((1..31).map { d(2027, 1, it) }, first.map { it.date })
        assertEquals("Matin (M06)", first[0].title)
        assertTrue(first.all { it.status == SlotStatus.KEPT })
    }
}
