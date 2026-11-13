package app.n_zik.android.playback.services.automotive.browse

import java.io.File

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.models.Song
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.activeProfileKey
import app.n_zik.android.utils.profilePreferences
import app.n_zik.android.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.core.database.AlbumTable
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.PlaylistTable
import app.n_zik.android.core.database.SongAlbumMapTable
import app.n_zik.android.core.database.SongArtistMapTable
import app.n_zik.android.core.database.SongPlaylistMapTable
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.ArtworkContentProvider
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.widget.PlaylistQuickPickSource
import app.n_zik.android.widget.PlaylistWidgetManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AutoSuggestedQueueLoaderTest {
    private lateinit var context: Context
    private lateinit var source: PlaylistQuickPickSource
    private val songs = mockk<SongTable>()
    private val playlists = mockk<PlaylistTable>()
    private val artists = mockk<ArtistTable>()
    private val albums = mockk<AlbumTable>()
    private val playlistSongs = mockk<SongPlaylistMapTable>()
    private val artistSongs = mockk<SongArtistMapTable>()
    private val albumSongs = mockk<SongAlbumMapTable>()
    private val originalDownloads = MyDownloadHelper.downloads

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        context.preferences.edit().clear().commit()
        context.profilePreferences.edit().remove(activeProfileKey).commit()
        source = PlaylistQuickPickSource()
        mockkObject(Database, NetworkQualityHelper, PlaylistQuickPickSource.Companion)
        every { PlaylistQuickPickSource.shared } answers { source }
        every { Database.songTable } returns songs
        every { Database.playlistTable } returns playlists
        every { Database.artistTable } returns artists
        every { Database.albumTable } returns albums
        every { Database.songPlaylistMapTable } returns playlistSongs
        every { Database.songArtistMapTable } returns artistSongs
        every { Database.songAlbumMapTable } returns albumSongs
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns true
        every { songs.allFavorites(any()) } returns flowOf(emptyList())
        every { songs.all(any(), any()) } returns flowOf(emptyList())
        coEvery { songs.getAllDislikedIds() } returns emptyList()
        coEvery { songs.getSongsByDislikedArtists() } returns emptyList()
        coEvery { songs.getSongsByDislikedAlbums() } returns emptyList()
        every { playlists.sortPreviewsByPlayCount(8) } returns flowOf(emptyList())
        every { artists.sortFollowingByPlayCount(8) } returns flowOf(emptyList())
        every { artists.sortInLibraryByPlayCount(8) } returns flowOf(emptyList())
        every { albums.sortBookmarkedByPlayCount(8) } returns flowOf(emptyList())
        every { albums.sortInLibraryByPlayCount(8) } returns flowOf(emptyList())
        MyDownloadHelper.downloads = MutableStateFlow(emptyMap())
    }

    @After fun cleanup() {
        MyDownloadHelper.downloads = originalDownloads
        context.preferences.edit().clear().commit()
        context.profilePreferences.edit().remove(activeProfileKey).commit()
        unmockkAll()
    }

    // JUnit is synchronous; exercise the suspend database-to-dashboard loader.
    @Test fun `suggestions use widget sources with favorites first and named separate queues`() = runBlocking {
        every { songs.allFavorites(any()) } returns flowOf(listOf(song("favorite")))
        every { playlists.sortPreviewsByPlayCount(8) } returns flowOf(listOf(PlaylistPreview(Playlist(7, "Road trip"), 2)))
        every { playlistSongs.sortSongsByPosition(7, any()) } returns flowOf(listOf(song("playlist-first"), song("playlist-second")))
        val artist = Artist("artist", name = "Favorite artist", thumbnailUrl = "https://example.invalid/artist.jpg")
        every { artists.sortFollowingByPlayCount(8) } returns flowOf(listOf(artist))
        every { artists.sortInLibraryByPlayCount(8) } returns flowOf(listOf(artist))
        every { artistSongs.allSongsBy("artist") } returns flowOf(listOf(song("artist-song")))
        val album = Album("album", title = "Saved album", thumbnailUrl = "https://example.invalid/album.jpg")
        every { albums.sortBookmarkedByPlayCount(8) } returns flowOf(listOf(album))
        every { albums.sortInLibraryByPlayCount(8) } returns flowOf(listOf(album))
        every { albumSongs.allSongsOf("album") } returns flowOf(listOf(song("album-song")))
        every { songs.all(any(), any()) } returns flowOf(listOf(song("random-song")))

        val result = AutoSuggestedQueueLoader.load(context, Database, MyDownloadHelper, null)
        assertEquals(5, result.size)
        assertEquals(context.getString(R.string.library_favorites_title), result.first().title)
        assertEquals(listOf("favorite"), result.first().items.map { it.mediaId })
        assertEquals(listOf("playlist-first", "playlist-second"), result.single { it.title == "Road trip" }.items.map { it.mediaId })
        assertEquals("https://example.invalid/artist.jpg", result.single { it.title == "Favorite artist" }.artworkUri?.getQueryParameter("u"))
        assertEquals(setOf("Road trip", "Favorite artist", "Saved album", "random-song"), result.drop(1).map { it.title }.toSet())
    }

    // JUnit is synchronous; run the actual offline and dislike policy before queue publication.
    @Test fun `unplayable and explicit recommendations are omitted before choosing their first song`() = runBlocking {
        context.preferences.edit().putBoolean(parentalControlEnabledKey, true).commit()
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns false
        coEvery { songs.getAllDislikedIds() } returns listOf("disliked")
        val complete = Download(
            DownloadRequest.Builder("downloaded", Uri.parse("https://example.invalid/downloaded")).build(),
            Download.STATE_COMPLETED, 0, 0, 0, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE,
        )
        MyDownloadHelper.downloads.value = mapOf("downloaded" to complete)
        every { songs.allFavorites(any()) } returns flowOf(listOf(
            song("unavailable"), song("disliked"), song("explicit").copy(title = "$EXPLICIT_PREFIX explicit"), song("downloaded"),
        ))
        every { playlists.sortPreviewsByPlayCount(8) } returns flowOf(listOf(PlaylistPreview(Playlist(7, "Unavailable playlist"), 1)))
        every { playlistSongs.sortSongsByPosition(7, any()) } returns flowOf(listOf(song("unavailable")))
        val result = AutoSuggestedQueueLoader.load(context, Database, MyDownloadHelper, null)
        assertEquals(AutoSessionConstants.ID_SONGS_FAVORITES, result.first().parentId)
        assertEquals(listOf("downloaded"), result.first().items.map { it.mediaId })
        assertTrue(result.last().items.isEmpty())
        assertEquals("playlist/7", result.last().browseId)
    }

    // JUnit is synchronous; the Android Auto dashboard accepts a small recommendation set.
    @Test fun `dashboard shares the widget limit of eight without displacing favorites`() = runBlocking {
        every { songs.allFavorites(any()) } returns flowOf(listOf(song("favorite")))
        every { playlists.sortPreviewsByPlayCount(8) } returns flowOf((1L..8L).map {
            PlaylistPreview(Playlist(it, "Playlist $it"), 1)
        })
        every { playlistSongs.sortSongsByPosition(any(), any()) } answers { flowOf(listOf(song("playlist-${firstArg<Long>()}"))) }
        every { songs.all(any(), any()) } returns flowOf((1..8).map { song("song-$it") })
        val result = AutoSuggestedQueueLoader.load(context, Database, MyDownloadHelper, null)
        assertEquals(8, result.size)
        assertEquals(AutoSessionConstants.ID_SONGS_FAVORITES, result.first().parentId)
        assertTrue(result.all { it.items.isNotEmpty() && it.title.isNotBlank() })
    }

    private fun song(id: String) = Song(id, id, durationText = "3:00", thumbnailUrl = "https://example.invalid/$id.jpg")

    // JUnit is synchronous; the actual mapper registers private files for external car renderers.
    @Test fun `dashboard artwork uses accessible provider identities for private covers and local audio`() = runBlocking {
        val playlistCover = File.createTempFile("playlist-cover", ".jpg", context.cacheDir)
        val artistCover = File.createTempFile("artist-cover", ".jpg", context.cacheDir)
        try {
            every { songs.all(any(), any()) } returns flowOf(listOf(song("local:42").copy(thumbnailUrl = null)))
            every { playlists.sortPreviewsByPlayCount(8) } returns flowOf(listOf(PlaylistPreview(Playlist(7, "Private cover"), 1)))
            every { playlistSongs.sortSongsByPosition(7, any()) } returns flowOf(listOf(
                song("playlist-song").copy(thumbnailUrl = playlistCover.toURI().toString()),
            ))
            every { artists.sortFollowingByPlayCount(8) } returns flowOf(listOf(
                Artist("artist", name = "Custom artist", thumbnailUrl = artistCover.toURI().toString()),
            ))
            every { artistSongs.allSongsBy("artist") } returns flowOf(listOf(song("artist-song")))

            val result = AutoSuggestedQueueLoader.load(context, Database, MyDownloadHelper, null)
            val localTrack = result.single { entry -> entry.items.any { it.mediaId == "local:42" } }
            assertEquals("local:42", localTrack.title)
            assertEquals(ArtworkContentProvider.uriFor("local:42"), localTrack.artworkUri)
            assertEquals(ArtworkContentProvider.uriForSource(context, "playlist-song", playlistCover),
                result.single { it.title == "Private cover" }.artworkUri)
            assertEquals(ArtworkContentProvider.uriForSource(context, "artist-song", artistCover),
                result.single { it.title == "Custom artist" }.artworkUri)
            assertTrue(result.drop(1).all { it.artworkUri?.scheme == "content" && it.artworkUri?.authority == ArtworkContentProvider.AUTHORITY })
        } finally {
            playlistCover.delete()
            artistCover.delete()
        }
    }

    // JUnit is synchronous; compare both consumers with a genuinely empty library.
    @Test fun `empty database keeps the same Favorites fallback in widget and Android Auto`() = runBlocking {
        val widget = PlaylistWidgetManager.getQuickPicks(context, 0xff112233.toInt())
        val automotive = AutoSuggestedQueueLoader.load(context, Database, MyDownloadHelper, null)
        assertEquals(widget.map { it.title }, automotive.map { it.title })
        assertEquals("nzik://app/playFavorites", shadowOf(widget.single().targetIntent).savedIntent.data.toString())
        assertEquals(AutoSessionConstants.ID_SONGS_FAVORITES, automotive.single().browseId)
        assertTrue(automotive.single().items.isEmpty())
        val tree = AutoBrowseTree(context, Database, MyDownloadHelper)
        val row = tree.getChildren(AutoSuggestedQueueLoader.ROOT_ID, -1, null).single()
        assertEquals(AutoSessionConstants.ID_SONGS_FAVORITES, row.mediaId)
        assertEquals(true, row.mediaMetadata.isBrowsable)
        assertEquals(false, row.mediaMetadata.isPlayable)
    }

    // JUnit is synchronous; both surfaces must consume the same cached selection and targets.
    @Test fun `widget and Android Auto retain identical order and empty container destinations`() = runBlocking {
        every { playlists.sortPreviewsByPlayCount(8) } returns flowOf(listOf(PlaylistPreview(Playlist(7, "Empty playlist"), 0)))
        every { playlistSongs.sortSongsByPosition(7, any()) } returns flowOf(emptyList())
        every { artists.sortFollowingByPlayCount(8) } returns flowOf(listOf(Artist("artist", name = "Saved artist")))
        every { artistSongs.allSongsBy("artist") } returns flowOf(emptyList())
        every { albums.sortBookmarkedByPlayCount(8) } returns flowOf(listOf(Album("album", title = "Saved album")))
        every { albumSongs.allSongsOf("album") } returns flowOf(emptyList())
        val widget = PlaylistWidgetManager.getQuickPicks(context, 0xff112233.toInt())
        val automotive = AutoSuggestedQueueLoader.load(context, Database, MyDownloadHelper, null)
        assertEquals(widget.map { it.title }, automotive.map { it.title })
        assertEquals(setOf("nzik://app/playFavorites", "nzik://app/localPlaylist/7", "nzik://app/channel/artist", "nzik://app/album/album"),
            widget.map { shadowOf(it.targetIntent).savedIntent.data.toString() }.toSet())
        assertEquals(setOf(AutoSessionConstants.ID_SONGS_FAVORITES, "playlist/7", "artist/artist", "album/album"), automotive.map { it.browseId }.toSet())
    }

    // JUnit is synchronous; a deterministic clock verifies the original sixty-second cache policy.
    @Test fun `shared selection expires after sixty seconds and never crosses profiles`() = runBlocking {
        var time = 0L
        source = PlaylistQuickPickSource { time }
        val initial = source.load(context)
        time = 59_999L
        assertSame(initial, source.load(context))
        time = 60_000L
        val refreshed = source.load(context)
        assertNotSame(initial, refreshed)
        context.profilePreferences.edit().putString(activeProfileKey, "other-profile").commit()
        assertNotSame(refreshed, source.load(context))
    }
}
