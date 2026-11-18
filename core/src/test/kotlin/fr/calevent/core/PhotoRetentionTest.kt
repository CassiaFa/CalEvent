package fr.calevent.core

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoRetentionTest {
    private val now = 1_800_000_000_000L
    private val day = TimeUnit.DAYS.toMillis(1)

    @Test
    fun `cutoff is now minus the retention`() {
        assertEquals(now - 7 * day, PhotoRetention.DAYS_7.cutoffMillis(now))
        assertEquals(now - 30 * day, PhotoRetention.DAYS_30.cutoffMillis(now))
    }

    @Test
    fun `always keeps everything`() {
        assertNull(PhotoRetention.ALWAYS.cutoffMillis(now))
        assertFalse(PhotoRetention.ALWAYS.isExpired(0L, now))
    }

    @Test
    fun `only strictly older files expire`() {
        assertTrue(PhotoRetention.DAYS_7.isExpired(now - 8 * day, now))
        assertFalse(PhotoRetention.DAYS_7.isExpired(now - 7 * day, now))
        assertFalse(PhotoRetention.DAYS_7.isExpired(now - day, now))
    }
}
