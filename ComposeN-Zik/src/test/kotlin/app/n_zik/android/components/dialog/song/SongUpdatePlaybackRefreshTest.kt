package app.n_zik.android.components.dialog.song

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.utils.EXPLICIT_BUNDLE_TAG
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.hasExplicitPrefix
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SongUpdatePlaybackRefreshTest {
    @Test
    fun `committed reset replaces queue metadata without replacing media configuration or playback position`() {
        val original = MediaItem.Builder().setMediaId("abcdefghijk").setUri("https://audio/stream")
            .setCustomCacheKey("cache-key").setMediaMetadata(
                MediaMetadata.Builder().setTitle("Custom").setArtist("Custom artist")
                    .setArtworkUri(Uri.parse("file:///custom.jpg"))
                    .setArtworkData(byteArrayOf(1, 2, 3), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .setExtras(Bundle().apply {
                        putString("albumId", "album")
                        putBoolean("isCoverModified", true)
                        putBoolean(EXPLICIT_BUNDLE_TAG, true)
                        putBoolean("androidx.media3.session.EXTRAS_KEY_IS_EXPLICIT", true)
                    }).build()
            ).build()
        val other = original.buildUpon().setMediaId("other-video").build()
        val player = mockk<Player>(relaxed = true)
        every { player.mediaItemCount } returns 2
        every { player.getMediaItemAt(0) } returns original
        every { player.getMediaItemAt(1) } returns other
        val reset = Song(original.mediaId, "Original", "Original artist", "3:00", "https://provider/cover")

        SongUpdatePlaybackRefresh.refresh(player, reset, SongUpdateSelection(title = true, authors = true, thumbnail = true))

        val replacement = slot<MediaItem>()
        verify(exactly = 1) { player.replaceMediaItem(0, capture(replacement)) }
        verify(exactly = 0) { player.replaceMediaItem(1, any()) }
        verify(exactly = 0) { player.seekTo(any<Long>()) }
        verify(exactly = 0) { player.setMediaItems(any<List<MediaItem>>()) }
        assertEquals(original.localConfiguration, replacement.captured.localConfiguration)
        val metadata = replacement.captured.mediaMetadata
        assertEquals("Original", metadata.title)
        assertEquals("Original artist", metadata.artist)
        assertEquals("https://provider/cover", metadata.artworkUri.toString())
        assertNull(metadata.artworkData)
        assertEquals("album", metadata.extras?.getString("albumId"))
        assertEquals("https://provider/cover", metadata.extras?.getString(SessionMediaItemMapper.ORIGINAL_ARTWORK_SOURCE_EXTRA))
        assertFalse(requireNotNull(metadata.extras).getBoolean("isCoverModified"))
        assertFalse(requireNotNull(metadata.extras).getBoolean(EXPLICIT_BUNDLE_TAG))
        assertFalse(requireNotNull(metadata.extras).getBoolean("androidx.media3.session.EXTRAS_KEY_IS_EXPLICIT"))
        assertFalse(replacement.captured.asSong.title.hasExplicitPrefix())
        assertEquals("Custom", original.mediaMetadata.title)
    }

    @Test fun `title reset also publishes newly fetched explicit status`() {
        val original = MediaItem.Builder().setMediaId("abcdefghijk")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Custom clean title").build()).build()
        val player = mockk<Player>(relaxed = true)
        every { player.mediaItemCount } returns 1
        every { player.getMediaItemAt(0) } returns original
        SongUpdatePlaybackRefresh.refresh(player,
            Song(original.mediaId, "${EXPLICIT_PREFIX}Original", "Artist", null, null),
            SongUpdateSelection(title = true))
        val replacement = slot<MediaItem>()
        verify(exactly = 1) { player.replaceMediaItem(0, capture(replacement)) }
        assertTrue(replacement.captured.asSong.title.hasExplicitPrefix())
        assertTrue(requireNotNull(replacement.captured.mediaMetadata.extras).getBoolean(EXPLICIT_BUNDLE_TAG))
        assertTrue(requireNotNull(replacement.captured.mediaMetadata.extras)
            .getBoolean("androidx.media3.session.EXTRAS_KEY_IS_EXPLICIT"))
    }
}
