package fr.calevent.core

import java.time.LocalDate

/** One successful send, kept locally for the home list "Derniers plannings". */
data class HistoryEntry(
    val id: String,
    val createdAtMillis: Long,
    /** Detected kind of schedule ("Calendrier mural", ...), null when unknown. */
    val kindLabel: String?,
    /** True for a team schedule; [personName] is then the chosen person. */
    val isTeam: Boolean,
    val personName: String?,
    val addedCount: Int,
    val calendarName: String,
    val from: LocalDate,
    val to: LocalDate,
    /** False once the thumbnail was deleted (photo retention, "Supprimer toutes les photos"). */
    val hasThumbnail: Boolean,
    /** Ids of the written calendar events (undo). */
    val eventIds: List<Long>,
)

object History {
    const val MAX_ENTRIES = 20

    /** Newest first, at most [MAX_ENTRIES]. */
    fun normalize(entries: List<HistoryEntry>): List<HistoryEntry> =
        entries.sortedByDescending { it.createdAtMillis }.take(MAX_ENTRIES)

    fun add(entries: List<HistoryEntry>, entry: HistoryEntry): List<HistoryEntry> =
        normalize(entries.filter { it.id != entry.id } + entry)

    /** Entries whose thumbnail must go under [retention]. */
    fun expiredThumbnails(entries: List<HistoryEntry>, retention: PhotoRetention, nowMillis: Long): List<HistoryEntry> =
        entries.filter { it.hasThumbnail && retention.isExpired(it.createdAtMillis, nowMillis) }

    private val MONTHS = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")

    private fun day(d: LocalDate) = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"

    /** "16 nov." · "16–29 nov." · "28 nov. – 3 déc." · with the year when the range crosses years. */
    fun dateRangeLabel(from: LocalDate, to: LocalDate): String {
        val (a, b) = if (to.isBefore(from)) to to from else from to to
        return when {
            a == b -> day(a)
            a.year != b.year -> "${day(a)} ${a.year} – ${day(b)} ${b.year}"
            a.month == b.month -> "${a.dayOfMonth}–${day(b)}"
            else -> "${day(a)} – ${day(b)}"
        }
    }
}
