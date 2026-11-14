package fr.calevent.core

/** What will happen when the user taps "send". */
data class SendPlan(
    val events: List<CalendarEvent>,
    val duplicates: DuplicateSplit,
    val selectedCount: Int,
) {
    /** False when nothing is selected or every selected slot already exists. */
    val canSend: Boolean get() = events.isNotEmpty()

    /** "Ajouter 21 créneaux à l'agenda". */
    val sendLabel: String
        get() = if (events.size == 1) "Ajouter 1 créneau à l'agenda"
        else "Ajouter ${events.size} créneaux à l'agenda"

    companion object {
        fun build(slots: List<Slot>, existing: List<CalendarEvent>): SendPlan {
            val selected = slots.filter { it.isSelected }
            val split = splitDuplicates(selected, existing)
            return SendPlan(split.toCreate.map { it.toCalendarEvent() }, split, selected.size)
        }
    }
}
