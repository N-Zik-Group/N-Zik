package app.n_zik.android.playback.services.automotive.session

import android.app.Application
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.offline.Download
import androidx.media3.session.MediaSession
import app.it.fast4x.rimusic.models.Format
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.appContext
import io.mockk.mockkStatic
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.FormatTable
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.core.database.ext.FormatWithSong
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.browse.AutoBrowseTree
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AutoCallbackAppendBatchTest {
    private lateinit var callback: AutoSessionCallback
    private lateinit var tree: AutoBrowseTree
    private lateinit var songs: SongTable
    private lateinit var formats: FormatTable
    private var excluded = listOf("blocked")
    private var originalDownloads: MutableStateFlow<Map<String, Download>>? = null

    @Before fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        mockkObject(NetworkQualityHelper, Database)
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns false
        songs = mockk()
        every { Database.songTable } returns songs
        coEvery { songs.getAllDislikedIds() } answers { excluded }
        coEvery { songs.getSongsByDislikedArtists() } returns emptyList()
        coEvery { songs.getSongsByDislikedAlbums() } returns emptyList()
        formats = mockk()
        every { Database.formatTable } returns formats
        every { formats.allWithSongs(any(), any()) } returns flowOf(listOf("kept", "blocked").map {
            FormatWithSong(Format(songId = it, contentLength = 100L), Song.makePlaceholder(it))
        })
        // Use the mutable state seam rather than mocking this singleton getter: JVM
        // instrumentation of the trivial accessor is unreliable in the combined suite.
        originalDownloads = MyDownloadHelper.downloads
        MyDownloadHelper.downloads = MutableStateFlow(emptyMap())
        callback = AutoSessionCallback(context, Database, MyDownloadHelper)
        callback.binder = mockk<PlayerServiceModern.Binder>(relaxed = true).also {
            every { it.cache.isCached(any(), 0L, 100L) } returns true
        }
        tree = AutoSessionCallback::class.java.getDeclaredField("autoBrowseTree").apply {
            isAccessible = true
        }.get(callback) as AutoBrowseTree
    }

    @After fun tearDown() {
        if (::callback.isInitialized) callback.release()
        originalDownloads?.let { MyDownloadHelper.downloads = it }
        unmockkAll()
    }

    @Test fun `bulk mixed append bounds policy queries and preserves canonical occurrences and fresh request policy`() {
        val parents = listOf(AutoSessionConstants.ID_SONGS_ALL, "${AutoSessionConstants.ID_SEARCH_SONGS}/AC/DC",
            "album/A", "playlist/42", "artist/A/videos", AutoSessionConstants.ID_SONGS_DISLIKED,
            AutoSessionConstants.ID_QUICK_PICKS, AutoSessionConstants.ID_SEARCH_VIDEOS)
        val request = mutableListOf<MediaItem>()
        val expected = mutableListOf<Int>()
        val captures = parents.mapIndexed { parentIndex, parent ->
            val rows = (0 until 80).map { index ->
                val key = listOf("blocked", "kept", "cold", "kept")[index % 4]
                val number = (index / 4 * parents.size + parentIndex) * 4 + index % 4
                MediaItem.Builder().setMediaId("context/$key").setUri("https://example.test/$key")
                    .setCustomCacheKey(key).setMediaMetadata(MediaMetadata.Builder().setIsPlayable(true)
                        .setIsBrowsable(false).setTrackNumber(number).build()).build()
            }
            tree.selectionStore.capture(parent, rows)
        }
        repeat(20) { batch ->
            captures.forEachIndexed { parentIndex, rows ->
                (0 until 4).forEach { offset ->
                    val number = (batch * parents.size + parentIndex) * 4 + offset
                    if (offset != 2 && (offset != 0 || parentIndex >= 2)) expected += number
                    request += rows[batch * 4 + offset]
                }
            }
        }
        fun append() = callback.onAddMediaItems(mockk<MediaSession>(), mockk<MediaSession.ControllerInfo>(), request)
            .get(15, TimeUnit.SECONDS)
        val first = append()
        assertEquals(expected, first.map { it.mediaMetadata.trackNumber })
        assertEquals(expected.size, first.size)
        coVerify(exactly = 1) { songs.getAllDislikedIds() }
        coVerify(exactly = 1) { songs.getSongsByDislikedArtists() }
        coVerify(exactly = 1) { songs.getSongsByDislikedAlbums() }
        verify(exactly = 2) { formats.allWithSongs(any(), any()) }
        excluded = emptyList()
        val fresh = append()
        assertEquals(request.indices.filter { it % 4 != 2 }, fresh.map { it.mediaMetadata.trackNumber })
        coVerify(exactly = 2) { songs.getAllDislikedIds() }
        verify(exactly = 4) { formats.allWithSongs(any(), any()) }
    }
}
