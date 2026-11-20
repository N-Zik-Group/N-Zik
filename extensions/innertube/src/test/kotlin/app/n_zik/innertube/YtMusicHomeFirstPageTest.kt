package app.n_zik.innertube

import app.n_zik.innertube.models.BrowseResponse
import com.metrolist.innertubex.InnerTube
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class YtMusicHomeFirstPageTest {
    private val scope = Innertube.HomeBrowseScope(mockk(), InnerTube.SessionSnapshot(), setLogin = true)
    private val json = Json { explicitNulls = false }

    @BeforeEach
    fun mockRequests() {
        mockkObject(Innertube)
    }

    @AfterEach
    fun restoreRequests() {
        unmockkObject(Innertube)
    }

    @Test
    fun `matching Home first page is reused while continuations and later attempts load independently`() = runTest {
        coEvery { Innertube.homeBrowseScope(true) } returns scope
        coEvery { Innertube.browseHome(scope, null) } returns firstPage
        coEvery { Innertube.browseHome(scope, "next-page") } returns continuation

        val firstPage = YtMusic.getHomeFirstPage(setLogin = true).getOrThrow()
        assertEquals(listOf("recommended-song"), firstPage.quickPicks.map { it.key })
        coVerify(exactly = 0) { Innertube.browseHome(scope, "next-page") }

        val home = YtMusic.getHomePage(setLogin = true, firstPage = firstPage).getOrThrow()
        assertEquals(listOf("Quick picks", "More music"), home.sections.map { it.title })
        coVerify(exactly = 1) { Innertube.browseHome(scope, null) }
        coVerify(exactly = 1) { Innertube.browseHome(scope, "next-page") }

        YtMusic.getHomePage(setLogin = true).getOrThrow()
        coVerify(exactly = 2) { Innertube.browseHome(scope, null) }
        coVerify(exactly = 2) { Innertube.browseHome(scope, "next-page") }
    }

    @Test
    fun `account or request auth changes require a new Home first page`() = runTest {
        val switched = scope.copy(session = scope.session.copy(authUser = "1"))
        val guest = switched.copy(setLogin = false)
        coEvery { Innertube.homeBrowseScope(true) } returnsMany listOf(scope, switched)
        coEvery { Innertube.homeBrowseScope(false) } returns guest
        coEvery { Innertube.browseHome(scope, null) } returns firstPage
        coEvery { Innertube.browseHome(switched, null) } returns firstPage
        coEvery { Innertube.browseHome(guest, null) } returns firstPage
        coEvery { Innertube.browseHome(switched, "next-page") } returns continuation
        coEvery { Innertube.browseHome(guest, "next-page") } returns continuation

        val firstPage = YtMusic.getHomeFirstPage(setLogin = true).getOrThrow()
        YtMusic.getHomePage(setLogin = true, firstPage = firstPage).getOrThrow()
        YtMusic.getHomePage(setLogin = false, firstPage = firstPage).getOrThrow()

        coVerify(exactly = 1) { Innertube.browseHome(switched, null) }
        coVerify(exactly = 1) { Innertube.browseHome(guest, null) }
        coVerify(exactly = 0) { Innertube.browseHome(scope, "next-page") }
    }

    private val firstPage: BrowseResponse = json.decodeFromString(
        """
        {
          "contents": {"singleColumnBrowseResultsRenderer": {"tabs": [{"tabRenderer": {"content": {
            "sectionListRenderer": {
              "contents": [{"musicCarouselShelfRenderer": {
                "header": {"musicCarouselShelfBasicHeaderRenderer": {"title": {"runs": [{"text": "Quick picks"}]}}},
                "contents": [{"musicTwoRowItemRenderer": {
                  "title": {"runs": [{"text": "Recommended song"}]},
                  "aspectRatio": "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE",
                  "navigationEndpoint": {"watchEndpoint": {"videoId": "recommended-song"}}
                }}]
              }}],
              "continuations": [{"nextContinuationData": {"continuation": "next-page"}}]
            }
          }}}]}}
        }
        """.trimIndent(),
    )

    private val continuation: BrowseResponse = json.decodeFromString(
        """
        {
          "continuationContents": {"sectionListContinuation": {
            "contents": [{"musicCarouselShelfRenderer": {
              "header": {"musicCarouselShelfBasicHeaderRenderer": {"title": {"runs": [{"text": "More music"}]}}},
              "contents": []
            }}]
          }}
        }
        """.trimIndent(),
    )
}
