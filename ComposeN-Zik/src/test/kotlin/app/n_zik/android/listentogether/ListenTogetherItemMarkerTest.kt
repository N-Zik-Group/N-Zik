package app.n_zik.android.listentogether

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract for the Listen Together MediaItem marker (2026-09-26, user-reported: the host
 * app Metrolist carries its own — sometimes misaligned — metadata, e.g. a literal "Titre"
 * byline or the title as album, which polluted the N-Zik library with garbage
 * Artist/Album rows). Items built for LT sessions must be recognized so the playback
 * pipeline skips every library write for them; regular items must not be.
 */
class ListenTogetherItemMarkerTest {

    @Before
    fun setUp() {
        // The bridge object initializes a Dispatchers.Main scope in <clinit>
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun mediaItemWithLtMarker(marker: Boolean, mediaId: String = "dQw4w9WgXcQ"): MediaItem {
        val extras = mockk<Bundle>(relaxed = true)
        every { extras.getBoolean(ListenTogetherPlayerBridge.LISTEN_TOGETHER_EXTRA, false) } returns
            marker
        val metadata = MediaMetadata.Builder().setExtras(extras).build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)
            .build()
    }

    @Test
    fun `items built for listen together sessions are recognized`() {
        assertTrue(ListenTogetherPlayerBridge.isListenTogetherItem(mediaItemWithLtMarker(true)))
    }

    @Test
    fun `regular items are not recognized as listen together`() {
        assertFalse(ListenTogetherPlayerBridge.isListenTogetherItem(mediaItemWithLtMarker(false)))
    }

    @Test
    fun `null item is not a listen together item`() {
        assertFalse(ListenTogetherPlayerBridge.isListenTogetherItem(null))
    }

    @Test
    fun `items without extras are not recognized as listen together`() {
        val metadata = MediaMetadata.Builder().build()
        val item = MediaItem.Builder()
            .setMediaId("dQw4w9WgXcQ")
            .setMediaMetadata(metadata)
            .build()
        assertFalse(ListenTogetherPlayerBridge.isListenTogetherItem(item))
    }

    // Thread-safety contract (2026-09-26, "load à l'infini"): the stream pipeline reads
    // the Listen Together marker from the ExoPlayer IO thread, where the player must not
    // be touched ("Player is accessed on the wrong thread"). isListenTogetherVideo is
    // therefore backed by a main-thread-maintained cache — this is the cached value it
    // computes for the current item.

    @Test
    fun `the cached lt video id is the id of the current listen together item`() {
        assertEquals("dQw4w9WgXcQ", ListenTogetherPlayerBridge.listenTogetherVideoIdOf(mediaItemWithLtMarker(true)))
    }

    @Test
    fun `the cached lt video id strips the media id path prefix`() {
        assertEquals("dQw4w9WgXcQ", ListenTogetherPlayerBridge.listenTogetherVideoIdOf(mediaItemWithLtMarker(true, "songs/dQw4w9WgXcQ")))
    }

    @Test
    fun `regular items yield no cached lt video id`() {
        assertNull(ListenTogetherPlayerBridge.listenTogetherVideoIdOf(mediaItemWithLtMarker(false)))
    }

    @Test
    fun `null item yields no cached lt video id`() {
        assertNull(ListenTogetherPlayerBridge.listenTogetherVideoIdOf(null))
    }
}
