package app.n_zik.android.core.migration

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.utils.TestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric + Room tests of [MonthlyPlaylistCleanup.runClean] (spec "Retrait du
 * mécanisme legacy « monthly playlists »"): the first run deletes the legacy
 * `monthly:YYYYMM` playlists AND their `SongPlaylistMap` mappings while ordinary playlists
 * stay intact (CLEANUP_FIRST_RUN); a second run or a no-match run is a no-op
 * (CLEANUP_IDEMPOTENT).
 *
 * Uses JUnit 4 annotations because Robolectric's @RunWith is a JUnit 4 concept.
 *
 * Note: SongPlaylistMap has FKs to Song and Playlist -> both placeholders must be inserted
 * before any mapping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class MonthlyPlaylistCleanupTest {

    private lateinit var db: DatabaseInitializer
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = context.getSharedPreferences("monthly-playlist-cleanup-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
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
    fun `legacy monthly playlists and their mappings are deleted on the first run`() = runBlocking {
        insertSong("song_a")
        insertSong("song_b")
        insertPlaylist(1, "monthly:202504")
        insertPlaylist(2, "monthly:202505")
        insertPlaylist(3, "my normal playlist")
        insertPlaylist(4, "pinned:monthly:202401")
        db.songPlaylistMapTable.map("song_a", 1)
        db.songPlaylistMapTable.map("song_b", 2)
        db.songPlaylistMapTable.map("song_a", 3)

        assertEquals(2, MonthlyPlaylistCleanup.runClean(prefs, db.playlistTable))

        // The legacy playlists are gone, the others (incl. a pinned one that merely
        // starts with the same letters) are intact
        val remaining = db.playlistTable.allAsPreview().first().map { it.playlist.name }
        assertEquals(listOf("my normal playlist", "pinned:monthly:202401"), remaining)

        // Mappings of the deleted playlists are cascade-deleted, the others survive
        assertEquals(0, db.songPlaylistMapTable.allSongsOf(1).first().size)
        assertEquals(0, db.songPlaylistMapTable.allSongsOf(2).first().size)
        assertEquals(1, db.songPlaylistMapTable.allSongsOf(3).first().size)

        assertTrue(prefs.getBoolean(MonthlyPlaylistCleanup.FLAG_KEY, false))
    }

    @Test
    fun `the second run is a no-op even if new legacy playlists appear`() = runBlocking {
        insertPlaylist(1, "monthly:202504")

        assertEquals(1, MonthlyPlaylistCleanup.runClean(prefs, db.playlistTable))

        // Created after the cleanup: must never be touched again
        insertPlaylist(2, "monthly:202506")
        assertEquals(0, MonthlyPlaylistCleanup.runClean(prefs, db.playlistTable))

        assertEquals(1, db.playlistTable.allAsPreview().first().size)
    }

    @Test
    fun `no legacy playlists sets the flag without any database write`() = runBlocking {
        insertPlaylist(1, "my normal playlist")

        assertEquals(0, MonthlyPlaylistCleanup.runClean(prefs, db.playlistTable))

        assertTrue(prefs.getBoolean(MonthlyPlaylistCleanup.FLAG_KEY, false))
        assertEquals(listOf("my normal playlist"), db.playlistTable.allAsPreview().first().map { it.playlist.name })
    }
}
