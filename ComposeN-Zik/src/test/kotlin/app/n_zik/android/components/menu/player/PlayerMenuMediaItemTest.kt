package app.n_zik.android.components.menu.player

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlayerMenuMediaItemTest {
    @Test
    fun `committed song replaces display fields while preserving playback configuration and extras`() {
        val original = MediaItem.Builder().setMediaId("queue/occurrence")
            .setUri("https://provider/playback").setCustomCacheKey("canonical-song")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Stale title")
                .setAlbumTitle("Provider album").setArtworkData(byteArrayOf(1, 2), null)
                .setExtras(Bundle().apply { putString("providerToken", "session"); putBoolean("is_explicit", true) }).build())
            .build()
        val song = Song("canonical-song", "New title", "New artist", "4:00", "https://provider/new-cover")
        val current = playerMenuMediaItem(original, song)
        assertEquals(song.id, playerMenuSongId(original))
        assertEquals(song.id, current.mediaId)
        assertEquals(original.localConfiguration, current.localConfiguration)
        assertEquals("New title", current.mediaMetadata.title)
        assertEquals("New artist", current.mediaMetadata.artist)
        assertEquals("Provider album", current.mediaMetadata.albumTitle)
        assertEquals(Uri.parse(song.thumbnailUrl), current.mediaMetadata.artworkUri)
        assertNull(current.mediaMetadata.artworkData)
        assertEquals("session", current.mediaMetadata.extras?.getString("providerToken"))
        assertFalse(current.mediaMetadata.extras?.getBoolean("is_explicit") == true)
    }
}
