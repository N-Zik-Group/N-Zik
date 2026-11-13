package app.n_zik.android.components.menu

import app.n_zik.android.models.Song
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuSongsTest {
    @Test
    fun `online-only queue observes edited song without any saved collection`() = runBlocking {
        val first = Song("first", "Old", durationText = "1:00", thumbnailUrl = null)
        val second = first.copy(id = "second", title = "Missing from database")
        val fetched = listOf(first, second, first)
        val rows = MutableStateFlow(listOf(first))
        val requested = mutableListOf<List<String>>()
        val values = Channel<List<Song>?>(Channel.UNLIMITED)
        val job = launch {
            menuSongRows(fetched.map(Song::id)) { ids ->
                requested.add(ids)
                rows
            }.collect { values.send(overlayMenuSongs(fetched, it)) }
        }
        try {
            assertEquals(fetched, withTimeout(5000) { values.receive() })
            val updated = first.copy(title = "New", thumbnailUrl = "new-cover")
            rows.value = listOf(updated)
            assertEquals(listOf(updated, second, updated), withTimeout(5000) { values.receive() })
            assertEquals(listOf(listOf("first", "second")), requested)
        } finally {
            job.cancelAndJoin()
            values.close()
        }
    }

    @Test
    fun `large queues batch identities below SQLite limit and empty queues do not query`() = runBlocking {
        val requested = mutableListOf<List<String>>()
        val ids = (0..1800).map(Int::toString)
        val rows = menuSongRows(ids + ids.take(5)) { batch ->
            requested.add(batch)
            flowOf(batch.map { Song(it, it, durationText = null, thumbnailUrl = null) })
        }.first()
        assertEquals(ids, rows.map(Song::id))
        assertEquals(3, requested.size)
        assertTrue(requested.all { it.size <= 900 })
        assertEquals(emptyList<Song>(), menuSongRows(emptyList()) { error("Empty queue queried") }.first())
    }
}
