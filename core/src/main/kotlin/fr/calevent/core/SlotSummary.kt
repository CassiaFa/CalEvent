package fr.calevent.core

/** Count of slots per status, e.g. "12 retenus · 3 à revoir". */
data class SlotSummary(val kept: Int, val toReview: Int, val ignored: Int) {

    /** French label. The "à revoir" part is omitted when nothing is to review. */
    val label: String
        get() {
            val keptPart = if (kept > 1) "$kept retenus" else "$kept retenu"
            return if (toReview > 0) "$keptPart · $toReview à revoir" else keptPart
        }

    companion object {
        fun of(slots: List<Slot>): SlotSummary {
            val byStatus = slots.groupingBy { it.status }.eachCount()
            return SlotSummary(
                kept = byStatus[SlotStatus.KEPT] ?: 0,
                toReview = byStatus[SlotStatus.TO_REVIEW] ?: 0,
                ignored = byStatus[SlotStatus.IGNORED] ?: 0,
            )
        }
    }
}

/**
 * Display order: "to review" slots first, then everything else; each group is sorted by
 * date then start time (slots without start time first). Ignored slots are hidden unless
 * [showIgnored] is true.
 */
fun displaySlots(slots: List<Slot>, showIgnored: Boolean = false): List<Slot> =
    slots
        .filter { showIgnored || it.status != SlotStatus.IGNORED }
        .sortedWith(
            compareBy<Slot> { if (it.status == SlotStatus.TO_REVIEW) 0 else 1 }
                .thenBy { it.date }
                .thenBy { it.start ?: java.time.LocalTime.MIN }
                .thenBy { it.id },
        )
