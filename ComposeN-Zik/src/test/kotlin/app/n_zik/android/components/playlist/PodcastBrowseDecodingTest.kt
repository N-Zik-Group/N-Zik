package app.n_zik.android.components.playlist

import it.fast4x.innertube.models.v0624.podcasts.BrowsePodcastsResponse0624
import it.fast4x.innertube.models.v0624.podcasts.IconType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Synchronous JUnit methods bridge the suspend page loader without a real transport.
class PodcastBrowseDecodingTest {
    private val browseId = "MPSPpodcast"

    private fun episode(id: String, icon: String) = """
        {"musicMultiRowListItemRenderer":{
            "title":{"runs":[{"text":"Episode $id"}]},
            "onTap":{"watchEndpoint":{"videoId":"$id"}},
            "menu":{"menuRenderer":{"items":[{"menuServiceItemRenderer":{
                "icon":{"iconType":"$icon"}
            }}]}}
        }}
    """.trimIndent()

    private fun initial(icon: String, title: String = "Podcast") = """
        {"contents":{"twoColumnBrowseResultsRenderer":{
            "tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[{
                "musicResponsiveHeaderRenderer":{
                    "title":{"runs":[{"text":"$title"}]},
                    "straplineTextOne":{"runs":[{"text":"Host"}]},
                    "buttons":[{"menuRenderer":{"items":[{"toggleMenuServiceItemRenderer":{
                        "defaultIcon":{"iconType":"$icon"},
                        "toggledIcon":{"iconType":"CHECK"}
                    }}]}}]
                }
            }]}}}}],
            "secondaryContents":{"sectionListRenderer":{"contents":[{"musicShelfRenderer":{
                "contents":[${episode("first", "PLAY_ARROW")}],
                "continuations":[{"nextContinuationData":{"continuation":"next"}}]
            }}]}}
        }},"futureDecoration":{"ignored":true}}
    """.trimIndent()

    private fun continuation(icon: String) = """
        {"continuationContents":{"musicShelfContinuation":{
            "contents":[${episode("second", icon)}]
        }}}
    """.trimIndent()

    @Test fun `KEEP and future header icons decode through the complete page loader`() = runBlocking {
        for (icon in listOf("KEEP", "FUTURE_PODCAST_ICON")) {
            val raw = initial(icon)
            assertTrue(runCatching {
                Json { ignoreUnknownKeys = true }.decodeFromString<BrowsePodcastsResponse0624>(raw)
            }.isFailure)
            val first = decodePodcastBrowseResponse(raw)
            val toggle = first.contents?.twoColumnBrowseResultsRenderer?.tabs?.single()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.single()
                ?.musicResponsiveHeaderRenderer?.buttons?.single()?.menuRenderer?.items?.single()
                ?.toggleMenuServiceItemRenderer
            assertNull(toggle?.defaultIcon?.iconType)
            assertEquals(IconType.Check, toggle?.toggledIcon?.iconType)
            val requests = mutableListOf<Pair<String?, String?>>()
            val page = loadCompletePodcastPage(browseId) { id, token ->
                requests += id to token
                if (token == null) first else decodePodcastBrowseResponse(continuation("FUTURE_EPISODE_ICON"))
            }
            assertEquals(listOf(browseId to null, null to "next"), requests)
            assertEquals("Podcast", page.title)
            assertEquals("Host", page.author)
            assertEquals(listOf("first", "second"), page.listEpisode.map { it.videoId })
            assertEquals(listOf("Episode first", "Episode second"), page.listEpisode.map { it.title })
        }
    }

    @Test fun `recognized header icons retain their enum values`() {
        val response = decodePodcastBrowseResponse(initial("BOOKMARK"))
        val icon = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.single()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.single()
            ?.musicResponsiveHeaderRenderer?.buttons?.single()?.menuRenderer?.items?.single()
            ?.toggleMenuServiceItemRenderer?.defaultIcon?.iconType
        assertEquals(IconType.Bookmark, icon)
    }

    @Test fun `tolerant decorations cannot turn invalid pages into complete snapshots`() = runBlocking {
        for (raw in listOf("{}", initial("KEEP", title = ""))) {
            assertTrue(runCatching {
                loadCompletePodcastPage(browseId) { _, _ -> decodePodcastBrowseResponse(raw) }
            }.isFailure)
        }
        assertTrue(runCatching {
            loadCompletePodcastPage(browseId) { _, token ->
                decodePodcastBrowseResponse(if (token == null) initial("KEEP") else "{}")
            }
        }.isFailure)
    }

    @Test fun `malformed JSON and invalid structural types still fail decoding`() {
        for (raw in listOf("{", """{"contents":42}""")) {
            assertTrue(runCatching { decodePodcastBrowseResponse(raw) }.isFailure)
        }
    }
}
