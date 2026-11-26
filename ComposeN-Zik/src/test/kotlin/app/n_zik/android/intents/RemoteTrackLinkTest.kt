package app.n_zik.android.intents

import android.app.Application
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.requests.nextPage
import app.n_zik.innertube.requests.song
import io.mockk.coEvery
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RemoteTrackLinkTest {
    private val videoId = "lvzGnko1zQA"

    @Before
    fun setUp() {
        mockkStatic("app.n_zik.innertube.requests.QueueKt")
        mockkStatic("app.n_zik.innertube.requests.NextPageKt")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun invalidIdDoesNotCreateAMediaItem() = runTest {
        assertNull(resolveRemoteTrackLink("Go Easy"))
    }

    @Test
    fun aLinkWithNoMetadataKeepsTheExactVideoId() = runTest {
        coEvery { Innertube.song(videoId) } returns null
        coEvery { Innertube.nextPage(videoId = videoId) } returns null

        val link = requireNotNull(resolveRemoteTrackLink(videoId))

        assertEquals(videoId, link.item.mediaId)
        assertEquals(videoId, link.item.localConfiguration?.uri.toString())
        assertNull(link.explicit)
    }

    @Test
    fun aDifferentVideoFromTheQueueCannotReplaceTheLink() = runTest {
        val unrelated = Innertube.SongItem(
            info = Innertube.Info(
                name = "Other track",
                endpoint = NavigationEndpoint.Endpoint.Watch(videoId = "6ms61yZ5RVk"),
            ),
            authors = null,
            album = null,
            durationText = null,
            thumbnail = null,
        )
        coEvery { Innertube.song(videoId) } returns boxedSongResult(unrelated)
        coEvery { Innertube.nextPage(videoId = videoId) } returns null

        val link = requireNotNull(resolveRemoteTrackLink(videoId))

        assertEquals(videoId, link.item.mediaId)
        assertNull(link.item.mediaMetadata.title)
    }
    @Suppress("UNCHECKED_CAST")
    private fun boxedSongResult(song: Innertube.SongItem): Result<Innertube.SongItem?>? =
        Result.success(Result.success(song)) as Result<Innertube.SongItem?>?
}
