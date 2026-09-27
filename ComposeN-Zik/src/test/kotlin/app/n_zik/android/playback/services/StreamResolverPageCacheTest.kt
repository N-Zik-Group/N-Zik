package app.n_zik.android.playback.services

import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.n_zik.android.core.database.AlbumTable
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.database.SongAlbumMapTable
import app.n_zik.android.core.database.SongArtistMapTable
import app.n_zik.android.core.database.SongTable
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.Thumbnail
import it.fast4x.innertube.requests.AlbumPage
import it.fast4x.innertube.requests.artistPage
import it.fast4x.innertube.requests.nextPage
import it.fast4x.innertube.requests.searchPage
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executor

/**
 * Boundary tests for the artist/album page-cache upserts of [upsertSongInfo]
 * (the `mergePageValue` call sites): an EMPTY fetched page (name/title null,
 * thumbnail null, authors empty) must keep the stored values while still
 * stamping `lastFetch`, and a NON-EMPTY page must still overwrite an
 * unmodified stored row.
 *
 * Harness follows [RunSongUpdateTest]: [Database] is object-mocked with
 * stubbed tables, the lazy Room initializer is kept out of the JVM, and
 * [Database.asyncTransaction] runs its block inline on a stubbed transaction
 * executor. The page fetches run fire-and-forget on the shared [scope]
 * (PLAYBACK dispatcher), so the upsert payloads are awaited with
 * `verify(timeout = ...)` (same spirit as the latch usage in
 * [StreamResolverScopeTest]). Distinct videoIds per test stay clear of the
 * file-private `justInserted` / `fetchingSongInfos` dedup state of
 * StreamResolver.
 */
class StreamResolverPageCacheTest {

    private val songArtistMapTable = mockk<SongArtistMapTable>(relaxed = true)
    private val songAlbumMapTable = mockk<SongAlbumMapTable>(relaxed = true)
    private val artistTable = mockk<ArtistTable>(relaxed = true)
    private val albumTable = mockk<AlbumTable>(relaxed = true)
    private val songTable = mockk<SongTable>(relaxed = true)
    private val internal = mockk<DatabaseInitializer>(relaxed = true)

