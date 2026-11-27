package app.n_zik.android.components.ui.screens.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.ondevice.isDeviceAlbumId
import app.n_zik.android.core.ondevice.isDeviceArtistId
import app.n_zik.android.enums.AlbumSortBy
import app.n_zik.android.enums.ArtistSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.Song
import kotlinx.coroutines.flow.first
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
    @Test fun `all mixes sorted remote and verified device groups without requiring local playback history`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val localAlbum = "LOCAL_ALBUM_DEVICE_1"
            val localArtist = "LOCAL_ARTIST_DEVICE_1"
            val manuallyLinkedAlbum = "LOCAL_ALBUM_MANUAL"
            val manuallyLinkedArtist = "LOCAL_ARTIST_MANUAL"
            database.songTable.upsert(listOf(
                Song.makePlaceholder("remote").copy(totalPlayTimeMs = 1L),
                Song.makePlaceholder("local:1"),
                Song.makePlaceholder("local:2"),
            ))
            val sql = database.openHelper.writableDatabase
            sql.execSQL("INSERT INTO Album (id) VALUES ('remote-album'), ('$localAlbum')")
            sql.execSQL(
                "INSERT INTO SongAlbumMap (songId, albumId, position) VALUES " +
                    "('remote', 'remote-album', 0), ('local:1', '$localAlbum', 0), ('local:2', '$localAlbum', 1)",
            )
            sql.execSQL("INSERT INTO Artist (id) VALUES ('remote-artist'), ('$localArtist')")
            sql.execSQL(
                "INSERT INTO SongArtistMap (songId, artistId) VALUES " +
                    "('remote', 'remote-artist'), ('local:1', '$localArtist'), ('local:2', '$localArtist')",
            )
            sql.execSQL("INSERT INTO Event (songId, timestamp, playTime) VALUES ('remote', 1000, 1)")

            val albumTable = database.albumTable
            assertEquals(listOf("remote-album"), albumTable.sortInLibrary(AlbumSortBy.PlayCount, SortOrder.Ascending)
                .first().map { it.id })
            assertEquals(listOf(localAlbum, "remote-album"), albumTable.sortInLibrary(
                AlbumSortBy.PlayCount, SortOrder.Ascending, includeDeviceGroups = true,
            ).first().map { it.id })
            val artistTable = database.artistTable
            assertEquals(listOf("remote-artist"), artistTable.sortInLibrary(ArtistSortBy.ListeningTime, SortOrder.Ascending)
                .first().map { it.id })
            assertEquals(listOf(localArtist, "remote-artist"), artistTable.sortInLibrary(
                ArtistSortBy.ListeningTime, SortOrder.Ascending, includeDeviceGroups = true,
            ).first().map { it.id })

            sql.execSQL("UPDATE Album SET bookmarkedAt = 1")
            sql.execSQL("UPDATE Artist SET bookmarkedAt = 1")
            assertEquals(listOf("remote-album"), albumTable.sortBookmarked(
                AlbumSortBy.PlayCount, SortOrder.Ascending,
            ).first().map { it.id })
            assertEquals(listOf(localAlbum, "remote-album"), albumTable.sortBookmarked(
                AlbumSortBy.PlayCount, SortOrder.Ascending, includeDeviceGroups = true,
            ).first().map { it.id })
            assertEquals(listOf("remote-artist"), artistTable.sortFollowing(
                ArtistSortBy.ListeningTime, SortOrder.Ascending,
            ).first().map { it.id })
            assertEquals(listOf(localArtist, "remote-artist"), artistTable.sortFollowing(
                ArtistSortBy.ListeningTime, SortOrder.Ascending, includeDeviceGroups = true,
            ).first().map { it.id })

            val albumSongs = loadHomeLibraryGroupSongs(
                listOf(localAlbum, "remote-album"), database.songAlbumMapTable::songsForHomeAlbums, ::isDeviceAlbumId,
            ) { mapOf(localAlbum to setOf("local:1")) }
            val artistSongs = loadHomeLibraryGroupSongs(
                listOf(localArtist, "remote-artist"), database.songArtistMapTable::songsForHomeArtists, ::isDeviceArtistId,
            ) { mapOf(localArtist to setOf("local:1")) }
            assertEquals(listOf("local:1", "remote"), albumSongs.map(Song::id))
            assertEquals(listOf("local:1", "remote"), artistSongs.map(Song::id))

            sql.execSQL("INSERT INTO Album (id, bookmarkedAt) VALUES ('$manuallyLinkedAlbum', 1)")
            sql.execSQL("INSERT INTO Artist (id, bookmarkedAt) VALUES ('$manuallyLinkedArtist', 1)")
            sql.execSQL("INSERT INTO SongAlbumMap (songId, albumId, position) VALUES ('local:1', '$manuallyLinkedAlbum', 0)")
            sql.execSQL("INSERT INTO SongArtistMap (songId, artistId) VALUES ('local:1', '$manuallyLinkedArtist')")
            assertEquals(listOf("remote-album"), database.songAlbumMapTable.observeNonDeviceSongCounts().first().map { it.id })
            assertEquals(listOf("remote-artist"), database.songArtistMapTable.observeNonDeviceSongCounts().first().map { it.id })
        } finally {
            database.close()
        }
    }

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

    // runBlocking bridges the suspend Room calls to the JUnit test.
    @Test fun `a stored device playlist enables actions only while a mapped track is available`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            database.songTable.upsert(listOf("local:1", "local:2", "online").map { Song.makePlaceholder(it) })
            val sql = database.openHelper.writableDatabase
            sql.execSQL("INSERT INTO Playlist (id, name, isEditable) VALUES (1, 'Saved', 1), (2, 'Available', 1)")
            sql.execSQL(
                "INSERT INTO SongPlaylistMap (songId, playlistId, position) VALUES " +
                    "('local:1', 1, 0), ('local:2', 2, 0), ('online', 2, 1)",
            )
            val memberships = database.songPlaylistMapTable
            assertEquals(listOf(1L), memberships.onDeviceOnlyPlaylistIds().first())
            val deviceMappings = memberships.observeDeviceTrackMappings().first()
            assertEquals(setOf(1L), availableDevicePlaylistIds(deviceMappings, setOf("local:1", "online")))
            assertEquals(setOf(2L), availableDevicePlaylistIds(deviceMappings, setOf("local:2")))
            assertEquals(emptySet<Long>(), availableDevicePlaylistIds(deviceMappings, emptySet()))
            assertEquals(listOf(1L), memberships.onDeviceOnlyPlaylistIds().first())
        } finally {
            database.close()
        }
    }
}
