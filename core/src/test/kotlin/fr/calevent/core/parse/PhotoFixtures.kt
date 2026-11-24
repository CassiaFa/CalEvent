package fr.calevent.core.parse

import fr.calevent.core.ShiftCode
import fr.calevent.core.ShiftCodeTable
import fr.calevent.core.sampleCodeTable
import fr.calevent.core.ShiftType
import java.time.LocalTime

/**
 * Hand-written OCR fixtures reproducing the STRUCTURE of the user's real photos (layout, date and
 * time formats, codes, distortions). Every name, place and title is invented.
 */
internal object PhotoFixtures {

    /** Default table plus the extra codes seen on the care-home schedules. */
    val careCodes: ShiftCodeTable = sampleCodeTable()
        .add(ShiftCode("G03", "Garde", start = LocalTime.of(7, 0), end = LocalTime.of(19, 0)))
        .add(ShiftCode("G05", "Garde", start = LocalTime.of(9, 0), end = LocalTime.of(21, 0)))
        .add(ShiftCode("J09", "Journée", start = LocalTime.of(9, 0), end = LocalTime.of(17, 0)))
        .add(ShiftCode("RF", "Repos férié", ShiftType.REST, allDay = true))
        .add(ShiftCode("RC", "Repos compensateur", ShiftType.REST, allDay = true))

    // ---------------------------------------------------------------------------------------------
    // Photo 1: weekly team schedule, hours "8h30 à 17h30" + place, headers "LUNDI" / "19/10" (no year),
    // title "PLANNING SEMAINE 43", Monday to Saturday, 9 people, landscape, slight tilt.

    val weekHoursPeople = listOf(
        "Mme Aubert", "M. Blanc", "Mme Caron", "M. Dumas", "M. Esnault", "M. Fabre", "Mme Gilles", "M. Hamel", "M. Ibert",
    )

    val weekHoursCells = listOf(
        listOf("8h30 à 17h30|Dépôt Nord", "", "", "8h30 à 17h30|Quai 12", "22h à 6h|Dépôt Nord", ""),
        listOf("l4h à 22h|Dépôt Sud", "8h30 à 17h30|Quai B", "", "9h à 18h|Site Est", "", "9h à 18h|Hangar C"),
        listOf("6h à 14h|Dépôt Sud", "7h30 a 12h30|Site Ouest", "22h à 6h|Quai 12", "", "", "13h à 17h|Chantier A7"),
        listOf("9h à 18h|Hangar C", "8h à 16h|Site Ouest", "8h30 à 17h30|Chantier A7", "", "7h30 à 12h30|Quai B", ""),
        listOf("", "6h à 14h|Dépôt Nord", "5h à 13h|Site Est", "", "6h à 14h|Quai B", ""),
        listOf("8h à 16h|Quai 12", "8h à 16h|Site Est", "7h30 à 12h3O|Zone logistique 3", "6h à 14h|Hangar Sud", "", "7h30 à 12h30|Zone logistique 3"),
        listOf("14h à 22h|Site Ouest", "7h30 à 12h30|Hangar Sud", "13h à 17h|Dépôt Sud", "22h à 6h|Chantier A7", "5h à 13h|Quai B", ""),
        listOf("", "", "", "14h à 22h|Quai 12", "", "8h30 à 17h30|Atelier 2"),
        listOf("", "", "8h30 à 17h30|Dépôt Nord", "", "8h à 16h|Chantier Gare", ""),
    )

    fun weekHours(tilt: Double = 0.4): RecognizedText {
        val spec = TableSpec(
            labelX = 222.0, firstColX = 472.0, pitch = 170.0, headerY = 292.0, headerLineGap = 26.0, headerH = 11.0,
            firstRowY = 364.0, rowPitch = 70.0, textH = 11.0, subH = 8.0, stackedNames = false,
        )
        return OcrSheet(1600, 1200)
            .text("TRANSLOG — Personnel intérimaire", 220.0, 208.0, 9.0)
            .centered("PLANNING SEMAINE 43", 815.0, 250.0, 16.0)
            .teamTable(
                spec, "Équipe",
                listOf("LUNDI|19/10", "MARDI|20/10", "MERCREDI|21/10", "JEUDI|22/10", "VENDREDI|23/10", "SAMEDI|24/10"),
                weekHoursPeople, weekHoursCells,
                // "Atelier 2" on the last row is badly printed.
                conf = { r, c, line -> if (r == 7 && c == 5 && line == 1) 0.55 else 0.95 },
            )
            .text("Toute modification doit être signalée avant 16h la veille.", 208.0, 990.0, 9.0)
            .centered("lot test - page 1/20", 820.0, 1155.0, 9.0, conf = 0.4)
            .build(tiltDegrees = tilt)
    }

