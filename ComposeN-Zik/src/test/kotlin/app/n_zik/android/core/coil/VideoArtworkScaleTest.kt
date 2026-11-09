package app.n_zik.android.core.coil

import androidx.compose.ui.layout.ContentScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoArtworkScaleTest {
    @Test fun `video jpeg webp and signed urls follow the crop setting`() {
        for (url in listOf("https://i.ytimg.com/vi/video/maxresdefault.jpg",
            "https://i9.ytimg.com/vi_webp/video/hqdefault.webp?sqp=abc",
            "https://I.YTIMG.COM/vi/video/mqdefault.jpg")) {
            assertTrue(isVideoArtwork(url))
            for (requested in listOf(ContentScale.Crop, ContentScale.FillHeight, ContentScale.FillWidth)) {
                assertEquals(ContentScale.Crop, videoArtworkContentScale(url, true, requested))
                assertEquals(ContentScale.Fit, videoArtworkContentScale(url, false, requested))
            }
        }
    }

    @Test fun `custom artist album playlist and unrelated urls retain caller scaling`() {
        for (url in listOf(null, "", "file:///app_covers/video.jpg", "content://media/image/1",
            "modified:https://i.ytimg.com/vi/video/hqdefault.jpg",
            "pinned:modified:https://i.ytimg.com/vi/video/hqdefault.jpg",
            "e:monthly:modified:https://i.ytimg.com/vi/video/hqdefault.jpg",
            "https://lh3.googleusercontent.com/artwork", "https://i.ytimg.com/pl_c/playlist/image.jpg",
            "https://i.ytimg.com/podcasts_artwork/podcast/image.jpg",
            "https://example.com/i.ytimg.com/vi/video/hqdefault.jpg",
            "https://i.ytimg.com.evil.test/vi/video/hqdefault.jpg")) {
            assertFalse(isVideoArtwork(url))
            for (crop in listOf(false, true)) {
                assertEquals(ContentScale.FillHeight, videoArtworkContentScale(url, crop, ContentScale.FillHeight))
            }
        }
    }

    @Test fun `non custom prefixes keep video classification`() {
        assertTrue(isVideoArtwork("pinned:e:https://i.ytimg.com/vi/video/hqdefault.jpg"))
    }

    @Test fun `explicit fitting and unscaled layouts retain their geometry`() {
        for (requested in listOf(ContentScale.Fit, ContentScale.Inside, ContentScale.None, ContentScale.FillBounds)) {
            for (crop in listOf(false, true)) {
                assertEquals(requested, videoArtworkContentScale("https://i.ytimg.com/vi/video/hqdefault.jpg", crop, requested))
            }
        }
    }
}
