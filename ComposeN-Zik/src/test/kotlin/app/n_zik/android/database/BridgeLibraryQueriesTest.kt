package app.n_zik.android.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.database.PlaylistSongRef
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Read-only queries added for the PC bridge library routes (story 6b), run on an in-memory
 * Room database. Orphan map rows (song missing) are inserted with foreign keys off, as an
 * old or damaged database may hold them: none of these queries may count or return them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BridgeLibraryQueriesTest {

    private lateinit var db: DatabaseInitializer
    private lateinit var sql: SupportSQLiteDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
        sql = db.openHelper.writableDatabase
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun insertSong(id: String, thumbnailUrl: String? = null) {
        db.songTable.upsert(Song(id = id, title = "T $id", durationText = null, thumbnailUrl = thumbnailUrl))
    }

    private fun withoutForeignKeys(block: () -> Unit) {
        sql.execSQL("PRAGMA foreign_keys = OFF")
        try {
            block()
        } finally {
            sql.execSQL("PRAGMA foreign_keys = ON")
        }
    }

    @Test
    fun `album song counts ignore orphan map rows`() {
        insertSong("s1")
        insertSong("s2")
        sql.execSQL("INSERT INTO Album (id) VALUES ('al1'), ('al2'), ('al3')")
        sql.execSQL("INSERT INTO SongAlbumMap (songId, albumId, position) VALUES ('s1', 'al1', 0), ('s2', 'al1', 1), ('s2', 'al2', 0)")
        withoutForeignKeys {
            sql.execSQL("INSERT INTO SongAlbumMap (songId, albumId, position) VALUES ('ghost', 'al1', 2), ('ghost', 'al3', 0)")
        }

        val counts = db.songAlbumMapTable.songCountsDirect().associate { it.id to it.count }

        assertEquals(mapOf("al1" to 2, "al2" to 1), counts)
    }

    @Test
    fun `artist song counts ignore orphan map rows`() {
        insertSong("s1")
        insertSong("s2")
        insertSong("s3")
        sql.execSQL("INSERT INTO Artist (id) VALUES ('ar1'), ('ar2')")
        sql.execSQL("INSERT INTO SongArtistMap (songId, artistId) VALUES ('s1', 'ar1'), ('s2', 'ar1'), ('s3', 'ar1'), ('s3', 'ar2')")
        withoutForeignKeys {
            sql.execSQL("INSERT INTO SongArtistMap (songId, artistId) VALUES ('ghost', 'ar2')")
        }

        val counts = db.songArtistMapTable.songCountsDirect().associate { it.id to it.count }

        assertEquals(mapOf("ar1" to 3, "ar2" to 1), counts)
    }

    @Test
    fun `playlist songs come in playlist order without orphans`() {
        insertSong("a")
        insertSong("b")
        insertSong("c")
        sql.execSQL("INSERT INTO Playlist (id, name, isEditable) VALUES (1, 'P1', 1), (2, 'P2', 1)")
        sql.execSQL("INSERT INTO SongPlaylistMap (songId, playlistId, position) VALUES ('c', 1, 0), ('a', 1, 2), ('b', 2, 0)")
        withoutForeignKeys {
            sql.execSQL("INSERT INTO SongPlaylistMap (songId, playlistId, position) VALUES ('ghost', 1, 1)")
        }

        assertEquals(listOf("c", "a"), db.songPlaylistMapTable.songsByPositionDirect(1L).map { it.id })
        assertEquals(emptyList<String>(), db.songPlaylistMapTable.songsByPositionDirect(99L).map { it.id })
    }

    @Test
    fun `songs with a thumbnail come per playlist in position order`() {
        insertSong("noThumb")
        insertSong("t1", thumbnailUrl = "https://x/1")
        insertSong("t2", thumbnailUrl = "https://x/2")
        sql.execSQL("INSERT INTO Playlist (id, name, isEditable) VALUES (1, 'P1', 1), (2, 'P2', 1), (3, 'P3', 1)")
        sql.execSQL(
            "INSERT INTO SongPlaylistMap (songId, playlistId, position) VALUES " +
                "('noThumb', 1, 0), ('t2', 1, 1), ('t1', 1, 2), ('t1', 2, 5), ('t2', 2, 3), ('noThumb', 3, 0)"
        )

        val rows = db.songPlaylistMapTable.songsWithThumbnailDirect()

        assertEquals(
            listOf(PlaylistSongRef(1L, "t2"), PlaylistSongRef(1L, "t1"), PlaylistSongRef(2L, "t2"), PlaylistSongRef(2L, "t1")),
            rows,
        )
    }
}
