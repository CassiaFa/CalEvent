package fr.calevent.core.parse

import fr.calevent.core.ScheduleCell
import fr.calevent.core.Slot
import fr.calevent.core.SlotStatus
import fr.calevent.core.TeamSchedule
import kotlin.math.abs
import kotlin.math.min

/** One day column: its x position in the working frame, header tokens and what they say. */
internal data class HeaderColumn(val x: Double, val tokens: List<Token>, val header: HeaderDate)

/** Row of day headers; [pitch] is the median distance between columns. */
internal data class DateHeader(val columns: List<HeaderColumn>, val top: Double, val bottom: Double, val pitch: Double)

/** A person row label (name, possibly on two lines: surname then first name). */
private data class PersonRow(val name: String, val tokens: List<Token>, val top: Double, val bottom: Double) {
    fun distanceTo(y: Double): Double = when {
        y < top -> top - y
        y > bottom -> y - bottom
        else -> 0.0
    }
}

/** Vertically stacked tokens of one table cell (e.g. "8h30 à 17h30" above "Entrepôt Nord"). */
private data class CellBlock(val column: Int, val tokens: List<Token>, val dxRatio: Double) {
    val text: String get() = tokens.joinToString(" ") { it.text }
    val top: Double get() = tokens.minOf { it.box.top }
    val bottom: Double get() = tokens.maxOf { it.box.bottom }
    val centerY: Double get() = (top + bottom) / 2
}

/** How much a row of header candidates looks like a day header: strong words plus consecutive days. */
private fun headerScore(row: List<Token>): Double {
    val parsed = row.sortedBy { it.box.centerX }.map { parseHeaderDate(it.text) }
    val strong = parsed.count { it.strong }
    val consecutive = parsed.zipWithNext().count { (a, b) ->
        val d1 = a.day
        val d2 = b.day
        d1 != null && d2 != null && (d2 == d1 + 1 || (d2 == 1 && d1 >= 28))
    }
    return (strong + consecutive).toDouble()
}

/**
 * Finds the row of day headers ("LUNDI", "19/10", "L25", "22 nov.") and attaches the header lines
 * just above or below it to their column ("L25" + "Jan" + "S04").
 */
internal fun findDateHeader(tokens: List<Token>, h: Double): DateHeader? {
    val candidates = tokens.filter { isHeaderCandidate(it.text) }
    val best = clusterBy(candidates, 0.8 * h) { it.box.centerY }
        .filter { it.size >= 3 }
        .maxByOrNull(::headerScore) ?: return null
    if (headerScore(best) < 2.5) return null
    // Perspective can lift the far columns: absorb candidates a bit off the row that add a column.
    val meanY = best.map { it.box.centerY }.average()
    val roughPitch = best.sortedBy { it.box.centerX }.zipWithNext { a, b -> b.box.centerX - a.box.centerX }.median()
    val extra = candidates.filter { c ->
        c !in best && abs(c.box.centerY - meanY) < 1.6 * h &&
            best.none { abs(it.box.centerX - c.box.centerX) < 0.5 * roughPitch }
    }

    val sorted = (best + extra).sortedBy { it.box.centerX }
    val pitch = sorted.zipWithNext { a, b -> b.box.centerX - a.box.centerX }.median()
    val groups = ArrayList<MutableList<Token>>()
    for (t in sorted) {
        val last = groups.lastOrNull()
        if (last != null && t.box.centerX - last.last().box.centerX < 0.4 * pitch) last += t else groups += mutableListOf(t)
    }
    val xs = groups.map { g -> g.map { it.box.centerX }.average() }
    val top = sorted.minOf { it.box.top }
    val bottom = sorted.maxOf { it.box.bottom }
    val inBest = sorted.toSet()
    for (t in tokens) {
        if (t in inBest || t.text.length > 16) continue
        if (t.box.centerY < top - 2.5 * h || t.box.centerY > bottom + 2.5 * h) continue
        if (parseHeaderDate(t.text).isEmpty) continue
        val k = nearestIndex(xs, t.box.centerX)
        if (abs(xs[k] - t.box.centerX) < 0.4 * pitch) groups[k] += t
    }
    val columns = groups.mapIndexed { k, g ->
        val ordered = g.sortedBy { it.box.centerY }
        HeaderColumn(xs[k], ordered, parseHeaderDate(ordered.joinToString(" ") { it.text }))
    }
    val all = groups.flatten()
    return DateHeader(columns, all.minOf { it.box.top }, all.maxOf { it.box.bottom }, pitch)
}

private val NAME_SHAPE = Regex("""^[\p{L}][\p{L} .'’\-]*$""")

