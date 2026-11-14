package fr.calevent.core

import java.text.Normalizer

private val COMBINING_MARKS = Regex("\\p{M}+")
private val WHITESPACE = Regex("\\s+")

private fun String.stripAccents(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

/** Lower-case, accent-free, whitespace-free key: "M 06" and "m06" give the same key. */
internal fun String.compactKey(): String =
    stripAccents().lowercase().replace(WHITESPACE, "")

/** Lower-case, accent-free, single-spaced, trimmed text (for names). */
internal fun String.searchKey(): String =
    stripAccents().lowercase().trim().replace(WHITESPACE, " ")
