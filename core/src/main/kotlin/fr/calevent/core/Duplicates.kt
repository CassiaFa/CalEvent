package fr.calevent.core

/** Slots to send, split from those whose event already exists in the calendar. */
data class DuplicateSplit(val toCreate: List<Slot>, val alreadyPresent: List<Slot>) {

    /** True when there were slots and all of them already exist. */
    val allPresent: Boolean get() = toCreate.isEmpty() && alreadyPresent.isNotEmpty()

    /** "2 créneaux déjà présents, ignorés"; null when there is no duplicate. */
    val label: String?
        get() = when (alreadyPresent.size) {
            0 -> null
            1 -> "1 créneau déjà présent, ignoré"
            else -> "${alreadyPresent.size} créneaux déjà présents, ignorés"
        }

    /** Message for the "everything is already there" case; null otherwise. */
    val allPresentLabel: String? get() = if (allPresent) "Tout est déjà dans l'agenda" else null
}

private data class EventKey(val start: java.time.LocalDateTime, val end: java.time.LocalDateTime, val allDay: Boolean, val title: String)

private fun CalendarEvent.key() = EventKey(start, end, allDay, title.compactKey())

/**
 * Same start, same end, same allDay and same title once normalized (case, spaces, accents)
 * means "already present".
 */
fun splitDuplicates(slots: List<Slot>, existing: List<CalendarEvent>): DuplicateSplit {
    val known = existing.mapTo(HashSet()) { it.key() }
    val (dups, fresh) = slots.partition { it.toCalendarEvent().key() in known }
    return DuplicateSplit(toCreate = fresh, alreadyPresent = dups)
}
