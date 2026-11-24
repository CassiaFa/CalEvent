package fr.calevent.core.parse

import fr.calevent.core.ShiftCodeTable
import fr.calevent.core.sampleCodeTable
import fr.calevent.core.Slot
import fr.calevent.core.SlotStatus
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * Local-only comparison of OCR engines on the private photos (`data/ocr-eval`, never committed), against the exact
 * truths `truth/IMG_2908.tsv` (person, date, start, end) and `truth/IMG_2929.tsv` (person "Surname Firstname",
 * date, code). For every engine folder named `mlkit`, `psm11-kotlin` or `cells*`: slots found, correct, correct
 * kept (Retenu), wrong kept; slot counts for the other photos. Writes `data/ocr-eval/cells-report.txt`.
 */
class TableCellReport {

    private val root = OcrEvalSupport.root
    private val legitCodes = setOf("M06", "M07", "S14", "J08", "N08", "RH", "CA", "AB", "MAL", "G03", "G05", "J09", "RF", "RC", "FOR")

    private fun key(s: String) = s.lowercase().filter { it.isLetter() }

    private fun lev(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length)
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        return d[a.length][b.length]
    }

    private fun samePerson(slot: String?, truth: String): Boolean {
        if (slot == null) return false
        val k = key(slot)
        val surname = key(truth.substringBeforeLast(' '))
        return lev(k, key(truth)) <= 1 || lev(k, surname) <= 1
    }

    private fun rows(name: String) = File(root, "truth/$name.tsv").readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }

    /** Index of the truth row matched by [slot], or null. */
    private fun match(photo: String, slot: Slot): Int? = when (photo) {
        "IMG_2908" -> rows(photo).indexOfFirst { t ->
            slot.date == LocalDate.parse(t[1]) && slot.start == LocalTime.parse(t[2]) && slot.end == LocalTime.parse(t[3]) &&
                samePerson(slot.person, t[0])
        }
        "IMG_2929" -> rows(photo).indexOfFirst { t ->
            slot.date == LocalDate.parse(t[1]) && slot.id.substringAfterLast('|').trim().uppercase() == t[2] &&
                samePerson(slot.person, t[0])
        }
        else -> -2
    }.takeIf { it != -1 }

    @Test
    fun writeReport() {
        assumeTrue(File(root, "truth/IMG_2929.tsv").isFile, "no data/ocr-eval truth: nothing to compare")
        val today = LocalDate.of(2026, 11, 1)
        val codes = sampleCodeTable()
        val engines = OcrEvalSupport.engines().filter { it.name == "mlkit" || it.name == "psm11-kotlin" || it.name.startsWith("cells") }
        val out = StringBuilder()
        out.appendLine("truth photos: found / correct / kept correct / kept wrong (truth: 2908 = ${rows("IMG_2908").size}, 2929 = ${rows("IMG_2929").size})")
        out.appendLine("other photos: slots (kept / review / ignored) people; odd-kept = kept code slots with a code not on the schedules")
        out.appendLine()
        val details = StringBuilder()
        for (engine in engines) {
            out.append(engine.name.padEnd(16))
            details.appendLine("################ ${engine.name}")
            for (photo in OcrEvalSupport.photos(engine)) {
                val an = OcrEvalSupport.analyze(engine, photo, today, codes)
                val slots = an.slots
                val short = photo.removePrefix("IMG_")
                if (photo == "IMG_2908" || photo == "IMG_2929") {
                    val matched = slots.map { match(photo, it) }
                    // A truth row counts once even when two slots match it.
                    val correct = matched.filterNotNull().toSet().size
                    val keptOk = slots.indices.filter { matched[it] != null && slots[it].status == SlotStatus.KEPT }.mapNotNull { matched[it] }.toSet().size
                    val keptBad = slots.indices.count { matched[it] == null && slots[it].status == SlotStatus.KEPT }
                    out.append(" | $short ${slots.size}/$correct/$keptOk/$keptBad")
                    val hit = matched.filterNotNull().toSet()
                    details.appendLine("=== $photo ${an.degrees}° missed: " +
                        rows(photo).indices.filter { it !in hit }.joinToString("; ") { rows(photo)[it].take(4).joinToString(" ") })
                    details.appendLine("=== $photo ${an.degrees}° wrong slots:")
                    slots.indices.filter { matched[it] == null }.forEach { i ->
                        val s = slots[i]
                        details.appendLine("    ${s.status.name.padEnd(9)} ${s.date} ${s.start}–${s.end} ${s.title} [${s.person}] id=${s.id}")
                    }
                } else {
                    val by = slots.groupingBy { it.status }.eachCount()
                    // Team-code photos without truth: kept slots whose code is not one of the schedules' real codes.
                    val odd = if (an.result.kind == ScheduleKind.TEAM_CODES)
                        " odd-kept " + slots.count { it.status == SlotStatus.KEPT && it.id.substringAfterLast('|').trim().uppercase() !in legitCodes }
                    else ""
                    out.append(" | $short ${slots.size} (${by[SlotStatus.KEPT] ?: 0}/${by[SlotStatus.TO_REVIEW] ?: 0}/${by[SlotStatus.IGNORED] ?: 0}) p${an.result.people.size}$odd")
                }
            }
            out.appendLine()
        }
        out.appendLine()
        out.append(details)
        File(root, "cells-report.txt").writeText(out.toString())
    }
}
