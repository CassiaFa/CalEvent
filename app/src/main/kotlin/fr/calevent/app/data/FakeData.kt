package fr.calevent.app.data

import fr.calevent.core.CalendarEvent
import fr.calevent.core.Slot
import fr.calevent.core.toCalendarEvent
import java.time.LocalDate
import java.time.LocalTime

/** Kind of schedule behind a recent planning; drives the thumbnail. */
enum class PlanningKind { WEEKLY, TEAM_CODES, WALL_CALENDAR }

/** One row of the home list "Derniers plannings". Texts are fake French data. */
data class RecentPlanning(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: PlanningKind,
    /** Slots still to review (> 0 shows an "à revoir" chip); otherwise [addedCount] events were added. */
    val toReviewCount: Int = 0,
    val addedCount: Int = 0,
)

/** A calendar of the phone (fake until the calendar provider is wired). [colorArgb] is the user's calendar colour. */
data class DeviceCalendar(val id: Long, val name: String, val account: String, val colorArgb: Long)

/** Coherent fake data matching the mockups (November 2026). Everything is built from `:core` types. */
object FakeData {
    private fun d(day: Int) = LocalDate.of(2026, 11, day)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    /** A printed weekly schedule, 16-29 Nov.: 12 kept, 3 to review (with reasons), 1 ignored. */
    val weeklySlots: List<Slot> = listOf(
        Slot("w01", "Matin (M06)", d(16), t(6), t(14), sourceText = "M06", confidence = 0.96),
        Slot("w02", "Matin (M06)", d(17), t(6), t(14), sourceText = "M06", confidence = 0.95),
        Slot("w03", "Réunion d'équipe", d(18), t(9), t(10, 30), location = "Salle 2", sourceText = "Réunion équipe 9h-10h30", confidence = 0.91),
        Slot("w04", "Préparation", d(19), t(13), t(20), sourceText = "Préparation 13h-20h?", confidence = 0.65, reviewReason = "Heure de fin incertaine"),
        Slot("w05", "Quai réception", d(20), t(6), t(13), location = "Entrepôt Nord", sourceText = "Quai réception 6h-13h", confidence = 0.93),
        Slot("w06", "Inventaire", d(21), t(8), t(12), sourceText = "Inventaire 8h-12h", confidence = 0.88),
        Slot("w07", "Livraison Dupont", d(22), t(10), confidence = 0.72, dateUncertain = true, sourceText = "Livraison Dupont 10h", reviewReason = "Date peu lisible : 22 ou 23 nov."),
        Slot("w08", "Matin (M06)", d(23), t(6), t(14), sourceText = "M06", confidence = 0.94),
        Slot("w09", "Matin (M06)", d(24), t(6), t(14), sourceText = "M06", confidence = 0.94),
        Slot("w10", "Soir (S14)", d(25), t(14), t(22), sourceText = "S14", confidence = 0.9),
        Slot("w11", "Nuit (N08)", d(26), t(21), t(7), endsNextDay = true, sourceText = "N08", confidence = 0.89),
        Slot("w12", "Pot de départ Marc", d(27), sourceText = "Pot de départ Marc", confidence = 0.3, reviewReason = "Date illisible"),
        Slot("w13", "Congés", d(27), allDay = true, sourceText = "CA", confidence = 0.87),
        Slot("w14", "Formation sécurité", d(28), t(9), t(17), sourceText = "Formation sécurité 9h-17h", confidence = 0.92),
        Slot("w15", "Atelier ?", d(28), t(14), sourceText = "Atelier ? 14h", confidence = 0.6, reviewReason = "Titre partiellement lu"),
        Slot("w16", "Garde", d(29), t(8), t(16), sourceText = "Garde 8h-16h", confidence = 0.85),
    )

    /** Home list "Derniers plannings". */
    val recentPlannings: List<RecentPlanning> = listOf(
        RecentPlanning("r1", "Planning hebdo · 16–29 nov.", "Planning imprimé · aujourd'hui 23:41", PlanningKind.WEEKLY, toReviewCount = 3),
        RecentPlanning("r2", "Équipe USC 3e · novembre", "Planning à codes · 8 nov.", PlanningKind.TEAM_CODES, addedCount = 18),
        RecentPlanning("r3", "Club de hand · décembre", "Calendrier mural · 2 nov.", PlanningKind.WALL_CALENDAR, addedCount = 6),
    )

    /** Fake system calendars. */
    val calendars: List<DeviceCalendar> = listOf(
        DeviceCalendar(1, "Personnel", "prenom.nom@gmail.com", 0xFF1D5CA3),
        DeviceCalendar(2, "Travail", "prenom.nom@entreprise.fr", 0xFFA3400B),
        DeviceCalendar(3, "Famille", "Sur l'appareil", 0xFF2E8B57),
    )

    /** Events already in the calendar: the first Monday slot is a duplicate when sending. */
    val existingEvents: List<CalendarEvent> = listOf(weeklySlots.first().toCalendarEvent())
}
