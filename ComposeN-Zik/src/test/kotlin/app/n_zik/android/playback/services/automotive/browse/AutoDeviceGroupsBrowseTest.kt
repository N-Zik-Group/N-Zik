package app.n_zik.android.playback.services.automotive.browse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.core.database.AlbumTable
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongAlbumMapTable
import app.n_zik.android.core.database.SongArtistMapTable
import app.n_zik.android.core.ondevice.DeviceLibrarySnapshot
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.enums.AlbumSortBy
import app.n_zik.android.enums.ArtistSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.browse.handlers.AlbumsBrowseHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.ArtistsBrowseHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.AutoDeviceGroupsBrowseHandler
import app.n_zik.android.playback.services.automotive.session.AutoMediaIdContract
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_ALBUMS_ONDEVICE
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_ARTISTS_ONDEVICE
import app.n_zik.android.utils.homeAlbumsOrderKey
import app.n_zik.android.utils.homeArtistsOrderKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.showOnDeviceAlbumsKey
import app.n_zik.android.utils.showOnDeviceArtistsKey
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import app.n_zik.android.core.ondevice.DEVICE_LIBRARY_ENABLED_KEY
import app.n_zik.android.core.ondevice.isDeviceLibraryEnabled
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.utils.getActiveProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AutoDeviceGroupsBrowseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val album = Album(id = "LOCAL_ALBUM_DEVICE_test", title = "Album")
    private val artist = Artist(id = "LOCAL_ARTIST_DEVICE_test", name = "Artist")
    private val eligibleSong = Song.makePlaceholder("local:1")
    private val albumDao = mockk<AlbumTable>()
    private val artistDao = mockk<ArtistTable>()
    private val songAlbumMap = mockk<SongAlbumMapTable>()
    private val songArtistMap = mockk<SongArtistMapTable>()
    private val database = mockk<Database> {
        every { albumTable } returns albumDao
        every { artistTable } returns artistDao
        every { songAlbumMapTable } returns songAlbumMap
        every { songArtistMapTable } returns songArtistMap
    }
    private val handler = AutoDeviceGroupsBrowseHandler {
        DeviceLibrarySnapshot(
            songIds = setOf(eligibleSong.id, "local:2"),
            albumSongIds = mapOf(album.id to setOf(eligibleSong.id)),
            artistSongIds = mapOf(artist.id to setOf(eligibleSong.id)),
        )
    }

    private fun enableOnDevice() {
        profileDataPrefs(context, getActiveProfile(context)).edit()
            .putBoolean(DEVICE_LIBRARY_ENABLED_KEY, true).apply()
    }

    @Test
    fun `on device is disabled without a saved choice`() = runTest {
        assertFalse(context.isDeviceLibraryEnabled())
        assertEquals(emptyList<Album>(), handler.albums(context, database))
        assertEquals(emptyList<Artist>(), handler.artists(context, database))
    }

    @Test
    fun `categories respect the same visibility settings and scan eligibility`() = runTest {
        enableOnDevice()
        every { albumDao.sortInLibrary(AlbumSortBy.Title, SortOrder.Ascending) } returns
            flowOf(listOf(Album(id = "remote", title = "Remote", isYoutubeAlbum = false), album))
        every { artistDao.sortInLibrary(ArtistSortBy.Name, SortOrder.Ascending) } returns
            flowOf(listOf(Artist(id = "remote", name = "Remote", isYoutubeArtist = false), artist))

        assertEquals(listOf(album), handler.albums(context, database))
        assertEquals(listOf(artist), handler.artists(context, database))

        context.preferences.edit().putBoolean(showOnDeviceAlbumsKey, false).apply()
        assertEquals(emptyList<Album>(), handler.albums(context, database))
        assertEquals(listOf(artist), handler.artists(context, database))

        context.preferences.edit().putBoolean(showOnDeviceArtistsKey, false).apply()
        assertEquals(emptyList<Artist>(), handler.artists(context, database))
    }

    @Test
    fun `category roots follow phone chip order and expose the eligible count`() = runTest {
        enableOnDevice()
        val downloads = mockk<MyDownloadHelper>()
        every { albumDao.allInLibrary() } returns flowOf(listOf(album))
        every { albumDao.allBookmarked() } returns flowOf(emptyList())
        every { albumDao.allDisliked() } returns flowOf(emptyList())
        every { albumDao.sortInLibrary(AlbumSortBy.Title, SortOrder.Ascending) } returns flowOf(listOf(album))
        every { artistDao.allInLibrary() } returns flowOf(listOf(artist))
        every { artistDao.allFollowing() } returns flowOf(emptyList())
        every { artistDao.allDisliked() } returns flowOf(emptyList())
        every { artistDao.sortInLibrary(ArtistSortBy.Name, SortOrder.Ascending) } returns flowOf(listOf(artist))
        context.preferences.edit()
            .putString(homeAlbumsOrderKey, """["on_device", "all"]""")
            .putString(homeArtistsOrderKey, """["on_device", "all"]""")
            .apply()

        val albums = AlbumsBrowseHandler(handler).getChildren(PlayerServiceModern.ALBUM, context, database, downloads, null)
        val artists = ArtistsBrowseHandler(handler).getChildren(PlayerServiceModern.ARTIST, context, database, downloads, null)
        assertEquals(ID_ALBUMS_ONDEVICE, albums.first().mediaId)
        assertEquals(ID_ARTISTS_ONDEVICE, artists.first().mediaId)
        assertEquals("1", albums.first().mediaMetadata.subtitle)
        assertEquals("1", artists.first().mediaMetadata.subtitle)
    }

    @Test
    fun `details and fallback playback use the same eligible songs`() = runTest {
        enableOnDevice()
        val mapped = listOf(eligibleSong, Song.makePlaceholder("local:2"), Song.makePlaceholder("remote"))
        every { songAlbumMap.allSongsOf(album.id) } returns flowOf(mapped)
        every { songArtistMap.allSongsBy(artist.id) } returns flowOf(mapped)
        val albumParent = "$ID_ALBUMS_ONDEVICE/${album.id}"
        val artistParent = "$ID_ARTISTS_ONDEVICE/${artist.id}"

        assertEquals(ID_ALBUMS_ONDEVICE, handler.detailParent(albumParent))
        assertEquals(ID_ARTISTS_ONDEVICE, handler.detailParent(artistParent))
        assertNull(handler.detailParent("album/${album.id}"))
        assertEquals(listOf(eligibleSong), handler.songs(albumParent, context, database))
        assertEquals(listOf(eligibleSong), handler.songs(artistParent, context, database))
        assertEquals(emptyList<Song>(), handler.songs("$ID_ALBUMS_ONDEVICE/stale", context, database))

        val fallback = AutoMediaIdContract.parseSongSelection("$albumParent/${eligibleSong.id}", ID_ALBUMS_ONDEVICE)
        assertEquals(album.id, fallback?.containerId)
        assertEquals(eligibleSong.id, fallback?.songId)
    }
}