    @Before
    fun setup() {
        mockkObject(Database)
        coEvery { Database.upsert(any<Innertube.SongItem>()) } just runs
        every { Database.songArtistMapTable } returns songArtistMapTable
        every { Database.songAlbumMapTable } returns songAlbumMapTable
        every { Database.artistTable } returns artistTable
        every { Database.albumTable } returns albumTable
        every { Database.songTable } returns songTable

        // asyncTransaction runs its block on the Room transactionExecutor:
        // keep the real (lazy) Room database out of the JVM and run the block
        // inline on the calling thread.
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns internal
        every { internal.transactionExecutor } returns Executor { it.run() }

        // Innertube.nextPage / artistPage are top-level suspend extensions
        // (requests/NextPage.kt, requests/ArtistInfoPage.kt); YtMusic.getAlbum
        // is a member of the YtMusic object.
        mockkStatic("it.fast4x.innertube.requests.NextPageKt")
        mockkStatic("it.fast4x.innertube.requests.ArtistInfoPageKt")
        mockkStatic("it.fast4x.innertube.requests.SearchPageKt")
        mockkObject(YtMusic)

        // The video-artist fallback (triggered on the first [IDs] attempt when
        // the queue item has no authors) must stay hermetic: the video search
        // returns nothing (no match, no write) so the tests above keep only
        // exercising the artist/album cache paths.
        coEvery { Innertube.searchPage<Innertube.VideoItem>(any(), any(), any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * MockK 1.14.11's suspend-mocking support unwraps any `returns` value
     * that is itself a `kotlin.Result` one level — but ONLY for functions
     * with a NULLABLE `Result<X>?`-typed return (verified empirically: a
     * non-nullable-Result return delivers the `returns` value as-is, so it
     * must stay single-wrapped). `nextPage` and `artistPage` return
     * `Result<X>?`, so their intended `Result.success(x)` value is wrapped
     * in one extra `Result.success(...)` layer to absorb that single
     * unwrap (same trick as RunSongUpdateTest).
     */
    @Suppress("UNCHECKED_CAST")
    private fun nextPageResult(value: Result<Innertube.NextPage>): Result<Innertube.NextPage>? =
        Result.success(value) as Result<Innertube.NextPage>?

    @Suppress("UNCHECKED_CAST")
    private fun artistPageResult(value: Result<Innertube.ArtistInfoPage>): Result<Innertube.ArtistInfoPage>? =
        Result.success(value) as Result<Innertube.ArtistInfoPage>?

    /**
     * `YtMusic.getAlbum` returns a NON-nullable `Result<AlbumPage>`, so the
     * intended `Result.success(page)` is delivered as-is: single wrap.
     */
    private fun getAlbumResult(page: AlbumPage): Result<AlbumPage> =
        Result.success(page)

    /** A watch-next response whose single item points at [videoId]. */
    private fun stubNextPage(videoId: String) {
        val nextPage = Innertube.NextPage(
            itemsPage = Innertube.ItemsPage(
                items = listOf(songItem(videoId, "Some track")),
                continuation = null
            ),
            playlistId = null
        )
        coEvery { Innertube.nextPage(videoId = videoId) } returns nextPageResult(Result.success(nextPage))
    }

    private fun songItem(videoId: String, title: String) =
        Innertube.SongItem(
            info = Innertube.Info(
                name = title,
                endpoint = NavigationEndpoint.Endpoint.Watch(videoId = videoId)
            ),
            authors = emptyList(),
            album = null,
            durationText = null,
            thumbnail = null
        )

    private fun artistPage(name: String?, thumbnailUrl: String?) =
        Innertube.ArtistInfoPage(
            name = name,
            description = null,
            subscriberCountText = null,
            thumbnail = thumbnailUrl?.let { Thumbnail(url = it, height = null, width = null) },
            shuffleEndpoint = null,
            radioEndpoint = null,
            songs = null,
            songsEndpoint = null,
            albums = null,
            albumsEndpoint = null,
            singles = null,
            singlesEndpoint = null,
            playlists = null
        )

    private fun albumPage(
        albumId: String,
        title: String?,
        thumbnailUrl: String?,
        authorNames: List<String>,
        year: String?,
        trackVideoIds: List<String>
    ): AlbumPage = AlbumPage(
        album = Innertube.AlbumItem(
            info = Innertube.Info(
                name = title,
                endpoint = NavigationEndpoint.Endpoint.Browse(browseId = albumId)
            ),
            authors = authorNames.map {
                Innertube.Info(
                    name = it,
                    endpoint = NavigationEndpoint.Endpoint.Browse(browseId = "UC_$it")
                )
            },
            year = year,
            thumbnail = thumbnailUrl?.let { Thumbnail(url = it, height = null, width = null) }
        ),
        songs = trackVideoIds.map { songItem(it, "Track $it") },
        otherVersions = emptyList(),
        url = "https://music.youtube.com/playlist?list=PL_$albumId",
        description = null
    )

    @Test
    fun emptyArtistPageKeepsTheStoredNameAndThumbnail() = runBlocking {
        // Device scenario 2026-09-27 (video 5_rs12ThNLc): classic channel
        // without a music header -> artistPage name + thumbnail null.
        val videoId = "vid_empty_artist"
        val artistId = "UCa7ao1c-NUd8zB9kHlIErOw"
        val stored = Artist(
            id = artistId,
            name = "Strike44",
            thumbnailUrl = "https://stored/thumb",
            lastFetch = null // never fetched -> the cache fetch is triggered
        )
        stubNextPage(videoId)
        every { songArtistMapTable.findArtistsOf(videoId) } returns flowOf(listOf(stored))
        every { songAlbumMapTable.findAlbumOf(videoId) } returns flowOf(null)
        every { artistTable.findByIdDirect(artistId) } returns stored
        coEvery { Innertube.artistPage(browseId = artistId) } returns
            artistPageResult(Result.success(artistPage(name = null, thumbnailUrl = null)))

        upsertSongInfo(videoId)

        val payload = slot<Artist>()
        verify(timeout = 15_000) { artistTable.upsert(capture(payload)) }
        // The empty page keeps the stored name + thumbnail...
        assertEquals("Strike44", payload.captured.name)
        assertEquals("https://stored/thumb", payload.captured.thumbnailUrl)
        // ...and still stamps lastFetch (the TTL must not re-fetch forever).
        assertNotNull("lastFetch must be stamped even on an empty page (TTL anti-spam)", payload.captured.lastFetch)
        // The video already has an artist link: the video-artist fallback
        // must not run a search for it (no network spam on an already-linked
        // video — the `searchPage -> null` stub in setup() must stay unused).
        coVerify(exactly = 0) { Innertube.searchPage<Innertube.VideoItem>(any(), any(), any()) }
    }

    @Test
    fun nonEmptyArtistPageOverwritesTheUnmodifiedStoredRow() = runBlocking {
        val videoId = "vid_full_artist"
        val artistId = "UC_full_artist"
        val stored = Artist(
            id = artistId,
            name = "tanger",
            thumbnailUrl = "https://stored/thumb",
            lastFetch = null
        )
        stubNextPage(videoId)
        every { songArtistMapTable.findArtistsOf(videoId) } returns flowOf(listOf(stored))
        every { songAlbumMapTable.findAlbumOf(videoId) } returns flowOf(null)
        every { artistTable.findByIdDirect(artistId) } returns stored
        coEvery { Innertube.artistPage(browseId = artistId) } returns
            artistPageResult(Result.success(artistPage(name = "Tanger", thumbnailUrl = "https://fetched/thumb")))

        upsertSongInfo(videoId)

        val payload = slot<Artist>()
        verify(timeout = 15_000) { artistTable.upsert(capture(payload)) }
        // A non-blank fetch still wins over an unmodified stored value.
        assertEquals("Tanger", payload.captured.name)
        assertEquals("https://fetched/thumb", payload.captured.thumbnailUrl)
        assertNotNull(payload.captured.lastFetch)
    }

    @Test
    fun emptyAlbumPageKeepsTheStoredAlbumFields() = runBlocking {
        val videoId = "vid_empty_album"
        val albumId = "ALB_empty"
        val stored = Album(
            id = albumId,
            title = "Stored title",
            thumbnailUrl = "https://stored/thumb",
            authorsText = "Stored artists",
            year = "2020",
            lastFetch = null
        )
        stubNextPage(videoId)
        every { songArtistMapTable.findArtistsOf(videoId) } returns flowOf(emptyList())
        every { songAlbumMapTable.findAlbumOf(videoId) } returns flowOf(stored)
        every { albumTable.findByIdDirect(albumId) } returns stored
        // Empty album page: no title, no thumbnail, no authors, no year —
        // but a non-empty track list (a zero-song page is a no-op, not an
        // empty page, and never reaches the upsert).
        coEvery { YtMusic.getAlbum(albumId, true) } returns getAlbumResult(
            albumPage(
                albumId = albumId,
                title = null,
                thumbnailUrl = null,
                authorNames = emptyList(),
                year = null,
                trackVideoIds = listOf("trk_ea1", "trk_ea2")
            )
        )

        upsertSongInfo(videoId)

        val payload = slot<Album>()
        verify(timeout = 15_000) { albumTable.upsert(capture(payload)) }
        // Await the rest of the transaction block (map clear + upsert) so no
        // mock call lands after the teardown.
        verify(timeout = 15_000) { songAlbumMapTable.upsert(any()) }
        assertEquals("Stored title", payload.captured.title)
        assertEquals("https://stored/thumb", payload.captured.thumbnailUrl)
        assertEquals("Stored artists", payload.captured.authorsText)
        assertEquals("2020", payload.captured.year)
        assertNotNull("lastFetch must be stamped even on an empty page (TTL anti-spam)", payload.captured.lastFetch)
    }

    @Test
    fun nonEmptyAlbumPageOverwritesTheUnmodifiedStoredFields() = runBlocking {
        val videoId = "vid_full_album"
        val albumId = "ALB_full"
        val stored = Album(
            id = albumId,
            title = "Stored title",
            thumbnailUrl = "https://stored/thumb",
            authorsText = "Stored artists",
            year = "2020",
            lastFetch = null
        )
        stubNextPage(videoId)
        every { songArtistMapTable.findArtistsOf(videoId) } returns flowOf(emptyList())
        every { songAlbumMapTable.findAlbumOf(videoId) } returns flowOf(stored)
        every { albumTable.findByIdDirect(albumId) } returns stored
        coEvery { YtMusic.getAlbum(albumId, true) } returns getAlbumResult(
            albumPage(
                albumId = albumId,
                title = "Fetched title",
                thumbnailUrl = "https://fetched/thumb",
                authorNames = listOf("Fetched artist"),
                year = "2021",
                trackVideoIds = listOf("trk_fa1")
            )
        )

        upsertSongInfo(videoId)

        val payload = slot<Album>()
        verify(timeout = 15_000) { albumTable.upsert(capture(payload)) }
        verify(timeout = 15_000) { songAlbumMapTable.upsert(any()) }
        // A non-blank page still overwrites an unmodified stored row.
        assertEquals("Fetched title", payload.captured.title)
        assertEquals("https://fetched/thumb", payload.captured.thumbnailUrl)
        assertEquals("Fetched artist", payload.captured.authorsText)
        assertEquals("2021", payload.captured.year)
        assertNotNull(payload.captured.lastFetch)
    }
}
