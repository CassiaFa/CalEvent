package fr.calevent.core.parse

import fr.calevent.core.ShiftCodeTable
import fr.calevent.core.Slot
import java.io.File
import java.time.LocalDate

/** Shared by the local OCR evaluation tests: replays the app's orientation choice on stored dumps. */
internal object OcrEvalSupport {
    val root = File("../data/ocr-eval")

    class Analysis(val degrees: Int, val result: ParseResult, val retried: Boolean, val slots: List<Slot>)

    fun engines(): List<File> =
        root.listFiles { f -> f.isDirectory && f.listFiles { j -> j.name.endsWith("_0.json") }?.isNotEmpty() == true }!!.sortedBy { it.name }

    fun photos(engine: File): List<String> =
        engine.listFiles { f -> f.name.endsWith("_0.json") }!!.map { it.name.removeSuffix("_0.json") }.sorted()

    /** Upright first, then the other quarter turns when the result looks wrong, as `ScanAnalyzer` does. */
    fun analyze(engine: File, photo: String, today: LocalDate, codes: ShiftCodeTable): Analysis {
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
        val slots = if (r.kind.isTeam) r.people.flatMap { r.slotsFor(it, codes) } else r.slotsFor(null, codes)
        return Analysis(deg, r, retry, slots)
    }
}
