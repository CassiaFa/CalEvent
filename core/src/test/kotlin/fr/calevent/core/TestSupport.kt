package fr.calevent.core

import java.time.LocalDate
import java.time.LocalTime

internal fun d(y: Int, m: Int, day: Int): LocalDate = LocalDate.of(y, m, day)
internal fun t(h: Int, min: Int = 0): LocalTime = LocalTime.of(h, min)

internal fun slot(
    id: String = "s",
    title: String = "Matin (M06)",
    date: LocalDate = d(2026, 11, 16),
    start: LocalTime? = t(6),
    end: LocalTime? = t(14),
    confidence: Double = 0.9,
    dateUncertain: Boolean = false,
    allDay: Boolean = false,
    endsNextDay: Boolean = false,
) = Slot(
    id = id, title = title, date = date, start = start, end = end, allDay = allDay,
    endsNextDay = endsNextDay, confidence = confidence, dateUncertain = dateUncertain,
)
