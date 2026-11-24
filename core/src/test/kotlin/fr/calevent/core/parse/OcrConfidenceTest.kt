package fr.calevent.core.parse

import fr.calevent.core.ShiftCodeTable
import fr.calevent.core.sampleCodeTable
import fr.calevent.core.SlotStatus
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The OCR-confidence rule: scaled mean guarded by the weakest token, then the doubt factors. */
class OcrConfidenceTest {

    private val today = LocalDate.of(2026, 11, 18)
    private val codes = sampleCodeTable()

    // ---- aggregation ----

    @Test
    fun `engine words around 0_7 to 0_8 read as kept text`() {
        assertTrue(OcrConfidence.calibrate(listOf(0.70)) >= SlotStatus.KEPT_THRESHOLD)
        assertTrue(OcrConfidence.calibrate(listOf(0.67)) >= SlotStatus.KEPT_THRESHOLD)
        assertTrue(OcrConfidence.calibrate(listOf(0.64)) < SlotStatus.KEPT_THRESHOLD)
    }

    @Test
    fun `one weak token is not drowned by its neighbours`() {
        // mean 0.75 but the weakest token (0.55) caps the mean at 0.65: below the kept threshold.
        assertTrue(OcrConfidence.calibrate(listOf(0.95, 0.55)) < SlotStatus.KEPT_THRESHOLD)
        // Two similar tokens: plain mean.
        assertEquals(0.72 * OcrConfidence.SCALE, OcrConfidence.calibrate(listOf(0.70, 0.74)), 1e-9)
    }

    @Test
    fun `clear misreads stay far below the review band`() {
        assertTrue(OcrConfidence.calibrate(listOf(0.30)) < SlotStatus.REVIEW_THRESHOLD)
        assertTrue(OcrConfidence.calibrate(listOf(0.40)) < SlotStatus.REVIEW_THRESHOLD)
        assertTrue(OcrConfidence.calibrate(listOf(0.55)) in SlotStatus.REVIEW_THRESHOLD..SlotStatus.KEPT_THRESHOLD)
    }

    @Test
    fun `ocr confidence is clamped and falls back when there are no tokens`() {
        assertEquals(DEFAULT_OCR_CONFIDENCE, OcrConfidence.of(emptyList()))
        val high = Token("M06", Box(0.0, 0.0, 1.0, 1.0), Box(0.0, 0.0, 1.0, 1.0), 0.99)
        assertEquals(1.0, OcrConfidence.of(listOf(high)))
    }

    // ---- doubt factors ----

    @Test
    fun `a doubt keeps a clear slot out of kept`() {
        val clear = Evidence(1.0)
        assertEquals(1.0, clear.confidence)
        val doubted = Evidence(1.0).penalize(0.9, REASON_CODE_CORRECTED)
        assertTrue(doubted.confidence < SlotStatus.KEPT_THRESHOLD)
        assertEquals(REASON_CODE_CORRECTED, doubted.reason(null))
        assertTrue(Evidence(1.0).penalize(0.85, REASON_POSITION).confidence < SlotStatus.KEPT_THRESHOLD)
    }

    @Test
    fun `a soft doubt only lowers the confidence`() {
        val soft = Evidence(1.0).penalize(0.9, REASON_TIME_CORRECTED, soft = true)
        assertEquals(0.9, soft.confidence, 1e-9)
        assertTrue(Evidence(0.8).penalize(0.9, REASON_TIME_CORRECTED, soft = true).confidence < SlotStatus.KEPT_THRESHOLD)
    }

    @Test
    fun `low text keeps its reason`() {
        assertEquals(REASON_LOW_OCR, Evidence(0.6).reason(null))
        assertEquals(null, Evidence(0.9).reason(null))
    }

    // ---- through the parser: a code table whose words have chosen confidences ----

    private val spec = TableSpec(
        labelX = 60.0, firstColX = 290.0, pitch = 88.0, headerY = 110.0, headerLineGap = 26.0, headerH = 16.0,
        firstRowY = 175.0, rowPitch = 110.0, textH = 16.0, subH = 13.0, stackedNames = true,
    )

    private fun parse(cells: List<List<String>>, conf: (row: Int, col: Int) -> Double) = ScheduleParser.parse(
        OcrSheet(1000, 600)
            .text("Planning — Décembre 2026", 60.0, 50.0, 16.0)
            .teamTable(
                spec, "NOM / PRENOM", PhotoFixtures.codeHeaders(LocalDate.of(2026, 12, 7), 7, mapOf(12 to "Déc")),
                listOf("Valdez|Alice", "Ferrand|Bruno"), cells,
                conf = { r, c, _ -> conf(r, c) },
            ).build(tiltDegrees = 0.3),
        today, codes,
    )

    private val cells = listOf(
        "M06 M06 N08 RH RH CA CA".split(' '),
        "S14 S14 S14 RH M06 M06 M06".split(' '),
    )

    @Test
    fun `words at 0_72 are kept, as an engine with conservative confidences returns them`() {
        val slots = parse(cells) { _, _ -> 0.72 }.slotsFor("Valdez Alice", codes)
        assertEquals(7, slots.size)
        assertTrue(slots.all { it.status == SlotStatus.KEPT }, slots.map { it.status to it.confidence }.toString())
    }

    @Test
    fun `hesitant cells go to review with the unreadable-text reason, unreadable ones are ignored`() {
        val r = parse(cells) { row, col -> if (row == 0 && col == 1) 0.52 else if (row == 0 && col == 2) 0.38 else 0.8 }
        val alice = r.slotsFor("Valdez Alice", codes)
        assertEquals(SlotStatus.KEPT, alice[0].status)
        assertEquals(SlotStatus.TO_REVIEW, alice[1].status)
        assertEquals(REASON_LOW_OCR, alice[1].reviewReason)
        assertEquals(SlotStatus.IGNORED, alice[2].status)
    }

    @Test
    fun `a corrected code is to review even when the text is clear`() {
        val misread = listOf(listOf("MO6") + cells[0].drop(1), cells[1])
        val alice = parse(misread) { _, _ -> 0.95 }.slotsFor("Valdez Alice", codes)
        assertEquals(SlotStatus.TO_REVIEW, alice[0].status)
        assertEquals(REASON_CODE_CORRECTED, alice[0].reviewReason)
        assertEquals(SlotStatus.KEPT, alice[1].status)
    }

    @Test
    fun `an unknown code is never kept, however clear`() {
        val odd = listOf(listOf("QQ") + cells[0].drop(1), cells[1])
        val alice = parse(odd) { _, _ -> 0.99 }.slotsFor("Valdez Alice", codes)
        assertEquals(SlotStatus.TO_REVIEW, alice[0].status)
        assertEquals("Code inconnu : QQ", alice[0].reviewReason)
    }
}
