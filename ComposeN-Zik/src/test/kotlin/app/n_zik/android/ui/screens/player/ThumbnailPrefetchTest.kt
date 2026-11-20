package app.n_zik.android.ui.screens.player

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
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
@Config(sdk = [33], application = Application::class)
class ThumbnailPrefetchTest {
    private val player = mockk<Player> {
        every { currentMediaItemIndex } returns 1
        every { mediaItemCount } returns 5
    }

    @Test fun `prefetch follows playback order even when shuffle skips queue entries`() {
        every { player.nextMediaItemIndex } returns 4
        every { player.previousMediaItemIndex } returns 0
        every { player.getMediaItemAt(4) } returns track("https://example.test/next.png")
        every { player.getMediaItemAt(0) } returns track("https://example.test/previous.png")

        assertEquals(
            listOf("https://example.test/next.png", "https://example.test/previous.png"),
            player.neighboringThumbnailUrls(),
        )
    }

    @Test fun `prefetch ignores the current track and unavailable neighbors`() {
        every { player.nextMediaItemIndex } returns 1
        every { player.previousMediaItemIndex } returns C.INDEX_UNSET

        assertEquals(emptyList<String>(), player.neighboringThumbnailUrls())
    }

    private fun track(artwork: String): MediaItem = MediaItem.Builder()
        .setMediaMetadata(MediaMetadata.Builder().setArtworkUri(Uri.parse(artwork)).build())
        .build()
}
