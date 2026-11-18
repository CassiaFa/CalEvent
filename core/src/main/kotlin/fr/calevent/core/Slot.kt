package fr.calevent.core

import java.time.LocalDate
import java.time.LocalTime

/** Explicit choice made by the user, overriding the computed [SlotStatus]. */
enum class UserDecision { KEEP, IGNORE }

/** Area of the photo a slot was read from, as fractions (0..1) of the image width and height. */
data class Region(val left: Double, val top: Double, val right: Double, val bottom: Double)

/** A slot detected on the photographed schedule. Immutable. */
data class Slot(
    val id: String,
    val title: String,
    val date: LocalDate,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val allDay: Boolean = false,
    /** Night shift: the end time belongs to the day after [date]. */
    val endsNextDay: Boolean = false,
    val location: String? = null,
    val person: String? = null,
    /** Raw text read on the photo. */
    val sourceText: String = "",
    /** Recognition confidence, 0..1. */
    val confidence: Double,
    val dateUncertain: Boolean = false,
    /** French explanation shown to the user, e.g. "Heure de fin incertaine". */
    val reviewReason: String? = null,
    val decision: UserDecision? = null,
    /** Where the slot was read on the photo, if known. */
    val region: Region? = null,
) {
    init {
        require(confidence in 0.0..1.0) { "confidence must be within 0..1: $confidence" }
    }

    /** Status shown to the user: the user's decision wins over the computed one. */
    val status: SlotStatus
        get() = when (decision) {
            UserDecision.KEEP -> SlotStatus.KEPT
            UserDecision.IGNORE -> SlotStatus.IGNORED
            null -> SlotStatus.from(confidence, dateUncertain)
        }

    /** Only KEPT slots are selected by default (and sent to the calendar). */
    val isSelected: Boolean get() = status == SlotStatus.KEPT

    fun keep(): Slot = copy(decision = UserDecision.KEEP)
    fun ignore(): Slot = copy(decision = UserDecision.IGNORE)
}
