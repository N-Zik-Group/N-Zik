package app.n_zik.android.playback.services.automotive.session

import android.app.Application
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.ArtworkContentProvider
import app.n_zik.android.playback.services.automotive.browse.AutoBrowseTree
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AutoArtworkUriQualityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var callback: AutoSessionCallback
    private lateinit var tree: AutoBrowseTree
    private val small = "https://lh3.googleusercontent.com/queen=w60-h60-l90-rj"
    private val best = small.replace("w60-h60", "w1200-h1200")

    @Before fun setUp() {
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        callback = AutoSessionCallback(context, Database, mockk<MyDownloadHelper>(relaxed = true))
        tree = AutoSessionCallback::class.java.getDeclaredField("autoBrowseTree").apply {
            isAccessible = true
        }.get(callback) as AutoBrowseTree
    }

    @After fun tearDown() {
        callback.release()
        tree.clearCache()
        unmockkAll()
    }

    @Test fun `best song artwork survives both overloads and scoped get set and add`() {
        val song = Song("quality-queen", "Queen", "Artist", "3:00", small)
        val path = AutoSessionConstants.ID_SEARCH_SONGS
        val contextual = SessionMediaItemMapper.mapSongToMediaItem(song, path)
        val queueItem = SessionMediaItemMapper.mapSongToMediaItem(song, isFromPersistentQueue = true)
        assertEquals(best, contextual.mediaMetadata.artworkUri.toString())
        assertEquals(best, queueItem.mediaMetadata.artworkUri.toString())
        val shown = tree.selectionStore.capture(path, listOf(contextual)).single()
        assertEquals(best, shown.mediaMetadata.artworkUri.toString())
        val controller = mockk<MediaSession.ControllerInfo>()
        val get = callback.onGetItem(mockk<MediaLibrarySession>(), controller, shown.mediaId)
            .get(15, TimeUnit.SECONDS)
        assertEquals(best, requireNotNull(get.value).mediaMetadata.artworkUri.toString())
        val set = callback.onSetMediaItems(mockk<MediaSession>(), controller, mutableListOf(shown), 0, 0L)
            .get(15, TimeUnit.SECONDS)
        assertEquals(best, set.mediaItems.single().mediaMetadata.artworkUri.toString())
        val add = callback.onAddMediaItems(mockk<MediaSession>(), controller, mutableListOf(shown))
            .get(15, TimeUnit.SECONDS)
        assertEquals(best, add.single().mediaMetadata.artworkUri.toString())
    }

    @Test fun `artist and album expose best remote artwork without rewriting local sources`() {
        listOf(small to best, "content://example/googleusercontent.com=w60-h60" to
            "content://example/googleusercontent.com=w60-h60", "android.resource://example/123" to
            "android.resource://example/123").forEach { (source, expected) ->
            val artist = SessionMediaItemMapper.mapArtistToMediaItem("artists", "a", "Queen", source)
            val album = SessionMediaItemMapper.mapAlbumToMediaItem("albums", "b", "Queen", "Artist", source)
            assertEquals(expected, artist.mediaMetadata.artworkUri.toString())
            assertEquals(expected, album.mediaMetadata.artworkUri.toString())
        }
        val provider = ArtworkContentProvider.uriFor("local:42").toString()
        val local = Song("local:42", "Local", "Artist", "3:00", provider)
        assertEquals(provider, SessionMediaItemMapper.mapSongToMediaItem(local, "songs").mediaMetadata.artworkUri.toString())
        assertEquals(provider, SessionMediaItemMapper.mapSongToMediaItem(local).mediaMetadata.artworkUri.toString())
    }
}
