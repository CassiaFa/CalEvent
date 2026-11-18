package fr.calevent.core

import java.time.LocalDate
import java.time.LocalTime

/** REST covers any non-worked all-day code that keeps its label (rest day, leave). */
enum class ShiftType { WORK, REST, ABSENCE }

/** One entry of the code table. A night shift is a WORK code with [endsNextDay]. */
data class ShiftCode(
    val code: String,
    val label: String,
    val type: ShiftType = ShiftType.WORK,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val allDay: Boolean = false,
    val endsNextDay: Boolean = false,
) {
    init {
        require(code.isNotBlank()) { "code must not be blank" }
        if (type == ShiftType.WORK && !allDay) {
            require(start != null && end != null) { "work code $code needs start and end times" }
        }
    }
}

/** Applies the table rules: an absence is a bare all-day "Absence"; a rest is all day. */
fun ShiftCode.normalized(): ShiftCode = when (type) {
    ShiftType.ABSENCE -> copy(label = ShiftCodeTable.ABSENCE_TITLE, start = null, end = null, allDay = true, endsNextDay = false)
    ShiftType.REST -> copy(start = null, end = null, allDay = true, endsNextDay = false)
    ShiftType.WORK -> if (allDay) copy(start = null, end = null, endsNextDay = false) else this
}

/**
 * Recomputes the slots whose code is now in the table (after the user defined it), without reading
 * the photo again. Other slots are returned unchanged; decisions, regions and dates are kept.
 */
fun ShiftCodeTable.resolveUnknownCodes(slots: List<Slot>): List<Slot> = slots.map { old ->
    val raw = old.unknownCode
    if (raw == null || find(raw) == null) return@map old
    toSlot(raw, old.date, old.person, old.uncappedConfidence ?: 1.0, old.id).copy(
        decision = old.decision, region = old.region, location = old.location, dateUncertain = old.dateUncertain,
    )
}

/** Distinct unknown codes still present in [slots], in order of first appearance. */
fun unknownCodesOf(slots: List<Slot>, table: ShiftCodeTable): List<String> =
    slots.mapNotNull { it.unknownCode }.filter { table.find(it) == null }.distinctBy { it.filter { c -> !c.isWhitespace() }.uppercase() }

/** Immutable, editable code table: every edit returns a new table. Order is preserved. */
class ShiftCodeTable private constructor(private val entries: Map<String, ShiftCode>) {

    constructor(codes: List<ShiftCode> = emptyList()) : this(
        LinkedHashMap<String, ShiftCode>().also { map ->
            codes.forEach { c ->
                require(map.put(c.code.compactKey(), c) == null) { "duplicate code ${c.code}" }
            }
        },
    )

    /** Entries in a stable order: the order they were added (defaults first). */
    val codes: List<ShiftCode> get() = entries.values.toList()

    val size: Int get() = entries.size

    override fun equals(other: Any?): Boolean = other is ShiftCodeTable && other.codes == codes
    override fun hashCode(): Int = codes.hashCode()

    /** Case- and whitespace-insensitive lookup ("m 06" finds "M06"). */
    fun find(raw: String): ShiftCode? = entries[raw.compactKey()]

    fun add(code: ShiftCode): ShiftCodeTable {
        require(find(code.code) == null) { "code ${code.code} already exists" }
        return ShiftCodeTable(entries + (code.code.compactKey() to code.normalized()))
    }

    /** Adds the entry, or replaces the one with the same code (keeping its position). */
    fun with(code: ShiftCode): ShiftCodeTable =
        if (find(code.code) == null) add(code) else update(code.code, code)

    /** Removes [code] if present; same as [remove] (immutable-style name). */
    fun without(code: String): ShiftCodeTable = remove(code)

    /** Replaces the entry found under [existingCode] (the code itself may be renamed). */
    fun update(existingCode: String, newValue: ShiftCode): ShiftCodeTable {
        val oldKey = existingCode.compactKey()
        require(oldKey in entries) { "unknown code $existingCode" }
        val newKey = newValue.code.compactKey()
        require(newKey == oldKey || newKey !in entries) { "code ${newValue.code} already exists" }
        val result = LinkedHashMap<String, ShiftCode>()
        entries.forEach { (k, v) -> if (k == oldKey) result[newKey] = newValue.normalized() else result[k] = v }
        return ShiftCodeTable(result)
    }

    fun remove(code: String): ShiftCodeTable = ShiftCodeTable(entries - code.compactKey())

