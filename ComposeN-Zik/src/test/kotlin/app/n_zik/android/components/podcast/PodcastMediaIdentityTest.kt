package app.n_zik.android.components.podcast

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.core.database.Converters
import app.n_zik.android.utils.asPodcastMediaItem
import app.n_zik.android.utils.podcastBrowseId
import app.n_zik.android.utils.withPodcastSource
import app.n_zik.innertube.Innertube
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@UnstableApi
class PodcastMediaIdentityTest {
    @Test
    fun episodeKeepsItsExactPodcastWhenTheQueueIsRestored() {
        val episode = Innertube.Podcast.EpisodeItem(
            title = "Episode",
            author = "Publisher",
            description = null,
            thumbnail = emptyList(),
            createdDay = null,
            durationString = null,
            videoId = "WAa1hUAlFmI",
        )
        val podcastId = "MPSPPLXK2KwV2tv0jwie5sRfW9Mrdz3gyZp9Vr"
        val item = episode.asPodcastMediaItem(podcastId)
        val restored = Converters.mediaItemFromByteArray(Converters.mediaItemToByteArray(item))

        assertEquals(podcastId, restored?.podcastBrowseId)
        assertEquals(episode.videoId, restored?.mediaId)
        assertNull(restored?.mediaMetadata?.albumTitle)
        assertNull(MediaItem.fromUri("https://example.org/track").podcastBrowseId)
    }

    @Test
    fun `locally decorated source keeps the exact provider ID in playback metadata`() {
        val item = MediaItem.fromUri("https://example.org/episode")
        val podcastId = "MPSPPLXK2KwV2tv0jwie5sRfW9Mrdz3gyZp9Vr"
        val sourced = item.withPodcastSource("$MODIFIED_PREFIX$podcastId")

        assertEquals(podcastId, sourced.podcastBrowseId)
        assertEquals(podcastId, sourced.mediaMetadata.extras?.getString("podcastBrowseId"))
        assertEquals(item.mediaId, sourced.mediaId)
        assertNull(item.withPodcastSource("${MODIFIED_PREFIX}SPOTIFY_IMPORT").podcastBrowseId)
    }
}
