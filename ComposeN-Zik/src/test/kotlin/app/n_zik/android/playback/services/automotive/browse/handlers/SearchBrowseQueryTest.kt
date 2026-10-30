package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.requests.searchPageContinuation
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33], application = android.app.Application::class)
class SearchBrowseQueryTest {
    private val context: Context = org.robolectric.RuntimeEnvironment.getApplication()
    private val downloadHelper = mockk<MyDownloadHelper>(relaxed = true)
    private val handler = SearchBrowseHandler(online = { true }, profile = { "test" })
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
    fun `all seven categories load one provider batch and Suite reaches the end without accumulation`() = runTest {
        mockkStatic("it.fast4x.innertube.requests.SearchPageKt")
        // Nullable inline Result suspend APIs need the same boxing fixture as Quick Picks.
        @Suppress("UNCHECKED_CAST")
        val first = Result.success(Result.success(Innertube.ItemsPage<Innertube.Item>(emptyList(), "next-cursor")))
            as Result<Innertube.ItemsPage<Innertube.Item>?>?
        @Suppress("UNCHECKED_CAST")
        val last = Result.success(Result.success(Innertube.ItemsPage<Innertube.Item>(emptyList(), null)))
            as Result<Innertube.ItemsPage<Innertube.Item>?>?
        coEvery { Innertube.searchPage<Innertube.Item>(any(), any(), any()) } returns first
        coEvery { Innertube.searchPageContinuation<Innertube.Item>(any(), any()) } returns last
        categories.forEach { (category, _) ->
            val rows = handler.getChildren("$category/AC/DC", context, Database, downloadHelper, null)
            org.junit.Assert.assertEquals(1, rows.size)
            val next = rows.single()
            assertEquals("${context.getString(app.n_zik.android.R.string.next)} (2)", next.mediaMetadata.title.toString())
            assertTrue(next.mediaMetadata.isBrowsable == true)
            assertTrue(next.mediaMetadata.isPlayable != true)
            assertTrue(handler.getChildren(next.mediaId, context, Database, downloadHelper, null).isEmpty())
        }
        coVerify(exactly = 7) { Innertube.searchPage<Innertube.Item>("AC/DC", any(), any()) }
        coVerify(exactly = 7) { Innertube.searchPageContinuation<Innertube.Item>("next-cursor", any()) }
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
