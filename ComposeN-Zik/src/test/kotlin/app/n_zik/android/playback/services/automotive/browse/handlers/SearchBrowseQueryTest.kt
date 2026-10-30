package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.searchPage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants

class SearchBrowseQueryTest {
    private val context = mockk<Context>(relaxed = true)
    private val downloadHelper = mockk<MyDownloadHelper>(relaxed = true)
    private val handler = SearchBrowseHandler()
    private val categories = listOf(
        AutoSessionConstants.ID_SEARCH_SONGS to Innertube.SearchFilter.Song.value,
        AutoSessionConstants.ID_SEARCH_VIDEOS to Innertube.SearchFilter.Video.value,
        AutoSessionConstants.ID_SEARCH_ARTISTS to Innertube.SearchFilter.Artist.value,
        AutoSessionConstants.ID_SEARCH_ALBUMS to Innertube.SearchFilter.Album.value,
        AutoSessionConstants.ID_SEARCH_PLAYLISTS to Innertube.SearchFilter.CommunityPlaylist.value,
        AutoSessionConstants.ID_SEARCH_FEATURED to Innertube.SearchFilter.FeaturedPlaylist.value,
        AutoSessionConstants.ID_SEARCH_PODCASTS to Innertube.SearchFilter.Podcast.value
    )

    @Before
    fun setUp() {
        mockkStatic("it.fast4x.innertube.requests.SearchPageKt")
        coEvery { Innertube.searchPage<Innertube.Item>(any(), any(), any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `all categories forward the full query after their first separator`() = runTest {
        val queries = listOf("AC/DC", "rock/pop/live", "été/東京 🎵", "foo_PAGE_2/bar_PAGE_fin")
        categories.forEach { (category, filter) ->
            queries.forEach { query ->
                handler.getChildren("$category/$query", context, Database, downloadHelper, null)
                coVerify(exactly = 1) {
                    Innertube.searchPage<Innertube.Item>(query, filter, any())
                }
            }
        }
    }

    @Test
    fun `missing or blank queries never request a search`() = runTest {
        categories.forEach { (category, _) ->
            listOf(category, "$category/", "$category/   ").forEach { parent ->
                assertTrue(handler.getChildren(parent, context, Database, downloadHelper, null).isEmpty())
            }
        }
        assertTrue(handler.getChildren("SEARCH_UNKNOWN/query", context, Database, downloadHelper, null).isEmpty())
        coVerify(exactly = 0) { Innertube.searchPage<Innertube.Item>(any(), any(), any()) }
    }
}