    // ---------------------------------------------------------------------------------------------
    // Photo 2: weekly team schedule, "13:00 à 17:00" + place, headers "Lun" / "22 nov.", title with
    // full date and year, Monday to Sunday, 8 people; last column drawn higher (perspective) and two
    // neighbouring cells merged into one OCR line.

    val weekColonPeople = listOf(
        "Mme Arnaud", "M. Bonnet", "M. Collin", "Mme Delmas", "M. Etienne", "Mme Fournier", "M. Garnier", "M. Henry",
    )

    val weekColonCells = listOf(
        listOf("13:00 à 17:00|Dépôt Sud", "O7:30 à 12:30|Hangar Sud", "", "", "13:00 à 17:00|Atelier 2", "", "22:00 à 06:00|Dépôt Sud"),
        listOf("05:00 à 13:00|Chantier Gare", "", "07:30 à 12:30|Chantier A7", "06:00 à 14:00|Site Ouest", "22:00 a 06:00|Hangar C", "", "13:00 à 17:00|Chantier A7"),
        listOf("08:30 à 17:30|Dépôt Nord", "09:00 à 18:00|Atelier 2", "", "13:00 à 17:00|Quai 12", "", "", "06:00 à 14:00|Dépôt Nord"),
        listOf("09:00 à 18:00|Chantier Gare", "06:00 à 14:00|Hangar C", "05:00 à 13:00|Dépôt Sud", "05:00 à 13:00|Quai B", "", "", ""),
        listOf("07:30 à 12:30|Atelier 2", "08:30 à 17:30|Chantier A7", "05:00 à 13:00|Chantier Gare", "05:00 à 13:00|Atelier 2", "", "", "05:00 à 13:00|Dépôt Sud"),
        listOf("22:00 à 06:00|Dépôt Sud", "", "", "06:00 à 14:00|Chantier A7", "07:30 à 12:30|Site Ouest", "07:30 à 12:30|Atelier 2", "05:00 à 13:00|Chantier Gare"),
        listOf("07:30 à 12:30|Atelier 2", "22:00 à 06:00|Site Est", "06:00 à 14:00|Hangar Sud", "08:30 à 17:30|Site Est", "", "", "22:00 à 06:00|Hangar Sud"),
        listOf("08:00 à 16:00|Dépôt Nord", "05:00 à 13:00|Atelier 2", "08:30 à 17:30|Atelier 2", "", "22:00 à 06:00|Dépôt Nord", "05:00 à 13:00|Quai 12", ""),
    )

    fun weekColon(): RecognizedText {
        val spec = TableSpec(
            labelX = 330.0, firstColX = 551.0, pitch = 113.5, headerY = 257.0, headerLineGap = 29.0, headerH = 12.0,
            firstRowY = 341.0, rowPitch = 80.4, textH = 12.0, subH = 9.0, stackedNames = false,
            columnDy = { c -> if (c == 6) -12.0 else 0.0 },
        )
        val sheet = OcrSheet(1600, 1200)
            .text("AGENCE EST — Personnel intérimaire", 325.0, 176.0, 9.0)
            .centered("Planning du 22 novembre 2027", 805.0, 215.0, 18.0)
            .teamTable(
                spec, "Équipe",
                listOf("Lun|22 nov.", "Mar|23 nov.", "Mer|24 nov.", "Jeu|25 nov.", "Ven|26 nov.", "Sam|27 nov.", "Dim|28 nov."),
                weekColonPeople, weekColonCells,
            )
        // M. Etienne, Friday and Saturday: two cells read as a single OCR line.
        val y = 341.0 + 4 * 80.4 - 0.6 * 12.0
        sheet.centered("09:00 à 18:00", 551.0 + 4 * 113.5, y, 12.0)
            .centered("13:00 à 17:00", 551.0 + 5 * 113.5, y, 12.0)
            .mergeLast(2)
            .centered("Chantier A7", 551.0 + 4 * 113.5, y + 1.35 * 12.0, 9.0)
            .centered("Site Est", 551.0 + 5 * 113.5, y + 1.35 * 12.0, 9.0)
            .text("Contact agence : 04 00 00 00 00", 312.0, 965.0, 9.0)
        return sheet.build(tiltDegrees = -0.8)
    }

    // ---------------------------------------------------------------------------------------------
    // Photo 3: month wall calendar, title "Septembre 2025", "Lun".."Dim", day numbers top-right of
    // each cell, events "17h Séminaire" / "12h45 Yoga" / untimed "Réunion équipe".

