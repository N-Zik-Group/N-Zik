package app.n_zik.android.components.ui.screens.home

import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.models.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class HomeSongsProjectionTest {
    private fun song(id: String, title: String = id, artist: String? = null) =
        Song(id, title, artist, durationText = null, thumbnailUrl = null)

    @Test
    fun `preserves sequential clamped insertion and first occurrence before filtering`() = runTest {
        val explicit = song("duplicate", EXPLICIT_PREFIX + "Hidden")
        val input = HomeSongsProjectionInput(
            listOf(song("a"), song("duplicate", "Visible"), song("b")), "", true,
            linkedMapOf(explicit to -8, song("tail") to 100, song("middle") to 2), true,
        )
        assertEquals(listOf("a", "middle", "b", "tail"), projectHomeSongsOnData(input).map { it.id })
        assertEquals(listOf("duplicate", "a", "middle", "b", "tail"),
            projectHomeSongsOnData(input.copy(parentalControlEnabled = false)).map { it.id })
        assertEquals(listOf("a", "duplicate", "b"),
            projectHomeSongsOnData(input.copy(recommendationsEnabled = false)).map { it.id })
    }

    @Test
    fun `matches title or artist ignoring case and handles null artists`() = runTest {
        val input = HomeSongsProjectionInput(
            listOf(song("a", "HELLO"), song("b", "Other", "Hello artist"), song("c", "Other")),
            "hello", false, emptyMap(), false,
        )
        assertEquals(listOf("a", "b"), projectHomeSongsOnData(input).map { it.id })
        assertTrue(projectHomeSongsOnData(input.copy(query = "absent")).isEmpty())
        assertEquals(3, projectHomeSongsOnData(input.copy(query = "")).size)
    }

    @Test
    fun `projection reads song data off the calling thread`() = runTest {
        val caller = Thread.currentThread()
        val reader = AtomicReference<Thread>()
        val items = object : AbstractList<Song>() {
            override val size = 1
            override fun get(index: Int): Song {
                reader.set(Thread.currentThread())
                return song("a")
            }
        }
        assertEquals(listOf("a"), projectHomeSongsOnData(HomeSongsProjectionInput(items, "", false, emptyMap(), false)).map { it.id })
        assertNotEquals(caller, reader.get())
    }

    @Test
    fun `cancelled old projection cannot publish over a newer query`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val slowItems = object : AbstractList<Song>() {
            override val size = 1
            override fun get(index: Int): Song {
                started.complete(Unit)
                check(release.await(10, TimeUnit.SECONDS))
                return song("old")
            }
        }
        val published = mutableListOf<String>()
        val old = launch {
            projectHomeSongsOnData(HomeSongsProjectionInput(slowItems, "old", false, emptyMap(), false))
            published += "old"
        }
        try {
            started.await()
            old.cancel()
            val latest = projectHomeSongsOnData(HomeSongsProjectionInput(listOf(song("new")), "new", false, emptyMap(), false))
            published += latest.single().id
        } finally {
            release.countDown()
            old.cancelAndJoin()
        }
        assertEquals(listOf("new"), published)
    }
}
