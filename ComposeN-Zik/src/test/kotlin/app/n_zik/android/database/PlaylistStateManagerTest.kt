package app.n_zik.android.database

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.core.database.SongPlaylistMapTable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests [PlaylistStateManager] (and the batch [SongPlaylistMapTable.songsInPlaylists] query
 * it relies on) using Robolectric (JUnit 4 runner) with an in-memory database.
 * Uses JUnit 4 annotations (@Before / @After) because Robolectric's @RunWith is a JUnit 4 concept.
 *
 * Note: SongPlaylistMap has FKs to Song and Playlist → both placeholders must be inserted
 * before inserting any mapping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaylistStateManagerTest {

    private lateinit var db: DatabaseInitializer
    private lateinit var songPlaylistMapDao: SongPlaylistMapTable

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
        songPlaylistMapDao = db.songPlaylistMapTable
    }

    @After
    fun closeDb() {
        db.close()
    }

    /** Insert a minimal Song so that the FK constraint on SongPlaylistMap is satisfied. */
    private fun insertSong(id: String) {
        db.songTable.upsert(Song.makePlaceholder(id))
    }

    /** Insert a local playlist so that the FK constraint on SongPlaylistMap is satisfied. */
    private fun insertPlaylist(id: Long, name: String) {
        db.playlistTable.upsert(Playlist(id = id, name = name))
    }

    @Test
    fun `mapped songs are true while unmapped songs are false`() = runBlocking {
        insertSong("song_a")
        insertSong("song_b")
        insertSong("song_c")
        insertPlaylist(1, "test playlist")

        songPlaylistMapDao.map("song_a", 1)

        val states = PlaylistStateManager.getPlaylistStates(
            listOf("song_a", "song_b", "song_c"),
            songPlaylistMapDao
        ).first()

        // The map covers every requested ID with explicit values
        assertEquals(3, states.size)
        assertEquals(true, states["song_a"])
        assertEquals(false, states["song_b"])
        assertEquals(false, states["song_c"])
    }

    @Test
    fun `song id with no SongPlaylistMap row resolves to false`() = runBlocking {
        insertSong("song_a")
        insertPlaylist(1, "test playlist")

        songPlaylistMapDao.map("song_a", 1)

        val states = PlaylistStateManager.getPlaylistStates(
            listOf("song_a", "ghost_song"),
            songPlaylistMapDao
        ).first()

        assertEquals(true, states["song_a"])
        // An ID with no row in SongPlaylistMap (not even a Song row) is false
        assertEquals(false, states["ghost_song"])
    }

    @Test
    fun `empty list emits an empty map`() = runBlocking {
        val states = PlaylistStateManager.getPlaylistStates(
            emptyList<String>(),
            songPlaylistMapDao
        ).first()

        assertTrue(states.isEmpty())
    }

    @Test
    fun `map re-emits when a song is added to a playlist`() = runBlocking {
        insertSong("song_a")
        insertSong("song_b")
        insertPlaylist(1, "test playlist")

        songPlaylistMapDao.map("song_a", 1)

        val flow = PlaylistStateManager.getPlaylistStates(
            listOf("song_a", "song_b"),
            songPlaylistMapDao
        )

        val firstEmission = CompletableDeferred<Map<String, Boolean>>()
        val secondEmission = CompletableDeferred<Map<String, Boolean>>()

        val collector = launch {
            flow.take(2).collect { states ->
                if (!firstEmission.complete(states)) secondEmission.complete(states)
            }
        }

        val initial = withTimeout(10_000) { firstEmission.await() }
        assertEquals(true, initial["song_a"])
        assertEquals(false, initial["song_b"])

        // Mutation after the first emission
        songPlaylistMapDao.map("song_b", 1)
        // Room delivers table invalidations on the main looper; flush it so the
        // reactive query observes the new row and the flow re-emits
        shadowOf(Looper.getMainLooper()).idle()

        val updated = withTimeout(10_000) { secondEmission.await() }
        assertEquals(true, updated["song_a"])
        assertEquals(true, updated["song_b"])

        collector.cancel()
    }
}
