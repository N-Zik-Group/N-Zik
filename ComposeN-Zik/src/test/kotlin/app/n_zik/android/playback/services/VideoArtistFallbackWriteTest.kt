package app.n_zik.android.playback.services

import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.SongArtistMap
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongArtistMapTable
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.Thumbnail
import it.fast4x.innertube.requests.searchPage
import kotlin.coroutines.SuspendFunction1
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Orchestration tests for [resolveVideoArtistFallback] (the write half of the
 * video-artist fallback): search → same-video match → Artist row
 * create/repair + SongArtistMap link, and every no-write branch.
 *
 * Harness follows [StreamResolverPageCacheTest]: [Database] is object-mocked
 * with stubbed tables; [Database.transaction] runs its block inline on the
 * calling thread (the suspending transaction commits before the caller
 * continues); `Innertube.searchPage` is static-mocked so no network happens.
 */
class VideoArtistFallbackWriteTest {

    private val artistTable = mockk<ArtistTable>(relaxed = true)
    private val songArtistMapTable = mockk<SongArtistMapTable>(relaxed = true)

    @Before
    fun setup() {
        mockkObject(Database)
        every { Database.artistTable } returns artistTable
        every { Database.songArtistMapTable } returns songArtistMapTable
        // The transaction block is a suspend lambda with the Database
        // receiver: capture it as its runtime type SuspendFunction1 (reifying
        // `suspend Database.() -> Unit` erases it to a plain Function2 and
        // fails the cast).
        val txBlock = slot<SuspendFunction1<Database, Unit>>()
        coEvery { Database.transaction(capture(txBlock)) } answers {
            // runBlocking: the MockK answers lambda is not suspend, but the
            // transaction block is — the synchronous test thread is fine here.
            runBlocking { txBlock.captured(Database) }
        }
        mockkStatic("it.fast4x.innertube.requests.SearchPageKt")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * `searchPage` returns a NULLABLE `Result<ItemsPage<T>?>?`, and MockK
     * 1.14.11's suspend-mocking support unwraps a `returns` value that is
     * itself a `kotlin.Result` one level for such functions — so the intended
     * `Result.success(page)` is wrapped in one extra `Result.success(...)`
     * layer (same trick as [StreamResolverPageCacheTest.nextPageResult]).
     */
    @Suppress("UNCHECKED_CAST")
    private fun searchPageResult(
        value: Result<Innertube.ItemsPage<Innertube.VideoItem>?>
    ): Result<Innertube.ItemsPage<Innertube.VideoItem>?>? =
        Result.success(value) as Result<Innertube.ItemsPage<Innertube.VideoItem>?>?

    private fun stubSearch(items: List<Innertube.VideoItem>) {
        coEvery {
            Innertube.searchPage<Innertube.VideoItem>(any(), any(), any())
        } returns searchPageResult(
            Result.success(Innertube.ItemsPage(items = items, continuation = null))
        )
    }

    private fun videoItem(
        resultVideoId: String,
        title: String,
        channelName: String?,
        channelBrowseId: String?,
        thumbnailUrl: String?
    ) = Innertube.VideoItem(
        info = Innertube.Info(
            name = title,
            endpoint = NavigationEndpoint.Endpoint.Watch(videoId = resultVideoId)
        ),
        authors = listOf(
            Innertube.Info(
                name = channelName,
                endpoint = channelBrowseId?.let { NavigationEndpoint.Endpoint.Browse(browseId = it) }
            )
        ),
        viewsText = null,
        durationText = null,
        thumbnail = thumbnailUrl?.let { Thumbnail(url = it, height = null, width = null) }
    )

    // --- writes on a match ---

    @Test
    fun strictMatchCreatesTheChannelArtistRowAndLink() = runBlocking {
        val videoId = "vid_strict"
        val thumb = "https://i.ytimg.com/vi/vid_strict/mqdefault.jpg"
        stubSearch(listOf(videoItem(videoId, "Some video", "SomeChannel", "UC_strict", thumb)))
        every { artistTable.findByIdDirect("UC_strict") } returns null

        resolveVideoArtistFallback(videoId, "Some video", null, thumb)

        val row = slot<Artist>()
        verify(exactly = 1) { artistTable.insertIgnore(capture(row)) }
        assertEquals("UC_strict", row.captured.id)
        assertEquals("SomeChannel", row.captured.name)
        assertEquals(null, row.captured.thumbnailUrl) // video thumb is NOT the channel avatar
        val link = slot<SongArtistMap>()
        verify(exactly = 1) { songArtistMapTable.insertIgnore(capture(link)) }
        assertEquals(videoId, link.captured.songId)
        assertEquals("UC_strict", link.captured.artistId)
        coVerify(exactly = 1) { Innertube.searchPage<Innertube.VideoItem>(any(), any(), any()) }
    }

    @Test
    fun fallbackMatchOnCleanedTitleAndThumbnailCreatesTheRow() = runBlocking {
        val videoId = "vid_fb"
        val thumb = "https://i.ytimg.com/vi/vid_fb/mqdefault.jpg"
        // The stored title carries an explicit prefix; the query and the
        // fallback comparison must both use the CLEANED title, and the
        // result (different videoId) matches on title (ignoreCase) + thumb.
        stubSearch(listOf(videoItem("other_vid", "some VIDEO", "SomeChannel", "UC_fb", thumb)))
        every { artistTable.findByIdDirect("UC_fb") } returns null

        resolveVideoArtistFallback(videoId, null, "e:Some video", thumb)

        val query = slot<String>()
        coVerify(exactly = 1) { Innertube.searchPage<Innertube.VideoItem>(capture(query), any(), any()) }
        assertEquals("Some video", query.captured)
        val row = slot<Artist>()
        verify(exactly = 1) { artistTable.insertIgnore(capture(row)) }
        assertEquals("SomeChannel", row.captured.name)
        verify(exactly = 1) { songArtistMapTable.insertIgnore(any<SongArtistMap>()) }
    }

    @Test
    fun wipedRowIsRepairedWithTheChannelName() = runBlocking {
        val videoId = "vid_repair"
        stubSearch(listOf(videoItem(videoId, "Some video", "SomeChannel", "UC_repair", "https://t")))
        every { artistTable.findByIdDirect("UC_repair") } returns
            Artist(id = "UC_repair", name = null, thumbnailUrl = "https://kept/thumb")

        resolveVideoArtistFallback(videoId, "Some video", "Some video", "https://t")

        val payload = slot<Artist>()
        verify(exactly = 1) { artistTable.upsert(capture(payload)) }
        assertEquals("SomeChannel", payload.captured.name)
        // the repair keeps the rest of the row (copy semantics)
        assertEquals("https://kept/thumb", payload.captured.thumbnailUrl)
        verify(exactly = 0) { artistTable.insertIgnore(any<Artist>()) }
        verify(exactly = 1) { songArtistMapTable.insertIgnore(any<SongArtistMap>()) }
    }

    @Test
    fun existingNonBlankNameRowIsLeftAloneButTheLinkIsInserted() = runBlocking {
        val videoId = "vid_keep"
        stubSearch(listOf(videoItem(videoId, "Some video", "FetchedChannel", "UC_keep", "https://t")))
        every { artistTable.findByIdDirect("UC_keep") } returns
            Artist(id = "UC_keep", name = "StoredName", thumbnailUrl = null)

        resolveVideoArtistFallback(videoId, "Some video", "Some video", "https://t")

        // the stored name is authoritative: no create, no repair
        verify(exactly = 0) { artistTable.upsert(any<Artist>()) }
        verify(exactly = 0) { artistTable.insertIgnore(any<Artist>()) }
        verify(exactly = 1) { songArtistMapTable.insertIgnore(any<SongArtistMap>()) }
    }

    // --- no-write branches ---

    @Test
    fun noMatchWritesNothing() = runBlocking {
        val videoId = "vid_nomatch"
        stubSearch(listOf(videoItem("other_vid", "Different title", "SomeChannel", "UC_nom", "https://other")))

        resolveVideoArtistFallback(videoId, "Our title", "Our title", "https://ours")

        verify(exactly = 0) { artistTable.upsert(any<Artist>()) }
        verify(exactly = 0) { artistTable.insertIgnore(any<Artist>()) }
        verify(exactly = 0) { songArtistMapTable.insertIgnore(any<SongArtistMap>()) }
    }

    @Test
    fun searchWithoutResponseWritesNothing() = runBlocking {
        // null page = failed or cancelled search (runCatchingNonCancellable)
        coEvery {
            Innertube.searchPage<Innertube.VideoItem>(any(), any(), any())
        } returns null

        resolveVideoArtistFallback("vid_fail", "Title", "Title", "https://t")

        verify(exactly = 0) { artistTable.upsert(any<Artist>()) }
        verify(exactly = 0) { artistTable.insertIgnore(any<Artist>()) }
        verify(exactly = 0) { songArtistMapTable.insertIgnore(any<SongArtistMap>()) }
    }

    @Test
    fun blankTitlesSkipTheSearchEntirely() = runBlocking {
        coEvery {
            Innertube.searchPage<Innertube.VideoItem>(any(), any(), any())
        } returns null

        resolveVideoArtistFallback("vid_blank", "   ", "   ", null)

        coVerify(exactly = 0) { Innertube.searchPage<Innertube.VideoItem>(any(), any(), any()) }
        verify(exactly = 0) { songArtistMapTable.insertIgnore(any<SongArtistMap>()) }
    }

    @Test
    fun matchedChannelWithoutAUsableNameWritesNothing() = runBlocking {
        // A blank channel name must NOT create a nameless row: the empty-page
        // guard would keep the null name forever and the inserted link would
        // stop the fallback from ever re-trying for this video.
        val videoId = "vid_nullname"
        stubSearch(listOf(videoItem(videoId, "Some video", null, "UC_nullname", "https://t")))
        every { artistTable.findByIdDirect("UC_nullname") } returns null

        resolveVideoArtistFallback(videoId, "Some video", "Some video", "https://t")

        verify(exactly = 0) { artistTable.upsert(any<Artist>()) }
        verify(exactly = 0) { artistTable.insertIgnore(any<Artist>()) }
        verify(exactly = 0) { songArtistMapTable.insertIgnore(any<SongArtistMap>()) }
    }
}