/** A row label: letters only (no digits, no times), at most 5 words. */
private fun looksLikeName(text: String): Boolean {
    val t = text.trim()
    return t.length >= 2 && NAME_SHAPE.matches(t) && t.split(' ').size <= 5 && normWord(t) !in WEEKDAY_WORDS
}

private fun groupPeople(labels: List<Token>, h: Double): List<PersonRow> {
    val rows = ArrayList<MutableList<Token>>()
    for (t in labels.sortedBy { it.box.centerY }) {
        val cur = rows.lastOrNull()
        val prev = cur?.last()
        val stacked = prev != null && t.box.top - prev.box.bottom < 1.1 * h &&
            (min(t.box.right, prev.box.right) > maxOf(t.box.left, prev.box.left) || abs(t.box.left - prev.box.left) < 3 * h)
        if (stacked && cur!!.size < 3) cur += t else rows += mutableListOf(t)
    }
    return rows.map { r ->
        PersonRow(r.joinToString(" ") { it.text.trim() }, r, r.minOf { it.box.top }, r.maxOf { it.box.bottom })
    }
}

/**
 * Schedules with a row of dated columns: team schedules (a name column on the left, codes or hours
 * in the cells) and weekly schedules (events listed under each day).
 */
internal fun analyzeDateGrid(tokens: List<Token>, ctx: Context): Analysis? {
    val h = ctx.lineHeight
    val header = findDateHeader(tokens, h) ?: return null
    val titleLines = tokens.filter { it.box.bottom < header.top }.sortedBy { it.box.centerY }.map { it.text }
    val dates = resolveColumnDates(header.columns.map { it.header }, parseTitle(titleLines), ctx.today)
    val certain = dates.count { it.date != null && !it.uncertain }.toDouble() / dates.size
    val x0 = header.columns.first().x
    val xN = header.columns.last().x
    val pitch = header.pitch
    val below = tokens.filter { it.box.centerY > header.bottom + 0.2 * h }
    val labels = below.filter {
        it.box.centerX < x0 - 0.5 * pitch && it.box.right < x0 - 0.3 * pitch && looksLikeName(it.text)
    }
    val people = groupPeople(labels, h)
    val labelSet = labels.toSet()
    val data = below.filter { it !in labelSet && it.box.centerX > x0 - 0.5 * pitch && it.box.centerX < xN + 0.5 * pitch }
    val colScore = min(1.0, header.columns.size / 5.0)
    return if (people.size >= 2) analyzeTeam(header, dates, people, data, ctx, colScore, certain)
    else analyzeWeekly(header, dates, data, ctx, colScore, certain)
}

private fun cellBlocks(header: DateHeader, data: List<Token>, h: Double): List<CellBlock> {
    val xs = header.columns.map { it.x }
    val byColumn = data.mapNotNull { t ->
        val k = nearestIndex(xs, t.box.centerX)
        val ratio = abs(xs[k] - t.box.centerX) / header.pitch
        if (ratio > 0.55) null else Triple(k, t, ratio)
    }.groupBy { it.first }
    return byColumn.flatMap { (k, items) ->
        val blocks = ArrayList<MutableList<Triple<Int, Token, Double>>>()
        for (it in items.sortedBy { it.second.box.centerY }) {
            val cur = blocks.lastOrNull()
            if (cur != null && it.second.box.top - cur.maxOf { c -> c.second.box.bottom } < 1.0 * h) cur += it
            else blocks += mutableListOf(it)
        }
        blocks.map { b -> CellBlock(k, b.map { it.second }, b.maxOf { it.third }) }
    }
}