    /** Day of September 2025 to events written in its cell. */
    val wallEvents: Map<Int, List<String>> = mapOf(
        2 to listOf("17h Séminaire", "7h Entretien"),
        6 to listOf("18h Maintenance", "l2h45 Yoga"),
        7 to listOf("10h30 Match de foot"),
        8 to listOf("15h Déjeuner Paul", "9h Garage"),
        10 to listOf("18h Cinéma"),
        14 to listOf("12h Dentiste", "17h15 Conseil de classe", "15h Séminaire"),
        17 to listOf("Réunion équipe", "10h30 Réunion équipe"),
        18 to listOf("Atelier", "9h Appel fournisseur"),
        20 to listOf("10h15 Inventaire"),
        25 to listOf("9h30 Point client"),
        26 to listOf("Dentiste"),
        27 to listOf("9h15 Atelier"),
        29 to listOf("RDV médecin"),
    )

    fun wallCalendar(): RecognizedText {
        val colX = listOf(307.0, 478.0, 648.0, 817.0, 986.0, 1153.0, 1321.0)
        val rowY = listOf(330.0, 455.0, 578.0, 704.0, 831.0)
        val sheet = OcrSheet(1600, 1200).text("Septembre 2025", 232.0, 250.0, 22.0)
        listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim").forEachIndexed { i, d -> sheet.centered(d, colX[i], 300.0, 13.0) }
        for (day in 1..30) {
            val r = (day - 1) / 7
            val c = (day - 1) % 7
            when (day) {
                23 -> {} // number not read by OCR
                14 -> sheet.right("l4", colX[c] + 82.0, rowY[r], 12.0)
                else -> sheet.right("$day", colX[c] + 82.0, rowY[r], 12.0)
            }
            wallEvents[day]?.forEachIndexed { i, e -> sheet.text(e, colX[c] - 80.0, rowY[r] + 25.0 + 24.0 * i, 11.0) }
        }
        return sheet.text("Calendrier familial", 205.0, 980.0, 8.0).build(tiltDegrees = 0.3)
    }

    // ---------------------------------------------------------------------------------------------
    // Photos 4 to 6: care-home team schedules with codes. Headers "L25" + "Jan" (Monday: week
    // number "S04" instead of the month), "NOM / PRENOM" with surname above first name.

    private fun codeSpec(rows: Int) = TableSpec(
        labelX = 60.0, firstColX = 290.0, pitch = 88.0, headerY = 110.0, headerLineGap = 26.0, headerH = 16.0,
        firstRowY = 175.0, rowPitch = (1150.0 - 175.0) / rows, textH = 16.0, subH = 13.0, stackedNames = true,
    )

