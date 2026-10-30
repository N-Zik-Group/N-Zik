package app.n_zik.android.components.glass

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.FlagSet
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import app.n_zik.android.components.player.extractM3ECoverColors
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GlassArtworkTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `queue artwork loads before merged metadata and follows later replacement`() {
        val player = mockk<Player>(relaxed = true)
        val listener = slot<Player.Listener>()
        val original = Uri.parse("file:///queue.png")
        val replacement = Uri.parse("file:///merged.png")
        every { player.currentMediaItem } returns MediaItem.Builder().setMediaId("first")
            .setMediaMetadata(MediaMetadata.Builder().setArtworkUri(original).build()).build()
        var metadata = MediaMetadata.EMPTY
        every { player.mediaMetadata } answers { metadata }
        every { player.addListener(capture(listener)) } returns Unit
        val requests = mutableListOf<String?>()
        var observed: Color? = null
        compose.setContent {
            observed = rememberGlassArtworkColor(rememberGlassArtwork(player), true, true) { artwork, _ ->
                requests += artwork.uri
                if (artwork.uri == original.toString()) Color.Red else Color.Blue
            }
        }
        compose.runOnIdle {
            assertEquals(Color.Red, observed)
            metadata = MediaMetadata.Builder().setArtworkUri(replacement).build()
            listener.captured.onEvents(player, Player.Events(FlagSet.Builder()
                .add(Player.EVENT_MEDIA_METADATA_CHANGED).build()))
        }
        compose.runOnIdle {
            assertEquals(Color.Blue, observed)
            assertEquals(listOf(original.toString(), replacement.toString()), requests)
        }
    }

    @Test
    fun `initially disconnected player attaches and loads late first track metadata`() {
        val player = mockk<Player>(relaxed = true)
        val listener = slot<Player.Listener>()
        var metadata = MediaMetadata.EMPTY
        every { player.currentMediaItem } returns MediaItem.Builder().setMediaId("first").build()
        every { player.mediaMetadata } answers { metadata }
        every { player.addListener(capture(listener)) } returns Unit
        val activePlayer = mutableStateOf<Player?>(null)
        val loadedColor = CompletableDeferred<Color?>()
        var observed: Color? = null
        compose.setContent {
            observed = rememberGlassArtworkColor(rememberGlassArtwork(activePlayer.value), true, true) { _, _ ->
                loadedColor.await()
            }
        }
        compose.runOnIdle {
            assertNull(observed)
            activePlayer.value = player
        }
        compose.runOnIdle {
            assertNull(observed)
            metadata = MediaMetadata.Builder().setArtworkUri(Uri.parse("file:///first.png")).build()
            listener.captured.onEvents(player, Player.Events(FlagSet.Builder()
                .add(Player.EVENT_MEDIA_METADATA_CHANGED).build()))
        }
        compose.runOnIdle {
            assertNull(observed)
            loadedColor.complete(Color.Cyan)
        }
        compose.waitUntil { observed == Color.Cyan }
    }

    @Test
    fun `artwork metadata changes refresh same song and detach from old player`() {
        val player = mockk<Player>(relaxed = true)
        val listener = slot<Player.Listener>()
        var metadata = MediaMetadata.Builder().setArtworkUri(Uri.parse("file:///old.png")).build()
        var mediaItem = MediaItem.Builder().setMediaId("same-song").build()
        every { player.currentMediaItem } answers { mediaItem }
        every { player.mediaMetadata } answers { metadata }
        every { player.addListener(capture(listener)) } returns Unit
        val activePlayer = mutableStateOf<Player?>(player)
        var observed: GlassArtwork? = null
        compose.setContent { observed = rememberGlassArtwork(activePlayer.value) }
        compose.runOnIdle {
            assertEquals("file:///old.png", observed?.uri)
            metadata = MediaMetadata.Builder().setArtworkUri(Uri.parse("file:///new.png")).build()
            listener.captured.onEvents(player, Player.Events(FlagSet.Builder()
                .add(Player.EVENT_MEDIA_METADATA_CHANGED).build()))
        }
        compose.runOnIdle {
            assertEquals(GlassArtwork("same-song", "file:///new.png"), observed)
            mediaItem = MediaItem.Builder().setMediaId("next-song").build()
            metadata = MediaMetadata.Builder().setArtworkUri(Uri.parse("file:///next.png")).build()
            listener.captured.onEvents(player, Player.Events(FlagSet.Builder()
                .add(Player.EVENT_MEDIA_ITEM_TRANSITION).build()))
        }
        compose.runOnIdle {
            assertEquals(GlassArtwork("next-song", "file:///next.png"), observed)
            activePlayer.value = null
        }
        compose.runOnIdle {
            assertNull(observed)
            verify { player.removeListener(listener.captured) }
        }
    }

    @Test
    fun `cover tint follows shared dominant color despite a small vibrant patch`() = runBlocking {
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(40 * 40) { index ->
            if (index % 40 < 4) 0xFF00FF40.toInt() else 0xFF8899AA.toInt()
        }
        bitmap.setPixels(pixels, 0, 40, 0, 0, 40, 40)
        val expected = requireNotNull(extractM3ECoverColors(bitmap, isDark = true))
        assertNotEquals(expected.vibrant, expected.dominant)
        assertEquals(expected.dominant, extractGlassArtworkColor(bitmap, isDark = true)?.toArgb())
        bitmap.recycle()
    }

    @Test
    fun `white and black covers retain neutral tint instead of falling back to theme`() = runBlocking {
        listOf(android.graphics.Color.WHITE, android.graphics.Color.BLACK).forEach { source ->
            val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(source)
            val actual = requireNotNull(extractGlassArtworkColor(bitmap, isDark = true))
            assertEquals(actual.red, actual.green, .0001f)
            assertEquals(actual.red, actual.blue, .0001f)
            assertEquals(if (source == android.graphics.Color.WHITE) 1f else 0f, actual.red, .04f)
            bitmap.recycle()
        }
    }

    @Test
    fun `crossfade player replacement reattaches metadata and clears old cover while loading`() {
        val oldPlayer = mockk<Player>(relaxed = true)
        val nextPlayer = mockk<Player>(relaxed = true)
        val oldListener = slot<Player.Listener>()
        val nextListener = slot<Player.Listener>()
        every { oldPlayer.currentMediaItem } returns MediaItem.Builder().setMediaId("old").build()
        every { oldPlayer.mediaMetadata } returns MediaMetadata.Builder()
            .setArtworkUri(Uri.parse("file:///old.png")).build()
        every { oldPlayer.addListener(capture(oldListener)) } returns Unit
        every { nextPlayer.currentMediaItem } returns MediaItem.Builder().setMediaId("next").build()
        every { nextPlayer.mediaMetadata } returns MediaMetadata.Builder()
            .setArtworkUri(Uri.parse("file:///next.png")).build()
        every { nextPlayer.addListener(capture(nextListener)) } returns Unit
        val activePlayer = mutableStateOf<Player?>(oldPlayer)
        val nextColor = CompletableDeferred<Color?>()
        var observed: Color? = null
        compose.setContent {
            observed = rememberGlassArtworkColor(rememberGlassArtwork(activePlayer.value), true, true) { artwork, _ ->
                if (artwork.mediaId == "old") Color.Red else nextColor.await()
            }
        }
        compose.runOnIdle {
            assertEquals(Color.Red, observed)
            activePlayer.value = nextPlayer
        }
        compose.runOnIdle {
            assertNull(observed)
            verify { oldPlayer.removeListener(oldListener.captured) }
            verify { nextPlayer.addListener(nextListener.captured) }
            nextColor.complete(Color.Green)
        }
        compose.waitUntil { observed == Color.Green }
        compose.runOnIdle { activePlayer.value = null }
        compose.runOnIdle {
            assertNull(observed)
            verify { nextPlayer.removeListener(nextListener.captured) }
        }
    }

    @Test
    fun `late previous song extraction cannot replace the new song tint`() {
        val artwork = mutableStateOf(GlassArtwork("old", "file:///old.png"))
        var oldRequest: Continuation<Color?>? = null
        var observed: Color? = null
        compose.setContent {
            observed = rememberGlassArtworkColor(artwork.value, true, true) { current, _ ->
                // Model a callback loader that completes even after its caller is cancelled.
                if (current.mediaId == "old") suspendCoroutine { oldRequest = it }
                else Color.Blue
            }
        }
        compose.runOnIdle {
            requireNotNull(oldRequest)
            artwork.value = GlassArtwork("next", "file:///next.png")
        }
        compose.runOnIdle { assertEquals(Color.Blue, observed) }
        compose.runOnIdle { requireNotNull(oldRequest).resume(Color.Red) }
        compose.runOnIdle { assertEquals(Color.Blue, observed) }
    }
}
