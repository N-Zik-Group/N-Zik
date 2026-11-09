package app.n_zik.android.playback.services.automotive.models

import android.app.Application
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AutoMediaDisplayNamesTest {
    @Test
    fun `album browser hides internal title and author markers without changing its id`() {
        val item = SessionMediaItemMapper.mapAlbumToMediaItem(
            parentId = "albums",
            id = "modified:browse-id",
            title = "modified:e:Album: live",
            authorsText = "modified:Artist: ensemble",
            thumbnailUrl = null,
        )

        assertEquals("albums/modified:browse-id", item.mediaId)
        assertEquals("Album: live", item.mediaMetadata.title.toString())
        assertEquals("Artist: ensemble", item.mediaMetadata.subtitle.toString())
        assertEquals("Artist: ensemble", item.mediaMetadata.artist.toString())
    }

    @Test
    fun `artist browser keeps missing subtitles absent and preserves ordinary colons`() {
        val item = SessionMediaItemMapper.mapArtistToMediaItem(
            parentId = "artists",
            id = "artist-id",
            name = "Artist: collective",
            thumbnailUrl = null,
        )

        assertEquals("Artist: collective", item.mediaMetadata.title.toString())
        assertNull(item.mediaMetadata.subtitle)
        assertNull(item.mediaMetadata.artist)
    }
}
