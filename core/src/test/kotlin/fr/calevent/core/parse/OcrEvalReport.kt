package fr.calevent.core.parse

import fr.calevent.core.ShiftCodeTable
import fr.calevent.core.sampleCodeTable
import fr.calevent.core.Slot
import fr.calevent.core.SlotStatus
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate

/**
 * Not a regression test: a report generator for OCR evaluations run on the developer's machine.
 *
 * `tools/ocr-eval/run.sh` writes `data/ocr-eval/<engine>/<photo>_<degrees>.json` (RecognizedText JSON of
 * the private photos in `data/`, never committed). This test replays the app's analysis on them (upright
 * first, then the other quarter turns when the result looks wrong, as `ScanAnalyzer` does) and writes
 * `data/ocr-eval/report.txt`. Skipped when the folder does not exist (CI, other machines).
 */
class OcrEvalReport {

    private val root = OcrEvalSupport.root

    @Test
    fun writeReport() {
        assumeTrue(root.isDirectory, "no data/ocr-eval folder: nothing to evaluate")
        val today = LocalDate.now()
        val codes = sampleCodeTable()
        val out = StringBuilder()
        val engines = OcrEvalSupport.engines()
        for (engine in engines) {
            out.appendLine("################ ${engine.name}")
            for (photo in OcrEvalSupport.photos(engine)) {
                val an = OcrEvalSupport.analyze(engine, photo, today, codes)
                val deg = an.degrees
                val r = an.result
                val retry = an.retried
                val slots = an.slots
                val byStatus = slots.groupingBy { it.status }.eachCount()
                out.appendLine("=== $photo  image=${deg}°  ${r.kind.label}  score=${"%.2f".format(r.kindScore)}  " +
                    "créneaux=${slots.size} (retenus ${byStatus[SlotStatus.KEPT] ?: 0}, à revoir ${byStatus[SlotStatus.TO_REVIEW] ?: 0}, " +
                    "ignorés ${byStatus[SlotStatus.IGNORED] ?: 0})  personnes=${r.people.size}  période=${r.period}  " +
                    "avertissements=${r.warnings.map { it.name }}  relances=${if (retry) "oui" else "non"}")
                val reasons = slots.mapNotNull { it.reviewReason }.groupingBy { it }.eachCount()
                if (reasons.isNotEmpty()) out.appendLine("    raisons : $reasons")
                slots.take(40).forEach { s ->
                    val time = if (s.allDay) "journée" else "${s.start}–${s.end}${if (s.endsNextDay) " (+1 j)" else ""}"
                    out.appendLine("    ${s.status.name.padEnd(9)} ${s.date} ${time.padEnd(18)} ${s.title}" +
                        (s.person?.let { "  [$it]" } ?: "") + "  conf=${"%.2f".format(s.confidence)}")
                }
                if (slots.size > 40) out.appendLine("    … ${slots.size - 40} de plus")
            }
        }
        File(root, "report.txt").writeText(out.toString())
    }
}
