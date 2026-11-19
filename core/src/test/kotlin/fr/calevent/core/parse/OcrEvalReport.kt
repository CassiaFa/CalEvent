package fr.calevent.core.parse

import fr.calevent.core.ShiftCodeTable
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

    private val root = File("../data/ocr-eval")

    @Test
    fun writeReport() {
        assumeTrue(root.isDirectory, "no data/ocr-eval folder: nothing to evaluate")
        val today = LocalDate.now()
        val codes = ShiftCodeTable.default()
        val out = StringBuilder()
        val engines = root.listFiles { f -> f.isDirectory && f.listFiles { j -> j.name.endsWith("_0.json") }?.isNotEmpty() == true }!!.sortedBy { it.name }
        for (engine in engines) {
            out.appendLine("################ ${engine.name}")
            val photos = engine.listFiles { f -> f.name.endsWith("_0.json") }!!.map { it.name.removeSuffix("_0.json") }.sorted()
            for (photo in photos) {
                fun attempt(deg: Int): Pair<Int, ParseResult>? = File(engine, "${photo}_$deg.json").takeIf { it.isFile }
                    ?.let { deg to ScheduleParser.parse(RecognizedTextJson.read(it.readText()), today, codes) }
                fun score(r: ParseResult) = r.slots.size + (r.team?.cells?.size ?: 0) + r.kindScore
                val upright = attempt(0)!!
                val r0 = upright.second
                val lines0 = RecognizedTextJson.read(File(engine, "${photo}_0.json").readText()).lines.size
                val retry = r0.rotationDegrees != 0 || ParseWarning.NO_SLOTS in r0.warnings ||
                    ParseWarning.ROTATED_QUARTER_TURN in r0.warnings || ParseWarning.UPSIDE_DOWN in r0.warnings || lines0 < 6
                var best = upright
                if (retry) for (deg in intArrayOf(90, 180, 270)) {
                    val a = attempt(deg) ?: continue
                    if (score(a.second) > score(best.second)) best = a
                }
                val (deg, r) = best
                val slots: List<Slot> = if (r.kind.isTeam) r.people.flatMap { r.slotsFor(it, codes) } else r.slotsFor(null, codes)
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
