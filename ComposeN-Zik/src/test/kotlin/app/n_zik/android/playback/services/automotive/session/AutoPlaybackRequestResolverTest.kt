package app.n_zik.android.playback.services.automotive.session

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.models.Song
import app.n_zik.android.utils.maxSongsInQueueAndroidAutoKey
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.models.AutoAccessPolicy
import app.n_zik.android.playback.services.automotive.models.CancellableAutoMediaItemMapper
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.requests.searchPage
import app.n_zik.innertube.requests.song
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AutoPlaybackRequestResolverTest {
    private lateinit var context: Context
    private lateinit var resolver: AutoPlaybackRequestResolver
    private val songs = mockk<SongTable>()
    private val originalDownloads = MyDownloadHelper.downloads

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        mockkStatic("app.n_zik.android.GlobalVarsKt", "app.n_zik.innertube.requests.QueueKt", "app.n_zik.innertube.requests.SearchPageKt")
        every { appContext() } returns context
        context.preferences.edit().clear().commit()
        mockkObject(Database, NetworkQualityHelper)
        every { Database.songTable } returns songs
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns true
        every { songs.findAllTitleArtistContains(any()) } returns flowOf(emptyList())
        every { songs.findById(any()) } returns flowOf(null)
        coEvery { songs.getAllDislikedIds() } returns emptyList()
        coEvery { songs.getSongsByDislikedArtists() } returns emptyList()
        coEvery { songs.getSongsByDislikedAlbums() } returns emptyList()
        coEvery { Innertube.song(any()) } returns null
        coEvery { Innertube.searchPage<Innertube.Item>(any(), any(), any()) } returns null
        MyDownloadHelper.downloads = MutableStateFlow(emptyMap())
        resolver = AutoPlaybackRequestResolver(context, Database, AutoAccessPolicy(context, Database, MyDownloadHelper, null))
    }

    @After fun cleanup() {
        MyDownloadHelper.downloads = originalDownloads
        context.preferences.edit().clear().commit()
        unmockkAll()
    }

    // JUnit is synchronous; bridge the actual suspend resolver and eligibility policy.
    @Test fun `offline voice filters unavailable first match before choosing downloaded later match`() = runBlocking {
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns false
        val id = "abcdefghijk"
        val complete = Download(DownloadRequest.Builder(id, Uri.parse("https://example.invalid/$id")).build(),
            Download.STATE_COMPLETED, 0, 0, 0, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE)
        MyDownloadHelper.downloads.value = mapOf(id to complete)
        every { songs.findAllTitleArtistContains("match") } returns flowOf(listOf(song("unavailable"), song(id)))
        assertEquals(listOf(id), resolver.resolve(query("match")).map { it.mediaId })
        every { songs.findById(id) } returns flowOf(song(id))
        assertEquals(id, resolver.resolve(link("https://youtu.be/$id")).single().mediaId)
        assertTrue(resolver.resolve(link("https://untrusted.invalid/$id")).isEmpty())
        coVerify(exactly = 0) { Innertube.searchPage<Innertube.Item>(any(), any(), any()) }
        coVerify(exactly = 0) { Innertube.song(any()) }
    }

    // JUnit is synchronous; bridge the actual suspend resolver and eligibility policy.
    @Test fun `voice excludes disliked and explicit songs and prefers eligible local matches`() = runBlocking {
        context.preferences.edit().putBoolean(parentalControlEnabledKey, true).commit()
        coEvery { songs.getAllDislikedIds() } returns listOf("disliked")
        every { songs.findAllTitleArtistContains("match") } returns flowOf(listOf(
            song("disliked"), song("explicit").copy(title = "$EXPLICIT_PREFIX explicit"), song("clean")))
        assertEquals(listOf("clean"), resolver.resolve(query("match")).map { it.mediaId })
        coVerify(exactly = 0) { Innertube.searchPage<Innertube.Item>(any(), any(), any()) }
    }

    // JUnit is synchronous; exercise the resolved queue order rather than a sorting helper.
    @Test fun `voice starts exact title ahead of earlier partial title and artist matches`() = runBlocking {
        every { songs.findAllTitleArtistContains("Match") } returns flowOf(listOf(
            song("partial").copy(title = "Match live"),
            song("artist").copy(title = "Different song", artistsText = "Match"),
            song("exact").copy(title = "match"),
        ))
        assertEquals(listOf("exact", "partial", "artist"), resolver.resolve(query("Match")).map { it.mediaId })
    }

    // JUnit is synchronous; the DAO's LIKE result includes wildcard-only matches.
    @Test fun `voice treats SQL wildcard characters as literal title or artist text`() = runBlocking {
        every { songs.findAllTitleArtistContains("%") } returns flowOf(listOf(
            song("unrelated"), song("percent").copy(title = "100% music"),
        ))
        assertEquals(listOf("percent"), resolver.resolve(query("%")).map { it.mediaId })
        every { songs.findAllTitleArtistContains("_") } returns flowOf(listOf(
            song("unrelated"), song("underscore").copy(artistsText = "The_Band"),
        ))
        assertEquals(listOf("underscore"), resolver.resolve(query("_")).map { it.mediaId })
    }

    // JUnit is synchronous; verify excluded and truncated tracks never start artwork IO.
    @Test fun `voice applies eligibility and queue limit before mapping local artwork`() = runBlocking {
        context.preferences.edit().putBoolean(parentalControlEnabledKey, true)
            .putString(maxSongsInQueueAndroidAutoKey, "50").commit()
        coEvery { songs.getAllDislikedIds() } returns listOf("local:1")
        val candidates = (1..52).map { song("local:$it") } +
            song("local:53").copy(title = "$EXPLICIT_PREFIX explicit")
        every { songs.findAllTitleArtistContains("match") } returns flowOf(candidates)
        mockkObject(CancellableAutoMediaItemMapper)
        coEvery { CancellableAutoMediaItemMapper.mapSongToMediaItem(any(), any<Boolean>(), any()) } answers {
            MediaItem.Builder().setMediaId(firstArg<Song>().id).build()
        }
        assertEquals((2..51).map { "local:$it" }, resolver.resolve(query("match")).map { it.mediaId })
        coVerify(exactly = 50) { CancellableAutoMediaItemMapper.mapSongToMediaItem(any(), false, true) }
        listOf(1, 52, 53).forEach { id ->
            coVerify(exactly = 0) { CancellableAutoMediaItemMapper.mapSongToMediaItem(match { it.id == "local:$id" }, any<Boolean>(), any()) }
        }
    }

    // JUnit is synchronous; a failed future must not become an empty replacement queue.
    @Test fun `provider failures propagate instead of resolving an empty queue`() {
        coEvery { Innertube.searchPage<Innertube.Item>("failure", any(), any()) } throws IOException("offline provider")
        assertThrows(IOException::class.java) { runBlocking { resolver.resolve(query("failure")) } }
        coEvery { Innertube.song("abcdefghijk") } throws IOException("offline provider")
        assertThrows(IOException::class.java) { runBlocking { resolver.resolve(link("https://youtu.be/abcdefghijk")) } }
    }

    // JUnit is synchronous; bridge the actual suspend resolver and eligibility policy.
    @Test fun `online voice fallback maps provider songs and empty blank requests remain empty`() = runBlocking {
        val result = Innertube.ItemsPage<Innertube.Item>(listOf(remoteSong("abcdefghijk")), null)
        @Suppress("UNCHECKED_CAST")
        val boxed = Result.success(Result.success(result)) as Result<Innertube.ItemsPage<Innertube.Item>?>?
        coEvery { Innertube.searchPage<Innertube.Item>("remote", Innertube.SearchFilter.Song.value, any()) } returns boxed
        assertEquals(listOf("abcdefghijk"), resolver.resolve(query("remote")).map { it.mediaId })
        assertTrue(resolver.resolve(query("   ")).isEmpty())
        assertTrue(resolver.resolve(query("missing")).isEmpty())
        coVerify(exactly = 0) { Innertube.searchPage<Innertube.Item>("", any(), any()) }
    }

    // JUnit is synchronous; bridge the actual suspend resolver and eligibility policy.
    @Test fun `youtube metadata is database first and provider fallback produces canonical URI`() = runBlocking {
        every { songs.findById("abcdefghijk") } returns flowOf(song("abcdefghijk"))
        assertEquals("abcdefghijk", resolver.resolve(link("https://youtube.com/watch?v=abcdefghijk")).single().mediaId)
        coVerify(exactly = 0) { Innertube.song(any()) }
        @Suppress("UNCHECKED_CAST")
        val boxed = Result.success(Result.success(remoteSong("12345678901"))) as Result<Innertube.SongItem?>?
        coEvery { Innertube.song("12345678901") } returns boxed
        val remote = resolver.resolve(link("https://youtu.be/12345678901")).single()
        assertEquals("12345678901", remote.mediaId)
        assertEquals("12345678901", remote.localConfiguration?.uri.toString())
        coVerify(exactly = 1) { Innertube.song("12345678901") }
    }

    @Test fun `supported youtube IDs require exact hosts scheme path and eleven safe characters`() {
        listOf("https://youtu.be/abcdefghijk", "http://www.youtube.com/watch?v=abcdefghijk&t=1",
            "https://music.youtube.com/watch?v=abcdefghijk").forEach {
            assertEquals("abcdefghijk", AutoPlaybackRequestResolver.youtubeId(Uri.parse(it)))
        }
        listOf("file:///abcdefghijk", "content://youtube.com/watch?v=abcdefghijk", "https://youtube.com.evil/watch?v=abcdefghijk",
            "https://evil@youtube.com/watch?v=abcdefghijk", "https://youtu.be/abcdefghijk/extra",
            "https://youtube.com/watch?v=short", "https://youtube.com/watch?v=abcdefghijk&v=12345678901",
            "https://youtube.com/shorts/abcdefghijk", "https://youtu.be/abcdefghij!").forEach {
            assertNull(it, AutoPlaybackRequestResolver.youtubeId(Uri.parse(it)))
        }
    }

    // JUnit is synchronous; assert cancellation crosses the suspend resolver unchanged.
    @Test fun `source cancellation propagates rather than returning empty playback`() {
        every { songs.findAllTitleArtistContains("cancel") } returns flow { throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) { runBlocking { resolver.resolve(query("cancel")) } }
    }

    private fun query(value: String) = MediaItem.RequestMetadata.Builder().setSearchQuery(value).build()
    private fun link(value: String) = MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(value)).build()
    private fun song(id: String) = Song(id, id, durationText = "3:00", thumbnailUrl = null)
    private fun remoteSong(id: String) = Innertube.SongItem(
        info = Innertube.Info(name = id, endpoint = NavigationEndpoint.Endpoint.Watch(videoId = id)),
        authors = emptyList(), album = null, durationText = "3:00", thumbnail = null,
    )
}
