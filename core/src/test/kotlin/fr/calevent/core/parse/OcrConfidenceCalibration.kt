package fr.calevent.core.parse

import fr.calevent.core.ShiftCodeTable
import fr.calevent.core.Slot
import fr.calevent.core.SlotStatus
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * Local-only calibration of the OCR-confidence aggregation (private photos in `data/`, never committed).
 * Labels every slot produced from `data/ocr-eval/<engine>/` as correct or not, replays the parser with
 * several aggregation rules and writes precision / recall of the "Retenu" status to
 * `data/ocr-eval/calibration.txt`. Skipped when the folder does not exist.
 *
 * Labels: photo 2908 against its exact truth (`truth/IMG_2908.tsv`: person, date, start, end);
 * team-code photos 2929-2931 by a proxy (the code read is one of the schedule's real codes);
 * the other photos are only counted.
 */
class OcrConfidenceCalibration {

    private val root = OcrEvalSupport.root
    private val engines = listOf("mlkit", "psm11-kotlin")
    private val legitCodes = setOf("M06", "M07", "S14", "J08", "N08", "RH", "CA", "AB", "MAL", "G03", "G05", "J09", "RF", "RC", "FOR")

    private class Candidate(val name: String, val f: (List<Double>) -> Double)

    private class Row(val photo: String, val id: String, val label: Boolean?, val keepable: Boolean, val conf: Map<String, Double>, val status: Map<String, SlotStatus>)

    private fun mean(v: List<Double>) = v.average()
    private fun median(v: List<Double>): Double = v.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
    private fun affine(lo: Double, hi: Double, base: (List<Double>) -> Double): (List<Double>) -> Double =
        { v -> ((base(v) - lo) / (hi - lo)).coerceIn(0.0, 1.0) }

    private fun candidates(): List<Candidate> {
        val bases = listOf<Pair<String, (List<Double>) -> Double>>(
            "min" to { v -> v.min() },
            "mean" to ::mean,
            "median" to ::median,
            "min/mean" to { v -> 0.5 * v.min() + 0.5 * v.average() },
        )
        val list = ArrayList<Candidate>()
        list += Candidate("PRODUCTION", OcrConfidence::calibrate)
        bases.forEach { (n, f) -> list += Candidate(n, f) }
        for (cap in listOf(0.1, 0.25)) for (k in listOf(1.0, 1.2, 1.3)) {
            list += Candidate("guard(mean<=min+$cap x$k)", { v -> (minOf(v.average(), v.min() + cap) * k).coerceIn(0.0, 1.0) })
        }
        for ((n, f) in bases) for (k in listOf(1.1, 1.15, 1.2, 1.25, 1.3, 1.4)) {
            list += Candidate("scale($n x$k)", { v -> (f(v) * k).coerceIn(0.0, 1.0) })
        }
        for ((n, f) in bases) for (lo in listOf(0.0, 0.3, 0.5)) for (hi in listOf(0.6, 0.65, 0.7, 0.75, 0.8, 0.9)) {
            list += Candidate("affine($n, $lo..$hi)", affine(lo, hi, f))
        }
        return list
    }

    private fun lev(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length)
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        return d[a.length][b.length]
    }

    private class Truth(val person: String, val date: LocalDate, val start: LocalTime, val end: LocalTime)

    private fun truth2908(): List<Truth> = File(root, "truth/IMG_2908.tsv").readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { l -> l.split('\t').let { Truth(it[0], LocalDate.parse(it[1]), LocalTime.parse(it[2]), LocalTime.parse(it[3])) } }

    private fun key(s: String) = s.lowercase().filter { it.isLetter() }

    /** null: photo not labelled. */
    private fun label(photo: String, slot: Slot, truth: List<Truth>): Boolean? = when (photo) {
        "IMG_2908" -> truth.any { t ->
            slot.date == t.date && slot.start == t.start && slot.end == t.end &&
                slot.person != null && lev(key(slot.person), key(t.person)) <= 1
        }
        "IMG_2929", "IMG_2930", "IMG_2931" -> slot.id.substringAfterLast('|').trim().uppercase() in legitCodes
        else -> null
    }

    @Test
    fun writeCalibration() {
        assumeTrue(root.isDirectory && File(root, "truth/IMG_2908.tsv").isFile, "no data/ocr-eval folder: nothing to calibrate")
        val today = LocalDate.of(2026, 11, 1)
        val codes = ShiftCodeTable.default()
        val truth = truth2908()
        val cands = candidates()
        val out = StringBuilder()
        val saved = OcrConfidence.aggregate
        try {
            for (engineName in engines) {
                val engine = File(root, engineName)
                if (!engine.isDirectory) continue
                out.appendLine("################ $engineName")
                val rows = ArrayList<Row>()
                val perCand = HashMap<String, Map<String, Slot>>()
                val photos = OcrEvalSupport.photos(engine)
                val slotsBy = HashMap<String, MutableMap<String, Slot>>()
                for (c in cands) {
                    OcrConfidence.aggregate = c.f
                    for (photo in photos) {
                        OcrEvalSupport.analyze(engine, photo, today, codes).slots.forEach { s ->
                            slotsBy.getOrPut(c.name) { HashMap() }["$photo#${s.id}"] = s
                        }
                    }
                }
                val ids = slotsBy.getValue(cands[0].name).keys.sorted()
                for (k in ids) {
                    val photo = k.substringBefore('#')
                    val base = slotsBy.getValue(cands[0].name).getValue(k)
                    val lab = label(photo, base, truth)
                    val keepable = base.unknownCode == null
                    rows += Row(photo, k, lab, keepable,
                        cands.associate { it.name to (slotsBy.getValue(it.name)[k]?.confidence ?: Double.NaN) },
                        cands.associate { it.name to (slotsBy.getValue(it.name)[k]?.status ?: SlotStatus.IGNORED) })
                }
                val labelled = rows.filter { it.label != null }
                out.appendLine("labelled slots: ${labelled.size} (correct ${labelled.count { it.label == true }}, wrong ${labelled.count { it.label == false }}); " +
                    "correct and keepable (known code, not capped): ${labelled.count { it.label == true && it.keepable }}")
                out.appendLine()
                out.appendLine("candidate".padEnd(34) + "keptOK keptBAD  prec   recall | 2908 ok/bad kept (of ok/bad) | codes ok/bad kept | unlabelled kept (2909,2910)")
                val summary = cands.map { c ->
                    fun kept(r: Row) = r.status.getValue(c.name) == SlotStatus.KEPT
                    val ok = labelled.filter { it.label == true && it.keepable }
                    val keptOk = ok.count(::kept)
                    val keptBad = labelled.filter { it.label == false }.count(::kept)
                    val l08 = labelled.filter { it.photo == "IMG_2908" }
                    val ok08 = l08.filter { it.label == true }
                    val bad08 = l08.filter { it.label == false }
                    val lc = labelled.filter { it.photo != "IMG_2908" }
                    val unl = rows.filter { it.label == null }.count(::kept)
                    out.appendLine(c.name.padEnd(34) + "%6d %7d  %.2f   %.2f  | %2d/%-2d of %d/%d | %3d/%-3d | %d".format(
                        keptOk, keptBad, if (keptOk + keptBad == 0) 1.0 else keptOk.toDouble() / (keptOk + keptBad), keptOk.toDouble() / ok.size.coerceAtLeast(1),
                        ok08.count(::kept), bad08.count(::kept), ok08.size, bad08.size,
                        lc.filter { it.label == true }.count(::kept), lc.filter { it.label == false }.count(::kept), unl))
                    Triple(c, keptOk, keptBad)
                }
                out.appendLine()
                out.appendLine("--- status per photo (kept ok / kept bad / review ok / review bad / ignored ok / ignored bad; unlabelled: kept/review/ignored)")
                for (c in cands.filter { it.name in setOf("PRODUCTION", "min", "mean") }) {
                    out.appendLine(c.name)
                    for (photo in photos) {
                        val pr = rows.filter { it.photo == photo }
                        fun n(st: SlotStatus, l: Boolean?) = pr.count { it.status.getValue(c.name) == st && it.label == l }
                        out.appendLine("   $photo  " + if (pr.firstOrNull()?.label == null)
                            "${n(SlotStatus.KEPT, null)} / ${n(SlotStatus.TO_REVIEW, null)} / ${n(SlotStatus.IGNORED, null)}"
                        else SlotStatus.values().joinToString(" / ") { "${n(it, true)} ${n(it, false)}" })
                    }
                }
                out.appendLine()
                out.appendLine("--- labelled slots (final conf under PRODUCTION / min / mean; the doubt ceiling applies to all)")
                for (r in labelled.sortedWith(compareBy({ it.photo }, { it.label }, { it.conf["PRODUCTION"] }))) {
                    out.appendLine("   ${r.id.padEnd(40).take(40)} ${if (r.label == true) "ok " else "BAD"}${if (r.keepable) " " else "u"} " +
                        listOf("PRODUCTION", "min", "mean").joinToString(" ") { "%.2f".format(r.conf.getValue(it)) })
                }
                out.appendLine()
            }
        } finally {
            OcrConfidence.aggregate = saved
        }
        File(root, "calibration.txt").writeText(out.toString())
    }
}
