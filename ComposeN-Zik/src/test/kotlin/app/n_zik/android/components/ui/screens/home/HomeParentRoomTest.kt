package app.n_zik.android.components.ui.screens.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.models.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeParentRoomTest {
    // runBlocking bridges the suspend Room calls to the JUnit test.
    @Test fun `album artist and playlist batches keep parent and track order`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            database.songTable.upsert(listOf("a", "b", "c").map { Song.makePlaceholder(it) })
            val sql = database.openHelper.writableDatabase
            sql.execSQL("INSERT INTO Album (id) VALUES ('a1'), ('a2')")
            sql.execSQL(
                "INSERT INTO SongAlbumMap (songId, albumId, position) VALUES " +
                    "('a', 'a1', 1), ('b', 'a1', 0), ('b', 'a2', 0), ('c', 'a2', 1)"
            )
            sql.execSQL("INSERT INTO Artist (id) VALUES ('r1'), ('r2')")
            sql.execSQL(
                "INSERT INTO SongArtistMap (songId, artistId) VALUES " +
                    "('b', 'r1'), ('a', 'r1'), ('c', 'r2'), ('b', 'r2')"
            )
            sql.execSQL("INSERT INTO Playlist (id, name, isEditable) VALUES (1, 'First', 1), (2, 'Second', 1)")
            sql.execSQL(
                "INSERT INTO SongPlaylistMap (songId, playlistId, position) VALUES " +
                    "('a', 1, 1), ('b', 1, 0), ('c', 2, 1), ('b', 2, 0)"
            )

            val albums = loadHomeParentSongs(listOf("a2", "a1"), database.songAlbumMapTable::songsForHomeAlbums)
            val artists = loadHomeParentSongs(listOf("r2", "r1"), database.songArtistMapTable::songsForHomeArtists)
            val playlists = loadHomeParentSongs(listOf(2L, 1L), database.songPlaylistMapTable::songsForHomePlaylists)
            assertEquals(listOf("b", "c", "a"), albums.map(Song::id))
            assertEquals(listOf("b", "c", "a"), artists.map(Song::id))
            assertEquals(listOf("c", "b", "a"), playlists.map(Song::id))
        } finally {
            database.close()
        }
    }
}
