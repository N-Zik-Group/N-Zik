package app.n_zik.android.components.ui.screens.home

import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.enums.BuiltInPlaylist
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.Song
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
    fun `song origin filter is limited to synchronized favorites and disliked songs`() {
        val tabs = BuiltInPlaylist.entries
        assertEquals(
            setOf(BuiltInPlaylist.Favorites, BuiltInPlaylist.Disliked),
            tabs.filter { it.hasSongOriginFilter(youtubeSyncEnabled = true) }.toSet(),
        )
        assertTrue(tabs.none { it.hasSongOriginFilter(youtubeSyncEnabled = false) })
    }

    @Test
    fun `all keeps its sort order while adding only scanned device songs`() {
        val songs = listOf(
            song("remote-played").copy(totalPlayTimeMs = 1L),
            song("local:1"),
            song("remote-hidden"),
            song("local:2"),
        )
        assertEquals(
            listOf("remote-played", "local:1"),
            projectHomeLibrarySongs(songs, setOf("local:1"), excludeHidden = true).map(Song::id),
        )
        assertEquals(
            listOf("remote-played", "remote-hidden"),
            projectHomeLibrarySongs(songs, emptySet()).map(Song::id),
        )
    }

    @Test
    fun `device songs join tabs based on library activity without counting as cached or downloaded`() {
        assertEquals(
            setOf(BuiltInPlaylist.All, BuiltInPlaylist.Favorites, BuiltInPlaylist.Disliked, BuiltInPlaylist.Top),
            BuiltInPlaylist.entries.filter { it.includesDeviceLibrarySongs() }.toSet(),
        )
    }

    @Test
    fun `preserves sequential clamped insertion and first occurrence before filtering`() = runTest {
        val explicit = song("duplicate", EXPLICIT_PREFIX + "Hidden")
        val input = HomeSongsBaseInput(
            listOf(song("a"), song("duplicate", "Visible"), song("b")), true,
            linkedMapOf(explicit to -8, song("tail") to 100, song("middle") to 2), true,
        )
        assertEquals(listOf("a", "middle", "b", "tail"), projectHomeSongsBase(input).map { it.id })
        assertEquals(listOf("duplicate", "a", "middle", "b", "tail"),
            projectHomeSongsBase(input.copy(parentalControlEnabled = false)).map { it.id })
        assertEquals(listOf("a", "duplicate", "b"),
            projectHomeSongsBase(input.copy(recommendationsEnabled = false)).map { it.id })
    }

    @Test
    fun `recommendations stay outside the device group in the displayed and playback order`() = runTest {
        val scanned = setOf("local:1", "local:2")
        val library = projectHomeLibrarySongs(
            listOf(song("a"), song("local:1"), song("local:2"), song("local:removed"), song("b")),
            scanned,
        )
        val input = HomeSongsBaseInput(
            library, true, mapOf(song("recommendation") to 2), false, SortOrder.Ascending,
        )
        assertEquals(listOf("local:1", "local:2", "a", "recommendation", "b"), projectHomeSongsBase(input).map(Song::id))
        assertEquals(listOf("a", "recommendation", "b", "local:1", "local:2"),
            projectHomeSongsBase(input.copy(deviceSortOrder = SortOrder.Descending)).map(Song::id))
    }

    @Test
    fun `matches title or artist ignoring case and handles null artists`() = runTest {
        val input = HomeSongsBaseInput(
            listOf(song("a", "HELLO"), song("b", "Other", "Hello artist"), song("c", "Other")),
            false, emptyMap(), false,
        )
        val base = projectHomeSongsBase(input)
        assertEquals(listOf("a", "b"), searchHomeSongs(base, "hello").map { it.id })
        assertTrue(searchHomeSongs(base, "absent").isEmpty())
        assertEquals(3, searchHomeSongs(base, "").size)
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
        assertEquals(listOf("a"), projectHomeSongsBase(HomeSongsBaseInput(items, false, emptyMap(), false)).map { it.id })
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
            searchHomeSongs(slowItems, "old")
            published += "old"
        }
        try {
            started.await()
            old.cancel()
            val latest = searchHomeSongs(listOf(song("new")), "new")
            published += latest.single().id
        } finally {
            release.countDown()
            old.cancelAndJoin()
        }
        assertEquals(listOf("new"), published)
    }
}
