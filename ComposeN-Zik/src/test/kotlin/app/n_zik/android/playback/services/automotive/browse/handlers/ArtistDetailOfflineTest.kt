package app.n_zik.android.playback.services.automotive.browse.handlers

import app.n_zik.android.core.network.utils.NetworkQualityHelper
import io.mockk.mockkObject
import io.mockk.unmockkAll
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.requests.ArtistPage
import it.fast4x.innertube.requests.ArtistSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongArtistMapTable
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.browse.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ArtistDetailOfflineTest {
    private lateinit var context: Context
    private val songs = mockk<SongArtistMapTable>()
    private val downloadHelper = mockk<MyDownloadHelper>(relaxed = true)
    private val handler = ArtistDetailHandler()

    @Before
    fun setUp() {
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns true
        val app = ApplicationProvider.getApplicationContext<TestApplication>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
        context = app
        mockkObject(Database, YtMusic)
        every { Database.songArtistMapTable } returns songs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `unavailable UC artist shows only exact mapped songs in database order`() = runTest {
        coEvery { YtMusic.getArtistPage("UC_saved", any()) } returns Result.failure(IllegalStateException("offline"))
        every { songs.allSongsBy("UC_saved") } returns flowOf(
            listOf(Song.makePlaceholder("downloaded-b"), Song.makePlaceholder("downloaded-a"))
        )

        val items = handler.getChildren("artist/UC_saved", context, Database, downloadHelper, null)

        assertEquals(listOf("artist/UC_saved/downloaded-b", "artist/UC_saved/downloaded-a"), items.map { it.mediaId })
        verify(exactly = 1) { songs.allSongsBy("UC_saved") }
        verify(exactly = 1) { songs.allSongsBy(any()) }
    }

    @Test
    fun `failed request without mapped songs returns an empty list`() = runTest {
        coEvery { YtMusic.getArtistPage("UC_empty", any()) } throws IllegalStateException("offline")
        every { songs.allSongsBy("UC_empty") } returns flowOf(emptyList())

        assertTrue(handler.getChildren("artist/UC_empty", context, Database, downloadHelper, null).isEmpty())
        verify(exactly = 1) { songs.allSongsBy("UC_empty") }
    }

    @Test
    fun `modified artist keeps its exact database identity during fallback`() = runTest {
        val artistId = "${MODIFIED_PREFIX}UC_saved"
        coEvery { YtMusic.getArtistPage("UC_saved", any()) } throws IllegalStateException("offline")
        every { songs.allSongsBy(artistId) } returns flowOf(listOf(Song.makePlaceholder("saved")))

        val items = handler.getChildren("artist/$artistId", context, Database, downloadHelper, null)

        assertEquals(listOf("artist/$artistId/saved"), items.map { it.mediaId })
        verify(exactly = 1) { songs.allSongsBy(artistId) }
    }

    @Test
    fun `successful empty online page does not substitute library songs`() = runTest {
        val page = mockk<ArtistPage>()
        every { page.sections } returns emptyList()
        coEvery { YtMusic.getArtistPage("UC_online", any()) } returns Result.success(page)

        assertTrue(handler.getChildren("artist/UC_online", context, Database, downloadHelper, null).isEmpty())
        verify(exactly = 0) { songs.allSongsBy(any()) }
    }

    @Test
    fun `unavailable album and playlist sections do not substitute library songs`() = runTest {
        val page = mockk<ArtistPage>()
        every { page.sections } returns listOf(
            ArtistSection("Albums", listOf(mockk<Innertube.AlbumItem>()), null),
            ArtistSection("Playlists", listOf(mockk<Innertube.PlaylistItem>()), null)
        )
        coEvery { YtMusic.getArtistPage("UC_saved", any()) } returns Result.success(page)
        every { songs.allSongsBy("UC_saved") } returns flowOf(listOf(Song.makePlaceholder("saved")))
        val sections = handler.getChildren("artist/UC_saved", context, Database, downloadHelper, null)
        assertEquals(listOf("artist/UC_saved/Albums", "artist/UC_saved/Playlists"), sections.map { it.mediaId })
        coEvery { YtMusic.getArtistPage("UC_saved", any()) } returns Result.failure(IllegalStateException("offline"))

        sections.forEach { section ->
            assertTrue(handler.getChildren(section.mediaId, context, Database, downloadHelper, null).isEmpty())
        }

        verify(exactly = 0) { songs.allSongsBy(any()) }
    }

    @Test
    fun `effective offline remote sections never substitute artist root songs or fetch`() = runTest {
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns false
        every { songs.allSongsBy("UC_saved") } returns flowOf(listOf(Song.makePlaceholder("saved")))
        for (section in listOf("Songs", "Videos", "Albums", "Singles", "Playlists", "More")) {
            assertTrue(handler.getChildren("artist/UC_saved/$section", context, Database, downloadHelper, null).isEmpty())
        }
        verify(exactly = 0) { songs.allSongsBy(any()) }
        coVerify(exactly = 0) { YtMusic.getArtistPage(any(), any()) }
        assertEquals(listOf("artist/UC_saved/saved"),
            handler.getChildren("artist/UC_saved", context, Database, downloadHelper, null).map { it.mediaId })
    }

    @Test
    fun `successful online section retains its songs without database substitution`() = runTest {
        val page = mockk<ArtistPage>()
        val song = Innertube.SongItem(
            info = Innertube.Info("Online song", NavigationEndpoint.Endpoint.Watch(videoId = "online-song")),
            authors = emptyList(),
            album = null,
            durationText = null,
            thumbnail = null
        )
        every { page.sections } returns listOf(ArtistSection("Songs", listOf(song), null))
        coEvery { YtMusic.getArtistPage("UC_online", any()) } returns Result.success(page)

        val items = handler.getChildren("artist/UC_online/Songs", context, Database, downloadHelper, null)

        assertEquals(listOf("artist/UC_online/Songs/online-song"), items.map { it.mediaId })
        verify(exactly = 0) { songs.allSongsBy(any()) }
    }

    @Test
    fun `thrown cancellation never becomes an offline success`() = runTest {
        val cancellation = CancellationException("cancelled")
        coEvery { YtMusic.getArtistPage("UC_cancelled", any()) } throws cancellation

        try {
            handler.getChildren("artist/UC_cancelled", context, Database, downloadHelper, null)
            fail("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        verify(exactly = 0) { songs.allSongsBy(any()) }
    }

    @Test
    fun `failure-wrapped cancellation never becomes an offline success`() = runTest {
        val cancellation = CancellationException("cancelled")
        coEvery { YtMusic.getArtistPage("UC_cancelled", any()) } returns Result.failure(cancellation)

        try {
            handler.getChildren("artist/UC_cancelled", context, Database, downloadHelper, null)
            fail("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        verify(exactly = 0) { songs.allSongsBy(any()) }
    }

    @Test
    fun `malformed artist parents do not fetch online or offline data`() = runTest {
        listOf("artist", "artist/", "artist//Songs", "album/UC_artist").forEach { parent ->
            assertTrue(handler.getChildren(parent, context, Database, downloadHelper, null).isEmpty())
        }
        coVerify(exactly = 0) { YtMusic.getArtistPage(any(), any()) }
        verify(exactly = 0) { songs.allSongsBy(any()) }
    }
}
