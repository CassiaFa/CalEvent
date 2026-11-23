package fr.calevent.core

/**
 * The user's own name ("Moi"): [last] name and [first] name (either may be blank, but not both).
 * A legacy single string is read as [last], which then holds whatever label the user typed.
 */
data class PersonName(val last: String, val first: String = "") {
    /** "First LAST" as shown in the settings. */
    val display: String get() = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ")

    /** Single-string form kept in the settings; null when both parts are blank. */
    fun encode(): String? {
        val l = last.trim()
        val f = first.trim()
        return when {
            l.isEmpty() && f.isEmpty() -> null
            f.isEmpty() -> l
            else -> l + SEPARATOR + f
        }
    }

    companion object {
        private const val SEPARATOR = '\u001F'

        fun decode(encoded: String?): PersonName? {
            if (encoded.isNullOrBlank()) return null
            val i = encoded.indexOf(SEPARATOR)
            return if (i < 0) PersonName(encoded.trim()) else PersonName(encoded.substring(0, i).trim(), encoded.substring(i + 1).trim())
        }
    }
}

private val TITLES = setOf("m", "mr", "mme", "mlle", "madame", "monsieur", "mademoiselle", "dr")
private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

/** Words of a label: lower case, no accents, punctuation as separator. */
private fun nameTokens(s: String): List<String> = s.searchKey().split(NON_WORD).filter { it.isNotEmpty() }

/**
 * How well the detected [label] (e.g. "NOM Prénom", "Prénom N.", "M. NOM", "NOM") designates [me]:
 * 100 full match, 90 full match plus extra words, 80 one part plus the initial of the other,
 * 60 surname alone (first name known), 50 surname plus unrelated words (first name unknown),
 * 40 first name alone, 0 not this person.
 */
internal fun nameMatchScore(label: String, me: PersonName): Int {
    val lastT = nameTokens(me.last)
    val firstT = nameTokens(me.first)
    if (lastT.isEmpty() && firstT.isEmpty()) return 0
    val raw = nameTokens(label)
    // A title only counts in first position: in "DURAND M." the "m" is an initial.
    val t = if (raw.size > 1 && raw[0] in TITLES && raw[0] !in lastT && raw[0] !in firstT) raw.drop(1) else raw
    if (t.isEmpty()) return 0
    val hasLast = lastT.isNotEmpty() && t.containsAll(lastT)
    val hasFirst = firstT.isNotEmpty() && t.containsAll(firstT)
    val rem = t.toMutableList()
    if (hasLast) lastT.forEach { rem.remove(it) }
    if (hasFirst) firstT.forEach { rem.remove(it) }
    val firstInitial = firstT.firstOrNull()?.take(1)
    val lastInitial = lastT.firstOrNull()?.take(1)
    return when {
        hasLast && hasFirst -> if (rem.isEmpty()) 100 else 90
        hasLast -> when {
            rem.isEmpty() -> if (firstT.isEmpty()) 100 else 60
            rem.size == 1 && rem[0].length == 1 && rem[0] == firstInitial -> 80
            firstT.isEmpty() -> 50
            else -> 0
        }
        hasFirst -> when {
            rem.isEmpty() -> if (lastT.isEmpty()) 100 else 40
            rem.size == 1 && rem[0].length == 1 && rem[0] == lastInitial -> 80
            else -> 0
        }
        else -> 0
    }
}

/**
 * The person of [people] designated by [me], or null when none matches or when several people
 * share the best score (nobody is preselected rather than guessing).
 */
fun findPersonByName(people: List<String>, me: PersonName?): String? {
    if (me == null) return null
    val scored = people.map { it to nameMatchScore(it, me) }.filter { it.second > 0 }
    val best = scored.maxOfOrNull { it.second } ?: return null
    return scored.filter { it.second == best }.singleOrNull()?.first
}
