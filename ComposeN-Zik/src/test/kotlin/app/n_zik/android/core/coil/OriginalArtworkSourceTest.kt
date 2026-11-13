package app.n_zik.android.core.coil

import org.junit.Assert.assertEquals
import org.junit.Test

class OriginalArtworkSourceTest {
    @Test
    fun originalRequestsFullSizeFromGoogleArtwork() {
        val url = "https://lh3.googleusercontent.com/example=w300-h300"
        val original = ImageCacheFactory.DownloadDecision(true, ImageCacheFactory.NetworkQuality.HIGH, original = true)
        assertEquals("https://lh3.googleusercontent.com/example=s0", url.thumbnail(original.sourceSize))
        assertEquals("https://lh3.googleusercontent.com/example=w1000-h1000", url.thumbnail(1000))
    }

    @Test
    fun originalSelectsTheLargestYouTubeVariantAndKeepsOtherUrls() {
        val original = ImageCacheFactory.DownloadDecision(true, ImageCacheFactory.NetworkQuality.HIGH, original = true)
        assertEquals("https://i.ytimg.com/vi/test/maxresdefault.jpg",
            "https://i.ytimg.com/vi/test/hqdefault.jpg".thumbnail(original.sourceSize))
        val local = "file:///storage/cover.png"
        assertEquals(local, local.thumbnail(original.sourceSize))
    }
}
