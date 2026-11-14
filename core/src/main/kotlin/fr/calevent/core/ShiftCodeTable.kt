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

/** Immutable, editable code table: every edit returns a new table. Order is preserved. */
class ShiftCodeTable private constructor(private val entries: Map<String, ShiftCode>) {

    constructor(codes: List<ShiftCode> = emptyList()) : this(
        LinkedHashMap<String, ShiftCode>().also { map ->
            codes.forEach { c ->
                require(map.put(c.code.compactKey(), c) == null) { "duplicate code ${c.code}" }
            }
        },
    )

    val codes: List<ShiftCode> get() = entries.values.toList()

    /** Case- and whitespace-insensitive lookup ("m 06" finds "M06"). */
    fun find(raw: String): ShiftCode? = entries[raw.compactKey()]

    fun add(code: ShiftCode): ShiftCodeTable {
        require(find(code.code) == null) { "code ${code.code} already exists" }
        return ShiftCodeTable(entries + (code.code.compactKey() to code))
    }

    /** Replaces the entry found under [existingCode] (the code itself may be renamed). */
    fun update(existingCode: String, newValue: ShiftCode): ShiftCodeTable {
        val oldKey = existingCode.compactKey()
        require(oldKey in entries) { "unknown code $existingCode" }
        val newKey = newValue.code.compactKey()
        require(newKey == oldKey || newKey !in entries) { "code ${newValue.code} already exists" }
        val result = LinkedHashMap<String, ShiftCode>()
        entries.forEach { (k, v) -> if (k == oldKey) result[newKey] = newValue else result[k] = v }
        return ShiftCodeTable(result)
    }

    fun remove(code: String): ShiftCodeTable = ShiftCodeTable(entries - code.compactKey())

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
                reviewReason = "Code inconnu : $trimmed",
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
            ),
        )
    }
}
