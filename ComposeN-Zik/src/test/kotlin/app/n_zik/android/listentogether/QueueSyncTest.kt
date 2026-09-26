// Ported from Metrolist (GPL-3.0, https://github.com/MetrolistGroup/Metrolist):
// app/src/test/kotlin/com/metrolist/music/listentogether/QueueSyncTest.kt
// Adapted to JUnit 5 and the app.n_zik.android.listentogether package.
package app.n_zik.android.listentogether

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QueueSyncTest {
    @Test
    fun upcomingQueueNeverReplacesCurrentTrack() {
        val current = TrackInfo(id = "current", title = "Current", artist = "Artist", duration = 1L)
        val suggested = TrackInfo(id = "suggested", title = "Suggested", artist = "Artist", duration = 1L)
        val later = TrackInfo(id = "later", title = "Later", artist = "Artist", duration = 1L)

        val queue = canonicalPlaybackQueue(current, listOf(suggested, later))

        assertEquals(listOf("current", "suggested", "later"), queue.map { it.id })
    }

    @Test
    fun canonicalQueueRemovesCurrentAndDuplicateUpcomingEntries() {
        val current = TrackInfo(id = "current", title = "Current", artist = "Artist", duration = 1L)
        val next = TrackInfo(id = "next", title = "Next", artist = "Artist", duration = 1L)

        val queue = canonicalPlaybackQueue(current, listOf(current, next, next))

        assertEquals(listOf("current", "next"), queue.map { it.id })
    }

    @Test
    fun outgoingQueueExcludesPlaybackHistoryAndCurrentItem() {
        val queue = listOf("played", "current", "suggested", "later")

        assertEquals(listOf("suggested", "later"), upcomingQueueItems(queue, currentIndex = 1))
        assertEquals(emptyList<String>(), upcomingQueueItems(queue, currentIndex = -1))
    }
}
