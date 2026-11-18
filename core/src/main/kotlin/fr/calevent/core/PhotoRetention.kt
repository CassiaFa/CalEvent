package fr.calevent.core

import java.util.concurrent.TimeUnit

/** How long captured photos are kept on the phone (Réglages › Confidentialité). */
enum class PhotoRetention(val days: Int?) {
    DAYS_7(7),
    DAYS_30(30),
    ALWAYS(null);

    /** Files last modified strictly before the returned instant (epoch millis) must be deleted; null keeps everything. */
    fun cutoffMillis(nowMillis: Long): Long? = days?.let { nowMillis - TimeUnit.DAYS.toMillis(it.toLong()) }

    fun isExpired(lastModifiedMillis: Long, nowMillis: Long): Boolean =
        cutoffMillis(nowMillis)?.let { lastModifiedMillis < it } ?: false
}
