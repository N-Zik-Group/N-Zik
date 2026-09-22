package app.n_zik.android.utils

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.it.fast4x.rimusic.utils.isExplicit
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the explicit-detection contract the Discord title marker relies on: the
 * presence title carries the explicit marker exactly like the app's notification
 * and widgets, driven by the app's own [isExplicit] property. A title carrying the
 * "e:" explicit prefix must flag the media explicit; a plain title must not.
 */
class MediaItemExplicitTest {

    private fun mediaItem(title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId("song1")
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()

    @Test
    fun `a title with the explicit prefix flags the media explicit`() {
        assertTrue(mediaItem("e:Some Song").isExplicit)
    }

    @Test
    fun `a plain title does not flag the media explicit`() {
        assertFalse(mediaItem("Some Song").isExplicit)
        assertFalse(mediaItem("").isExplicit)
    }
}
