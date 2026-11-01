package app.n_zik.android.components.playlist

import it.fast4x.innertube.models.v0624.podcasts.BrowsePodcastsResponse0624
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Synchronous JUnit methods bridge the suspend network coordinator without a real transport.
class CompletePodcastSourceTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val id = "MPSPopaque"

    private fun episode(id: String, playedText: String? = null): String {
        val played = playedText?.let { """, "playedText":{"runs":[{"text":" • "},{"text":"$it"}]}""" }.orEmpty()
        return """{"musicMultiRowListItemRenderer":{"title":{"runs":[{"text":"$id"}]},"onTap":{"watchEndpoint":{"videoId":"$id"}},"playbackProgress":{"musicPlaybackProgressRenderer":{"durationText":{"runs":[{"text":" • "},{"text":"12 min"}]}$played}}}}"""
    }
    private fun continuations(token: String?) = token?.let {
        """, "continuations":[{"nextContinuationData":{"continuation":"$it"}}]"""
    }.orEmpty()
    private fun initial(token: String? = "next", playedText: String? = null) = json.decodeFromString<BrowsePodcastsResponse0624>(
        """{"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[{"musicResponsiveHeaderRenderer":{"title":{"runs":[{"text":"Podcast"}]},"straplineTextOne":{"runs":[{"text":"Host"}]},"description":{"musicDescriptionShelfRenderer":{"description":{"runs":[{"text":"A podcast"}]}}}}}]}}}}],"secondaryContents":{"sectionListRenderer":{"contents":[{"musicShelfRenderer":{"contents":[${episode("first", playedText)}]${continuations(token)}}}]}}}}}"""
    )
    private fun next(token: String? = null, playedText: String? = null) = json.decodeFromString<BrowsePodcastsResponse0624>(
        """{"continuationContents":{"musicShelfContinuation":{"contents":[${episode("second", playedText)}]${continuations(token)}}}}"""
    )

    @Test fun `localized played labels deserialize through initial and continuation responses`() = runBlocking {
        for (label in listOf("Lue", "Played", "Reproduzido", "再生済み", null)) {
            val first = initial(playedText = label)
            val continuation = next(playedText = label)
            val firstProgress = first.contents?.twoColumnBrowseResultsRenderer?.secondaryContents
                ?.sectionListRenderer?.contents?.single()?.musicShelfRenderer?.contents?.single()
                ?.musicMultiRowListItemRenderer?.playbackProgress?.musicPlaybackProgressRenderer
            val nextProgress = continuation.continuationContents?.musicShelfContinuation?.contents?.single()
                ?.musicMultiRowListItemRenderer?.playbackProgress?.musicPlaybackProgressRenderer
            if (label == null) {
                assertNull(firstProgress?.playedText)
                assertNull(nextProgress?.playedText)
            } else {
                assertEquals(listOf(" • ", label), firstProgress?.playedText?.runs?.map { it.text })
                assertEquals(listOf(" • ", label), nextProgress?.playedText?.runs?.map { it.text })
            }
            val page = loadCompletePodcastPage(id) { _, token -> if (token == null) first else continuation }
            assertEquals("Podcast", page.title)
            assertEquals("Host", page.author)
            assertEquals("A podcast", page.description)
            assertEquals(listOf("first", "second"), page.listEpisode.map { it.videoId })
            assertEquals(listOf("12:00", "12:00"), page.listEpisode.map { it.durationString })
        }
    }

    @Test fun `complete pages preserve opaque browse identity and only continuation token follows`() = runBlocking {
        val requests = mutableListOf<Pair<String?, String?>>()
        val episodes = loadCompletePodcastEpisodes(id) { browse, continuation ->
            requests += browse to continuation
            if (continuation == null) initial() else next()
        }
        assertEquals(listOf(id to null, null to "next"), requests)
        assertEquals(listOf("first", "second"), episodes.map { it.videoId })
    }

    @Test fun `missing continuation shelf cannot return a partial source`() = runBlocking {
        val outcome = runCatching {
            loadCompletePodcastEpisodes(id) { _, continuation ->
                if (continuation == null) initial() else BrowsePodcastsResponse0624()
            }
        }
        assertTrue(outcome.isFailure)
    }

    @Test fun `repeated continuation aborts within bounded requests`() = runBlocking {
        var requests = 0
        val outcome = runCatching {
            loadCompletePodcastEpisodes(id) { _, continuation ->
                requests++
                if (continuation == null) initial() else next("next")
            }
        }
        assertTrue(outcome.isFailure)
        assertEquals(2, requests)
    }

    @Test fun `transport failure cancellation and malformed first page never return partial episodes`() = runBlocking {
        assertTrue(runCatching {
            loadCompletePodcastEpisodes(id) { _, continuation ->
                if (continuation == null) initial() else error("network failed")
            }
        }.isFailure)
        val cancelled = runCatching {
            loadCompletePodcastEpisodes(id) { _, _ -> throw CancellationException("cancelled") }
        }
        assertTrue(cancelled.exceptionOrNull() is CancellationException)
        assertTrue(runCatching {
            loadCompletePodcastEpisodes(id) { _, _ -> BrowsePodcastsResponse0624() }
        }.isFailure)
    }
}
