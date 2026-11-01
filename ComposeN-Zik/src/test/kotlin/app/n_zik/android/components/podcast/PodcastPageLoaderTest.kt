package app.n_zik.android.components.podcast

import it.fast4x.innertube.Innertube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// Synchronous JUnit methods drive the suspend loader without a real transport.
class PodcastPageLoaderTest {
    private val page = Innertube.Podcast(
        title = "Podcast", author = "Host", authorThumbnail = null, thumbnail = emptyList(),
        description = "Description",
        listEpisode = listOf(Innertube.Podcast.EpisodeItem(
            title = "Episode", author = "Host", description = null, thumbnail = emptyList(),
            createdDay = null, durationString = "12:00", videoId = "episode",
        )),
    )

    @Test fun `failed load exits loading and retry publishes a successful complete page`() = runBlocking {
        var requests = 0
        lateinit var loader: PodcastPageLoader
        loader = PodcastPageLoader { id ->
            assertEquals("MPSPpodcast", id)
            assertEquals(PodcastPageStatus.Loading, loader.state.value.status)
            if (++requests == 1) error("Invalid response") else page
        }
        loader.load("MPSPpodcast")
        assertEquals(PodcastPageStatus.Error, loader.state.value.status)
        assertNull(loader.state.value.page)
        loader.load("MPSPpodcast")
        assertEquals(2, requests)
        assertEquals(PodcastPageStatus.Content, loader.state.value.status)
        assertSame(page, loader.state.value.page)
    }

    @Test fun `valid empty feed and cached page are distinct from loading and failure`() = runBlocking {
        val loader = PodcastPageLoader { page.copy(listEpisode = emptyList()) }
        loader.load("MPSPpodcast")
        assertEquals(PodcastPageStatus.Empty, loader.state.value.status)
        assertTrue(requireNotNull(loader.state.value.page).listEpisode.isEmpty())
        val cached = PodcastPageLoader(initialPage = page) { error("Cached page needs no request") }
        assertEquals(PodcastPageStatus.Content, cached.state.value.status)
        assertSame(page, cached.state.value.page)
    }

    @Test fun `transport cancellation propagates without publishing an error`() = runBlocking {
        val cancellation = CancellationException("Screen left")
        val loader = PodcastPageLoader { throw cancellation }
        val result = runCatching { loader.load("MPSPpodcast") }
        assertSame(cancellation, result.exceptionOrNull())
        assertEquals(PodcastPageStatus.Loading, loader.state.value.status)
        assertNull(loader.state.value.page)
    }

    @Test fun `cancelled request cannot publish even if transport returns a late page`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val loader = PodcastPageLoader {
            started.complete(Unit)
            try {
                CompletableDeferred<Unit>().await()
            } catch (_: CancellationException) {
                // Simulate a transport that swallows cancellation and returns stale data.
            }
            page
        }
        val job = launch { loader.load("MPSPpodcast") }
        started.await()
        job.cancelAndJoin()
        assertEquals(PodcastPageStatus.Loading, loader.state.value.status)
        assertNull(loader.state.value.page)
    }
}