private fun analyzeTeam(
    header: DateHeader, dates: List<ColumnDate>, people: List<PersonRow>, data: List<Token>,
    ctx: Context, colScore: Double, certain: Double,
): Analysis {
    val h = ctx.lineHeight
    val rowPitch = people.zipWithNext { a, b -> b.top - a.top }.median().coerceAtLeast(2 * h)
    val blocks = cellBlocks(header, data, h)
    // Assign each block to the closest person row.
    val placed = blocks.mapNotNull { b ->
        val p = people.indices.minBy { people[it].distanceTo(b.centerY) }
        val dist = people[p].distanceTo(b.centerY)
        if (dist > 0.5 * rowPitch) null else Triple(p, b, dist / rowPitch)
    }
    val timed = placed.count { parseTimes(it.second.text).ranges.isNotEmpty() }
    val coded = placed.count { looksLikeCode(it.second.text, ctx.codes) }
    val kind = if (timed > coded) ScheduleKind.TEAM_HOURS else ScheduleKind.TEAM_CODES
    val assigned = placed.size.toDouble() / blocks.size.coerceAtLeast(1)
    val score = 0.2 * colScore + 0.2 * min(1.0, people.size / 3.0) + 0.3 * assigned + 0.3 * certain
    val names = people.map { it.name }

    fun penalties(dxRatio: Double, dyRatio: Double, crowded: Boolean) = buildList {
        if (dxRatio > 0.3 || dyRatio > 0.3) add(0.85 to REASON_POSITION)
        if (crowded) add(0.8 to REASON_AMBIGUOUS_CELL)
    }

    val byCell = placed.groupBy { it.first to it.second.column }
    if (kind == ScheduleKind.TEAM_HOURS) {
        val slots = ArrayList<Slot>()
        byCell.forEach { (key, items) ->
            val (p, col) = key
            items.forEachIndexed { i, (_, block, dy) ->
                slots += buildSlots(
                    id = "${names[p]}|${dates[col].date}|$i", text = block.text, tokens = block.tokens,
                    date = dates[col], ctx = ctx, person = names[p], team = true,
                    penalties = penalties(block.dxRatio, dy, false),
                )
            }
        }
        return Analysis(kind, score, slots.sortedWith(compareBy<Slot>({ names.indexOf(it.person) }, { it.date }, { it.start })), people = names)
    }

    val cells = ArrayList<ScheduleCell>()
    val details = HashMap<String, CellDetail>()
    byCell.forEach { (key, items) ->
        val (p, col) = key
        val date = dates[col]
        val day = date.date ?: return@forEach
        val (_, block, dy) = items.minBy { it.third }
        val (code, corrected) = fixCode(block.text, ctx.codes)
        val ev = Evidence(block.tokens.minOf { it.confidence })
        penalties(block.dxRatio, dy, items.size > 1).forEach { (f, r) -> ev.penalize(f, r) }
        if (corrected) ev.penalize(0.9, REASON_CODE_CORRECTED)
        // TeamSchedule has no "uncertain date" flag: keep such cells below the "kept" threshold.
        val confidence = if (date.uncertain) min(ev.confidence, SlotStatus.KEPT_THRESHOLD - 0.01) else ev.confidence
        cells += ScheduleCell(names[p], day, code, confidence)
        details[ParseResult.cellKey(names[p], day, code)] = CellDetail(
            regionOf(block.tokens, ctx.image.width, ctx.image.height),
            date.uncertain,
            ev.reason(date.reason.takeIf { date.uncertain }),
        )
    }
    cells.sortWith(compareBy({ names.indexOf(it.person) }, { it.date }))
    return Analysis(kind, score, team = TeamSchedule(names, cells), people = names, details = details)
}

/** A printed weekly schedule: events listed under each dated column, with or without times. */
private fun analyzeWeekly(
    header: DateHeader, dates: List<ColumnDate>, data: List<Token>, ctx: Context, colScore: Double, certain: Double,
): Analysis? {
    val h = ctx.lineHeight
    val xs = header.columns.map { it.x }
    val inColumns = data.mapNotNull { t ->
        val k = nearestIndex(xs, t.box.centerX)
        if (abs(xs[k] - t.box.centerX) > 0.5 * header.pitch) null else k to t
    }
    if (inColumns.isEmpty()) return null
    val slots = ArrayList<Slot>()
    var timedTokens = 0
    inColumns.groupBy({ it.first }, { it.second }).forEach { (k, colTokens) ->
        val events = groupEvents(colTokens.sortedBy { it.box.centerY }, h)
        timedTokens += events.count { e -> parseTimes(e.first().text).ranges.isNotEmpty() }
        events.forEachIndexed { i, e ->
            slots += buildSlots("week|${dates[k].date}|$i", e.joinToString(" ") { it.text }, e, dates[k], ctx)
        }
    }
    val timedFraction = timedTokens.toDouble() / inColumns.size
    val score = 0.25 * colScore + 0.35 * certain + 0.4 * timedFraction
    return Analysis(ScheduleKind.WEEKLY, score, slots.sortedWith(compareBy({ it.date }, { it.start })))
}

/**
 * Groups the lines of one day into events: a line with a time starts an event; a line without a
 * time right below a time-only line ("8h–12h" then "Réunion") completes it.
 */
internal fun groupEvents(lines: List<Token>, h: Double): List<List<Token>> {
    val events = ArrayList<MutableList<Token>>()
    for (t in lines) {
        val timed = parseTimes(t.text)
        val cur = events.lastOrNull()
        val prevTimed = cur?.let { parseTimes(it.first().text) }
        val gap = cur?.let { t.box.top - it.last().box.bottom } ?: Double.MAX_VALUE
        val continues = timed.ranges.isEmpty() && prevTimed != null && prevTimed.ranges.isNotEmpty() &&
            gap < 0.8 * h && (prevTimed.rest.isEmpty() || gap < 0.4 * h)
        if (continues) cur!! += t else events += mutableListOf(t)
    }
    return events
}
