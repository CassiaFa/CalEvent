package fr.calevent.core

import java.time.LocalTime

/** Codes used by the tests (the app itself ships with an empty table). */
fun sampleCodeTable(): ShiftCodeTable {
    fun t(h: Int) = LocalTime.of(h, 0)
    return ShiftCodeTable(
        listOf(
            ShiftCode("M06", "Matin", start = t(6), end = t(14)),
            ShiftCode("M07", "Matin", start = t(7), end = t(15)),
            ShiftCode("S14", "Soir", start = t(14), end = t(22)),
            ShiftCode("J08", "Journée", start = t(8), end = t(20)),
            ShiftCode("N08", "Nuit", start = t(21), end = t(7), endsNextDay = true),
            ShiftCode("RH", "Repos", ShiftType.REST, allDay = true),
            ShiftCode("CA", "Congés", ShiftType.REST, allDay = true),
            ShiftCode("AB", "Absence", ShiftType.ABSENCE, allDay = true),
            ShiftCode("MAL", "Absence", ShiftType.ABSENCE, allDay = true),
        ),
    )
}