    /**
     * Text form, one entry per line: `code<TAB>label<TAB>TYPE<TAB>start<TAB>end<TAB>allDay<TAB>endsNextDay`
     * (times as HH:mm or empty, flags as 0/1). Tabs and line breaks inside fields become spaces.
     */
    fun serialize(): String = entries.values.joinToString("\n") { c ->
        listOf(
            c.code.clean(), c.label.clean(), c.type.name, c.start?.toString().orEmpty(), c.end?.toString().orEmpty(),
            if (c.allDay) "1" else "0", if (c.endsNextDay) "1" else "0",
        ).joinToString("\t")
    }

    /**
     * Translates a raw code read on a schedule into a [Slot].
     * - work: "Matin (M06)", times from the table; night: "Nuit (N08)", ends the next day
     * - rest / leave: the table label ("Repos", "Congés"), all day
     * - absence: "Absence", all day, no code and no source text (medical confidentiality)
     * - unknown code: raw code as title, forced to "to review" with a written reason
     */
    fun toSlot(
        rawCode: String,
        date: LocalDate,
        person: String? = null,
        confidence: Double = 1.0,
        id: String = "${person.orEmpty()}|$date|$rawCode",
    ): Slot {
        val entry = find(rawCode)
        val trimmed = rawCode.trim()
        return when (entry?.type) {
            null -> Slot(
                id = id, title = trimmed, date = date, person = person, sourceText = trimmed,
                // Capped below the "kept" threshold so an unknown code is never pre-selected.
                confidence = minOf(confidence, UNKNOWN_CODE_CONFIDENCE),
                reviewReason = "$UNKNOWN_PREFIX$trimmed",
                unknownCode = trimmed, uncappedConfidence = confidence,
            )
            ShiftType.ABSENCE -> Slot(
                id = id, title = ABSENCE_TITLE, date = date, allDay = true, person = person,
                sourceText = "", confidence = confidence,
            )
            ShiftType.REST -> Slot(
                id = id, title = entry.label, date = date, allDay = true, person = person,
                sourceText = trimmed, confidence = confidence,
            )
            ShiftType.WORK -> Slot(
                id = id, title = "${entry.label} (${entry.code})", date = date,
                start = entry.start.takeUnless { entry.allDay },
                end = entry.end.takeUnless { entry.allDay },
                allDay = entry.allDay, endsNextDay = entry.endsNextDay && !entry.allDay,
                person = person, sourceText = trimmed, confidence = confidence,
            )
        }
    }

    companion object {
        const val ABSENCE_TITLE = "Absence"
        const val UNKNOWN_CODE_CONFIDENCE = 0.7
        const val UNKNOWN_PREFIX = "Code inconnu : "

        private fun String.clean() = replace(Regex("[\\t\\r\\n]+"), " ").trim()

        /**
         * Reads the text produced by [serialize]. Invalid or duplicate lines are skipped; blank or null
         * input gives an empty table. Use [default] yourself when nothing was stored.
         */
        fun parse(text: String?): ShiftCodeTable {
            val list = ArrayList<ShiftCode>()
            val seen = HashSet<String>()
            text.orEmpty().lines().forEach { line ->
                val f = line.split('\t')
                if (f.size < 7) return@forEach
                val code = runCatching {
                    ShiftCode(
                        code = f[0], label = f[1].ifBlank { f[0] }, type = ShiftType.valueOf(f[2]),
                        start = f[3].takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
                        end = f[4].takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
                        allDay = f[5] == "1", endsNextDay = f[6] == "1",
                    ).normalized()
                }.getOrNull() ?: return@forEach
                if (seen.add(code.code.compactKey())) list += code
            }
            return ShiftCodeTable(list)
        }

        private fun t(h: Int) = LocalTime.of(h, 0)

        /** Defaults taken from the "Codes de planning" mockup. */
        fun default(): ShiftCodeTable = ShiftCodeTable(
            listOf(
                ShiftCode("M06", "Matin", start = t(6), end = t(14)),
                ShiftCode("M07", "Matin", start = t(7), end = t(15)),
                ShiftCode("S14", "Soir", start = t(14), end = t(22)),
                ShiftCode("J08", "Journée", start = t(8), end = t(20)),
                ShiftCode("N08", "Nuit", start = t(21), end = t(7), endsNextDay = true),
                ShiftCode("RH", "Repos", ShiftType.REST, allDay = true),
                // Leave is not a medical detail: it keeps its own label.
                ShiftCode("CA", "Congés", ShiftType.REST, allDay = true),
                ShiftCode("AB", "Absence", ShiftType.ABSENCE, allDay = true),
                // Sick leave: always shown as a bare "Absence", never the medical code.
                ShiftCode("MAL", "Absence", ShiftType.ABSENCE, allDay = true),
            ),
        )
    }
}
