package app.n_zik.android.components.theme

import android.app.Application
import android.net.Uri
import androidx.media3.common.FlagSet
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DynamicPaletteArtworkListenerTest {
    @Test
    fun `first track artwork arriving after transition updates theme without navigation`() {
        val player = mockk<Player>(relaxed = true)
        var item: MediaItem? = null
        var metadata = MediaMetadata.EMPTY
        every { player.currentMediaItem } answers { item }
        every { player.mediaMetadata } answers { metadata }
        val published = mutableListOf<Uri?>()
        val listener = DynamicPaletteArtworkListener(player) { published += it }

        listener.publishCurrentArtwork()
        item = MediaItem.Builder().setMediaId("first").build()
        listener.onEvents(player, events(Player.EVENT_MEDIA_ITEM_TRANSITION))
        val artwork = Uri.parse("file:///first.png")
        metadata = MediaMetadata.Builder().setArtworkUri(artwork).build()
        listener.onEvents(player, events(Player.EVENT_MEDIA_METADATA_CHANGED))

        assertEquals(listOf(null, null, artwork), published)
        listener.onEvents(player, events(Player.EVENT_TIMELINE_CHANGED))
        assertEquals(3, published.size)
        // The queue item's metadata stays empty; the merged player metadata owns this update.
        assertEquals(null, item?.mediaMetadata?.artworkUri)
    }

    @Test
    fun `initial queue artwork is available before merged metadata and later replacement refreshes`() {
        val player = mockk<Player>(relaxed = true)
        val original = Uri.parse("file:///original.png")
        val replacement = Uri.parse("file:///replacement.png")
        every { player.currentMediaItem } returns MediaItem.Builder().setMediaId("restored")
            .setMediaMetadata(MediaMetadata.Builder().setArtworkUri(original).build()).build()
        var metadata = MediaMetadata.EMPTY
        every { player.mediaMetadata } answers { metadata }
        val published = mutableListOf<Uri?>()
        val listener = DynamicPaletteArtworkListener(player) { published += it }

        listener.publishCurrentArtwork()
        metadata = MediaMetadata.Builder().setArtworkUri(replacement).build()
        listener.onEvents(player, events(Player.EVENT_MEDIA_METADATA_CHANGED))

        assertEquals(listOf(original, replacement), published)
    }

    private fun events(event: Int) = Player.Events(FlagSet.Builder().add(event).build())
}
