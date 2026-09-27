package app.n_zik.android.playback.services

import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.Thumbnail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function I/O matrix for the video-artist fallback
 * (spec-video-artist-search-fallback.md): the [sameVideo] identity check
 * (strict video id / conservative title+thumbnail fallback) and the
 * [channelWithBrowseId] channel extraction.
 */
class VideoArtistFallbackTest {

    private fun info(videoId: String?, name: String?) =
        Innertube.Info<NavigationEndpoint.Endpoint.Watch>(
            name = name,
            endpoint = videoId?.let { NavigationEndpoint.Endpoint.Watch(videoId = it) }
        )

    private fun author(name: String?, browseId: String?) =
        Innertube.Info<NavigationEndpoint.Endpoint.Browse>(
            name = name,
            endpoint = browseId?.let { NavigationEndpoint.Endpoint.Browse(browseId = it) }
        )

    private fun videoItem(
        info: Innertube.Info<NavigationEndpoint.Endpoint.Watch>?,
        authors: List<Innertube.Info<NavigationEndpoint.Endpoint.Browse>>? = null,
        thumbnailUrl: String? = null
    ) = Innertube.VideoItem(
        info = info,
        authors = authors,
        viewsText = null,
        durationText = null,
        thumbnail = thumbnailUrl?.let { Thumbnail(url = it, height = null, width = null) }
    )

    // --- sameVideo ---

    @Test
    fun strictVideoIdMatchIsAPerfectMatch() {
        // The video id is the arbiter of identity: it wins even with a
        // different title and a missing thumbnail.
        val result = videoItem(info = info("vid_A", "Different title"), thumbnailUrl = null)
        assertTrue(
            sameVideo(result, "vid_A", storedTitle = "Stored title", storedThumbnailUrl = "https://stored/thumb")
        )
    }

    @Test
    fun fallbackMatchesOnExactTitleIgnoreCaseAndIdenticalThumbnail() {
        val result = videoItem(info = info("vid_B", "Stored Title"), thumbnailUrl = "https://stored/thumb")
        assertTrue(
            sameVideo(result, "vid_A", storedTitle = "stored title", storedThumbnailUrl = "https://stored/thumb")
        )
    }

    @Test
    fun titleMatchAloneIsNotEnough() {
        // Same title, different thumbnail: the image is required, not optional.
        val result = videoItem(info = info("vid_B", "Stored title"), thumbnailUrl = "https://other/thumb")
        assertFalse(
            sameVideo(result, "vid_A", storedTitle = "Stored title", storedThumbnailUrl = "https://stored/thumb")
        )
    }

    @Test
    fun thumbnailMatchAloneIsNotEnough() {
        // Same thumbnail, different title.
        val result = videoItem(info = info("vid_B", "Other title"), thumbnailUrl = "https://stored/thumb")
        assertFalse(
            sameVideo(result, "vid_A", storedTitle = "Stored title", storedThumbnailUrl = "https://stored/thumb")
        )
    }

    @Test
    fun fallbackIsDisallowedWhenTheStoredThumbnailIsNull() {
        val result = videoItem(info = info("vid_B", "Stored title"), thumbnailUrl = "https://stored/thumb")
        assertFalse(sameVideo(result, "vid_A", storedTitle = "Stored title", storedThumbnailUrl = null))
    }

    @Test
    fun fallbackIsDisallowedWhenTheStoredTitleIsNull() {
        val result = videoItem(info = info("vid_B", "Some title"), thumbnailUrl = "https://stored/thumb")
        assertFalse(sameVideo(result, "vid_A", storedTitle = null, storedThumbnailUrl = "https://stored/thumb"))
    }

    @Test
    fun nullInfoAndNullsAreDefensive() {
        // A result without info cannot match at all: no crash, strict-only.
        val noInfo = videoItem(info = null)
        assertFalse(
            sameVideo(noInfo, "vid_A", storedTitle = "Stored title", storedThumbnailUrl = "https://stored/thumb")
        )
        // Null stored values: the fallback is off, but the strict video id
        // still matches (null name / null thumbnail in the result).
        val strict = videoItem(info = info("vid_A", null), thumbnailUrl = null)
        assertTrue(sameVideo(strict, "vid_A", storedTitle = null, storedThumbnailUrl = null))
        // A null result name must not crash the title comparison.
        val nullName = videoItem(info = info("vid_B", null), thumbnailUrl = "https://stored/thumb")
        assertFalse(
            sameVideo(nullName, "vid_A", storedTitle = "Stored title", storedThumbnailUrl = "https://stored/thumb")
        )
    }

    // --- channelWithBrowseId ---

    @Test
    fun authorWithBrowseIdIsReturned() {
        val result = videoItem(info = info("vid_A", "t"), authors = listOf(author("Channel", "UC_ch")))
        val channel = channelWithBrowseId(result)
        assertEquals("UC_ch", channel?.endpoint?.browseId)
        assertEquals("Channel", channel?.name)
    }

    @Test
    fun authorWithoutEndpointIsSkipped() {
        // The VideoItem.from fallbackAuthor is a plain text run with a null
        // endpoint: it is not a channel, nothing can be copied.
        val result = videoItem(info = info("vid_A", "t"), authors = listOf(author("Plain name", null)))
        assertNull(channelWithBrowseId(result))
    }

    @Test
    fun emptyOrNullAuthorsYieldNull() {
        assertNull(channelWithBrowseId(videoItem(info = info("vid_A", "t"), authors = emptyList())))
        assertNull(channelWithBrowseId(videoItem(info = info("vid_A", "t"), authors = null)))
    }

    @Test
    fun theFirstAuthorWithABrowseIdWins() {
        // The first entry is the endpoint-less fallback author; the channel is
        // the second one.
        val result = videoItem(
            info = info("vid_A", "t"),
            authors = listOf(author("Fallback", null), author("Channel", "UC_ch2"))
        )
        val channel = channelWithBrowseId(result)
        assertEquals("UC_ch2", channel?.endpoint?.browseId)
    }
}
