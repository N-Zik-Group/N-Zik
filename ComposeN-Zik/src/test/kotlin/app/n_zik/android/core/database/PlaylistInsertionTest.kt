package app.n_zik.android.core.database

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.PlaylistSongSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.appContext
import app.n_zik.android.components.tab.ImportSongsFromServices
import app.n_zik.android.utils.playlist.PlaylistInsertionPosition
import app.n_zik.android.utils.playlist.PlaylistInsertionPreferences
import app.n_zik.android.utils.playlist.supportsInsertionPosition
import app.n_zik.android.utils.playlist.importSongsToPlaylist
import app.n_zik.android.utils.playlist.importServiceSongsToPlaylist
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaylistInsertionTest {
    private lateinit var db: DatabaseInitializer
    private lateinit var context: Context
    private lateinit var preferences: PlaylistInsertionPreferences
    private val playlist = Playlist(id = 901, name = "Isolated playlist")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        setActiveProfile("insertion-test", context)
        context.preferences.edit().clear().commit()
        preferences = PlaylistInsertionPreferences(context)
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries().build()
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns db
        mockkStatic(::appContext)
        every { appContext() } returns context
    }

    @After
    fun cleanup() {
        unmockkStatic(::appContext)
        unmockkObject(DatabaseInitializer.Companion)
        db.close()
        setActiveProfile("default", context)
    }

    private fun add(vararg ids: String) {
        Database.mapIgnore(playlist, *ids.map { Song.makePlaceholder(it) }.toTypedArray())
    }

    private fun begin() = preferences.set(playlist.id, PlaylistInsertionPosition.Beginning)
    private fun ids() = db.songPlaylistMapTable.songsByPositionDirect(playlist.id).map { it.id }
    private fun positions() = ids().map { db.songPlaylistMapTable.findPositionOf(it, playlist.id) }

    @Test fun `missing preference appends after max including gaps`() {
        add("A", "B")
        db.songPlaylistMapTable.updatePosition(playlist.id, "B", 9)
        add("C", "D")
        assertEquals(listOf("A", "B", "C", "D"), ids())
        assertEquals(listOf(0, 9, 10, 11), positions())
    }

    @Test fun `invalid and wrong typed preference resolve to end`() {
        val key = preferences.key(playlist.id)
        context.preferences.edit().putString(key, "unknown").commit()
        assertEquals(PlaylistInsertionPosition.End, preferences.get(playlist.id))
        context.preferences.edit().putInt(key, 42).commit()
        add("A", "B")
        add("C")
        assertEquals(listOf("A", "B", "C"), ids())
    }

    @Test fun `beginning keeps incoming order and existing gaps`() {
        add("A", "B")
        db.songPlaylistMapTable.updatePosition(playlist.id, "B", 9)
        begin()
        add("C", "D")
        assertEquals(listOf("C", "D", "A", "B"), ids())
        assertEquals(listOf(0, 1, 2, 11), positions())
    }

    @Test fun `duplicates count only new unique songs`() {
        add("A", "B")
        begin()
        add("A", "C", "C", "D")
        assertEquals(listOf("C", "D", "A", "B"), ids())
        assertEquals(listOf(0, 1, 2, 3), positions())
    }

    @Test fun `empty and duplicate only batches do not shift`() {
        add("A", "B")
        begin()
        add()
        add("B", "A", "A")
        assertEquals(listOf("A", "B"), ids())
        assertEquals(listOf(0, 1), positions())
    }

    @Test fun `empty target preserves source order`() {
        begin()
        add("C", "D", "C")
        assertEquals(listOf("C", "D"), ids())
    }

    @Test fun `single addition prepends`() {
        add("A", "B")
        begin()
        add("C")
        assertEquals(listOf("C", "A", "B"), ids())
    }

    @Test fun `preference change leaves contents positions and sort preference untouched`() {
        add("A", "B")
        context.preferences.edit().putString("playlist_sort_order", "Descending").commit()
        begin()
        assertEquals(listOf("A", "B"), ids())
        assertEquals(listOf(0, 1), positions())
        assertEquals("Descending", context.preferences.getString("playlist_sort_order", null))
    }

    @Test fun `preferences are isolated by playlist and active profile`() {
        begin()
        assertEquals(PlaylistInsertionPosition.End, preferences.get(902))
        setActiveProfile("insertion-other", context)
        context.preferences.edit().clear().commit()
        assertEquals(PlaylistInsertionPosition.End, preferences.get(playlist.id))
        preferences.set(902, PlaylistInsertionPosition.Beginning)
        setActiveProfile("insertion-test", context)
        assertEquals(PlaylistInsertionPosition.Beginning, preferences.get(playlist.id))
        assertEquals(PlaylistInsertionPosition.End, preferences.get(902))
    }

    @Test fun `failure after shift rolls back membership songs and positions`() {
        add("A", "B")
        begin()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_new_song BEFORE INSERT ON SongPlaylistMap " +
                "WHEN NEW.songId = 'D' BEGIN SELECT RAISE(ABORT, 'fixture failure'); END"
        )
        assertTrue(runCatching { add("C", "D") }.isFailure)
        assertEquals(listOf("A", "B"), ids())
        assertEquals(listOf(0, 1), positions())
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM Song WHERE id = 'C'").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    @Test fun `custom ascending exposes canonical insertion order`() {
        // JUnit's synchronous test method must await Room's Flow result.
        runBlocking {
            add("A", "B")
            begin()
            add("C", "D")
            val sorted = db.songPlaylistMapTable.sortSongs(
                playlist.id, PlaylistSongSortBy.Custom, SortOrder.Ascending
            ).first()
            assertEquals(listOf("C", "D", "A", "B"), sorted.map { it.id })
        }
    }

    @Test fun `explicit mapping keeps requested position despite preference`() {
        add("A")
        begin()
        db.songTable.insertIgnore(Song.makePlaceholder("C"))
        db.songPlaylistMapTable.mapAtPosition("C", playlist.id, 7)
        assertEquals(listOf("A", "C"), ids())
        assertEquals(listOf(0, 7), positions())
    }

    @Test fun `remote playlists suppress option and always append`() {
        begin()
        val remote = playlist.copy(browseId = "VLremote", isYoutubePlaylist = true)
        assertFalse(remote.supportsInsertionPosition())
        assertFalse(playlist.copy(browseId = "remote").supportsInsertionPosition())
        assertFalse(playlist.copy(isEditable = false).supportsInsertionPosition())
        assertTrue(playlist.supportsInsertionPosition())
        Database.mapIgnore(remote, Song.makePlaceholder("A"))
        Database.mapIgnore(remote, Song.makePlaceholder("B"))
        assertEquals(listOf("A", "B"), ids())
    }

    @Test fun `generated playlist id supports normal default insertion`() {
        Database.mapIgnore(Playlist(name = "New"), Song.makePlaceholder("C"), Song.makePlaceholder("D"))
        db.openHelper.readableDatabase.query("SELECT songId FROM SongPlaylistMap ORDER BY position").use {
            val result = mutableListOf<String>()
            while (it.moveToNext()) result += it.getString(0)
            assertEquals(listOf("C", "D"), result)
        }
    }

    @Test fun `YouTube and CSV import batch prepends in order and still upserts existing metadata`() {
        add("A", "B")
        begin()
        val updated = Song.makePlaceholder("A").copy(title = "Imported metadata", totalPlayTimeMs = 1L)
        importSongsToPlaylist(playlist, listOf(updated, Song.makePlaceholder("C"), Song.makePlaceholder("D"), Song.makePlaceholder("C")))
        assertEquals(listOf("C", "D", "A", "B"), ids())
        assertEquals("Imported metadata", db.songTable.findByIdDirect("A")?.title)
        assertEquals(1L, db.songTable.findByIdDirect("A")?.totalPlayTimeMs)
    }

    @Test fun `service import records actual positions and later prepends move pending matches together`() {
        add("A", "B")
        begin()
        val observedPositions = mutableListOf<Int>()
        importServiceSongsToPlaylist(playlist, listOf(Song.makePlaceholder("C"), Song.makePlaceholder("D"), Song.makePlaceholder("A"))) { _, position ->
            observedPositions += position
        }
        assertEquals(listOf("C", "D", "A", "B"), ids())
        assertEquals(listOf(0, 1, 2), observedPositions)
        assertEquals(0, db.importSongTable.getPosition("C", playlist.id))
        add("E", "E", "A")
        assertEquals(listOf("E", "C", "D", "A", "B"), ids())
        assertEquals(1, db.importSongTable.getPosition("C", playlist.id))
        assertEquals(2, db.importSongTable.getPosition("D", playlist.id))
        assertEquals(3, db.importSongTable.getPosition("A", playlist.id))
        add("C", "A")
        assertEquals(1, db.importSongTable.getPosition("C", playlist.id))
        assertEquals(listOf("E", "C", "D", "A", "B"), ids())
    }

    @Test fun `import failure rolls back metadata and shifted pending matches`() {
        add("A", "B")
        db.importSongTable.insert(ImportSong(originalId = "A", position = 0, playlistId = playlist.id))
        begin()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_import BEFORE INSERT ON SongPlaylistMap " +
                "WHEN NEW.songId = 'D' BEGIN SELECT RAISE(ABORT, 'fixture failure'); END"
        )
        val originalTitle = db.songTable.findByIdDirect("A")?.title
        assertTrue(runCatching {
            importSongsToPlaylist(playlist, listOf(Song.makePlaceholder("A").copy(title = "Changed"), Song.makePlaceholder("C"), Song.makePlaceholder("D")))
        }.isFailure)
        assertEquals(listOf("A", "B"), ids())
        assertEquals(originalTitle, db.songTable.findByIdDirect("A")?.title)
        assertEquals(0, db.importSongTable.getPosition("A", playlist.id))
    }

    @Test fun `service CSV parser completes one ordered batch before reporting success`() {
        add("A", "B")
        begin()
        val csv = File.createTempFile("service-import", ".csv", context.cacheDir)
        csv.writeText("Track URI,Track Name,Artist Name(s),Duration (ms)\nC,First,Artist,1000\nD,Second,Artist,2000\nC,Duplicate,Artist,1000\n")
        val finished = CountDownLatch(1)
        val callbacks = mutableListOf<Int>()
        var completedOrder = emptyList<String>()
        try {
            // The synchronous JUnit method waits for the suspend file reader and queued Room transaction.
            runBlocking {
                ImportSongsFromServices.openFile(
                    Uri.fromFile(csv), targetPlaylistId = playlist.id,
                    afterTransaction = { position, _, _, _ -> callbacks += position },
                    onImportComplete = { completedOrder = ids(); finished.countDown() },
                )
            }
            assertTrue("import completion callback", finished.await(5, TimeUnit.SECONDS))
            assertEquals(listOf("C", "D", "A", "B"), completedOrder)
            assertEquals(listOf(0, 1, 0), callbacks)
            assertEquals(0, db.importSongTable.getPosition("C", playlist.id))
            assertEquals(1, db.importSongTable.getPosition("D", playlist.id))
            assertEquals("First", db.songTable.findByIdDirect("C")?.title)
        } finally {
            csv.delete()
        }
    }
}
