package fr.calevent.core

/** True when two names are the same ignoring case, accents and extra spaces. */
fun sameName(a: String?, b: String?): Boolean = a != null && b != null && a.searchKey() == b.searchKey()

/** First of [people] that is the same name as [name], or null. */
fun findPerson(people: List<String>, name: String?): String? = people.firstOrNull { sameName(it, name) }

/**
 * What the user changed on a slot, kept for the whole scan session.
 * [edited] is the complete slot when more than the decision changed (edits from the slot sheet).
 */
data class SlotChange(val decision: UserDecision?, val edited: Slot?)

/**
 * User changes remembered per slot, so that switching person on a team schedule and coming back
 * restores them. Slots are keyed by their id (person | date | raw code), which is stable across rebuilds.
 */
class SlotMemory private constructor(private val changes: Map<String, SlotChange>) {
    constructor() : this(emptyMap())

    val size: Int get() = changes.size

    /**
     * Records what [current] differs on from [baseline] (the same slots as freshly built from the photo).
     * Entries of other slots are kept; entries of slots in [baseline] are replaced.
     */
    fun capture(baseline: List<Slot>, current: List<Slot>): SlotMemory {
        val next = changes.toMutableMap()
        val currentById = current.associateBy { it.id }
        for (base in baseline) {
            val now = currentById[base.id]
            when {
                now == null || now == base -> next.remove(base.id)
                now.copy(decision = null) == base.copy(decision = null) -> next[base.id] = SlotChange(now.decision, null)
                else -> next[base.id] = SlotChange(now.decision, now)
            }
        }
        return SlotMemory(next)
    }

    /** [baseline] with the remembered decisions and edits applied. */
    fun restore(baseline: List<Slot>): List<Slot> = baseline.map { base ->
        val change = changes[base.id] ?: return@map base
        (change.edited ?: base).copy(decision = change.decision)
    }
}