    /** Headers "L25|S04", "M26|Jan", … for consecutive days; Mondays carry the ISO week number. */
    fun codeHeaders(firstDay: java.time.LocalDate, count: Int, monthLabels: Map<Int, String>): List<String> =
        (0 until count).map { i ->
            val d = firstDay.plusDays(i.toLong())
            val letter = "LMMJVSD"[d.dayOfWeek.value - 1]
            val second = if (d.dayOfWeek.value == 1) "S%02d".format(d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR))
            else monthLabels.getValue(d.monthValue)
            "%s%02d|%s".format(letter, d.dayOfMonth, second)
        }

    val janFebPeople = listOf(
        "Garnier|Paul", "Leblanc|Sophie", "Morel|Thomas", "Perrin|Julie", "Roux|Antoine", "Vidal|Claire", "Lambert|Hugo", "Masson|Léa",
    )

    val janFebCells: List<List<String>> = listOf(
        "J09 RH - G03 RC N08 M06 M06 S14 S14 S14 S14 G03 RH RH",
        "G05 G05 RF RF RF N08 RH RH GO3 N08 N08 - G05 M06 M06",
        "CA CA CA CA CA RF - G03 G05 G05 G05 RH RH RH S14",
        "RH - G03 RH RH G03 J09 J09 J09 M06 MO6 N08 N08 RH RH",
        "MAL MAL MAL RC RH RH N08 S14 S14 S14 G03 G03 G03 RH RH",
        "S14 G03 G03 S14 S14 G03 RH G03 J09 RH J09 J09 J09 J09 J09",
        "G03 - RH G03 S14 S14 S14 G03 G03 N08 M06 M06 RH RH RH",
        "N08 N08 - G03 N08 M06 M06 G03 G03 G05 RH RH RF RF G03",
    ).map { row -> row.split(' ').map { if (it == "-") "" else it } }

    /** Photo 4: January to February 2027, photographed upside down. */
    fun janFebUpsideDown(): RecognizedText {
        val sheet = OcrSheet(1600, 1200)
            .text("Planning EHPAD Les Glycines — Janvier 2027 / Février 2027", 60.0, 50.0, 16.0)
            .teamTable(
                codeSpec(janFebPeople.size), "NOM / PRENOM",
                codeHeaders(java.time.LocalDate.of(2027, 1, 25), 15, mapOf(1 to "Jan", 2 to "Fév")),
                janFebPeople, janFebCells,
            )
            // Show-through from the back of the sheet: faint, mirrored, low confidence.
            .text("ɐɯɯoɔ sǝʇou", 900.0, 30.0, 12.0, conf = 0.2)
        return sheet.build(rotation = 180, tiltDegrees = 0.6)
    }

    val januaryPeople = listOf(
        "Bertin|Hugo", "Joly|Inès", "Hoarau|Lucas", "Benard|Nina", "Guyot|Marc", "Lacroix|Mathis",
        "Rey-Duval|Agnès", "Navarro|Carole", "Noël|Inès", "",
    )

    val januaryCells: List<List<String>> = listOf(
        "G03 M06 N08 N08 M06 M06 G03 M06 N08 N08 J09 J09 RH G03 -",
        "M06 M06 M06 J09 N08 RH RH G03 G03 RH CA CA CA CA CA",
        "G05 G05 G05 - - G05 G05 G05 RH RH M06 M06 MAL MAL MAL",
        "G03 G03 G03 RH S14 S14 RH RH RH G05 RH RH RH J09 J09",
        "G03 J09 J09 J09 J09 G05 G05 G05 RH RH RH M06 M06 M06 S14",
        "S14 G05 GO5 RH M06 S14 - NO8 CA CA CA CA CA CA CA",
        "M06 MAL MAL MAL MAL RF N08 N08 N08 N08 RH RH RH S14 S14",
        "RF RF RF S14 MAL MAL MAL MAL MAL MAL MAL G05 RC M06 -",
        "- - - - - - - - - - - - - RH RH",
        "- - - - - - - - - - - - - - -",
    ).map { row -> row.split(' ').map { if (it == "-") "" else it } }

    /** Photo 5: January 2027, sheet photographed a quarter turn (text reads top to bottom). */
    fun januaryQuarterTurn(): RecognizedText = OcrSheet(1600, 1200)
        .text("Planning EHPAD Les Acacias — Janvier 2027", 60.0, 50.0, 16.0)
        .teamTable(
            codeSpec(januaryPeople.size), "NOM / PRENOM",
            codeHeaders(java.time.LocalDate.of(2027, 1, 4), 15, mapOf(1 to "Jan")),
            januaryPeople, januaryCells,
        )
        .build(rotation = 90, tiltDegrees = -0.5)

    val augustPeople = listOf(
        "Joly|Inès", "Hoarau|Lucas", "Lacroix|Mathis", "Navarro|Carole", "Guyot|Marc", "Rey-Duval|Agnès", "Martin|Yasmine", "Bertin|Hugo",
    )

    val augustCells: List<List<String>> = listOf(
        "N08 N08 N08 S14 S14 S14 RC G03 G03 FOR FOR RH - CA CA",
        "M06 S14 G05 J09 J09 RF J09 RF S14 N08 N08 RH G03 RH -",
        "S14 S14 S14 N08 S14 N08 G05 G03 G03 M06 M06 M06 RH G03 J09",
        "M06 M06 M06 S14 RH RH N08 N08 N08 MAL MAL MAL RH RH RH",
        "RF M06 S14 G03 G03 G03 G05 RH G03 G03 G03 G03 RH RH RH",
        "G05 G05 G05 S14 S14 S14 RH RH RH G03 S14 S14 RH G05 G05",
        "G03 G03 G03 RF G05 N08 N08 N08 G03 G03 G03 S14 S14 RC RC",
        "J09 G03 G03 G03 M06 RH M06 M06 M06 G03 G03 G03 S14 S14 S14",
    ).map { row -> row.split(' ').map { if (it == "-") "" else it } }

    /** Photo 6: August 2027, quarter turn the other way, and no OCR angles reported. */
    fun augustQuarterTurnNoAngles(): RecognizedText = OcrSheet(1600, 1200)
        .text("Planning Clinique Saint-Ange — Août 2027", 60.0, 50.0, 16.0)
        .teamTable(
            codeSpec(augustPeople.size), "NOM / PRENOM",
            codeHeaders(java.time.LocalDate.of(2027, 8, 16), 15, mapOf(8 to "Aoû")),
            augustPeople, augustCells,
        )
        .build(rotation = 270, withAngles = false)
}
