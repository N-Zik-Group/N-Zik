package app.n_zik.android.legacyoffmain.info

import app.n_zik.android.models.Artist
import app.n_zik.android.models.SongArtistMap
import app.n_zik.android.ui.screens.info.loadVideoOrSongInfo
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongArtistMapTable
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.VideoOrSongInfo
import app.n_zik.innertube.requests.searchPage
import app.n_zik.innertube.requests.songInfo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Issue #606 M12 (`Innertube.songInfo`, `Innertube.searchPage`) + M13
 * (`Database.artistTable.findBySongId`/`findByName` in a loop) -- `VideoOrSongInfoScreen.kt`'s
 * `LaunchedEffect(videoId)` used to run this entire sequential network+DB block inline, on
 * whatever dispatcher that effect resumes on (Main). It is now extracted, unchanged, to
 * [loadVideoOrSongInfo] (declared `internal` in `VideoOrSongInfoScreen.kt`,
 * `app.n_zik.android.*`, legacy) and dispatched in a single `withContext(NzikDispatchers.DATA)`
 * call.
 *
 * This test file itself lives under `app.n_zik.android.*` -- not the legacy package -- per the
 * gh-606 Lot 2 spec's "no new file under app.n_zik.android.*" boundary; `internal` visibility
 * is module-scoped in Kotlin, not package-scoped, so it still resolves [loadVideoOrSongInfo] from
 * here without needing to share its package.
 *
 * `Innertube.songInfo`/`Innertube.searchPage` (network) and `Database.artistTable`/
 * `songArtistMapTable` (Room DAOs) are all mocked out -- same technique
 * `ChangeCoverDialogSaveCoverArtOffMainTest` (Lot 1, H9) and `ShufflerTest` (Lot 1, M2) use for
 * their respective legacy/network/DB dependencies -- so this stays a fast, deterministic JVM unit
 * test that verifies [loadVideoOrSongInfo]'s own sequencing, not real network/DB behavior.
 */
class VideoOrSongInfoScreenLoadOffMainTest {

    private val artistTable = mockk<ArtistTable>()
    private val songArtistMapTable = mockk<SongArtistMapTable>()

    @BeforeEach
    fun setup() {
        mockkObject(Database)
        every { Database.artistTable } returns artistTable
        every { Database.songArtistMapTable } returns songArtistMapTable
        mockkStatic("app.n_zik.innertube.requests.SongInfoKt")
        mockkStatic("app.n_zik.innertube.requests.SearchPageKt")
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `loadVideoOrSongInfo uses the DB artists and does not search online when the DB already has artists for the song`() = runBlocking {
        val videoId = "v1"
        val info = VideoOrSongInfo(videoId = videoId, title = "Title", author = "API Author", authorId = "api-author-id")
        mockSongInfoSuccess(videoId, info)
        every { artistTable.findBySongId(videoId) } returns flowOf(listOf(Artist(id = "a1", name = "Artist One")))

        val result = loadVideoOrSongInfo(videoId, songArtist = "Ignored Artist")

        assertEquals(info, result.info)
        assertEquals(listOf("a1" to "Artist One"), result.finalArtists)
        verify(exactly = 0) { artistTable.findByName(any()) }
        coVerifyNoSearchPageCalls()
    }

    @Test
    fun `loadVideoOrSongInfo parses songArtist and saves a newly found online artist when the DB has none`() = runBlocking {
        val videoId = "v2"
        val info = VideoOrSongInfo(videoId = videoId, title = "Title")
        mockSongInfoSuccess(videoId, info)
        every { artistTable.findBySongId(videoId) } returns flowOf(emptyList())
        every { artistTable.findByName("Alice") } returns flowOf(null)
        every { artistTable.insertIgnore(any<Artist>()) } just Runs
        every { songArtistMapTable.insertIgnore(any<SongArtistMap>()) } just Runs

        val foundArtist = mockk<Innertube.ArtistItem>()
        every { foundArtist.key } returns "id-alice"
        every { foundArtist.info } returns null
        val itemsPage = Innertube.ItemsPage(items = listOf(foundArtist), continuation = null)
        coEvery {
            Innertube.searchPage<Innertube.ArtistItem>(
                query = "Alice",
                params = any(),
                fromMusicShelfRendererContent = any()
            )
        } returns resultAnswerFor(Result.success(itemsPage))

        val result = loadVideoOrSongInfo(videoId, songArtist = "Alice")

        assertEquals(listOf("id-alice" to "Alice"), result.finalArtists)
        verify(exactly = 1) { artistTable.insertIgnore(Artist(id = "id-alice", name = "Alice")) }
        verify(exactly = 1) { songArtistMapTable.insertIgnore(SongArtistMap(songId = videoId, artistId = "id-alice")) }
    }

    @Test
    fun `loadVideoOrSongInfo falls back to the API author when songArtist is blank and the DB has no artists`() = runBlocking {
        val videoId = "v3"
        val info = VideoOrSongInfo(videoId = videoId, title = "Title", author = "API Author", authorId = "api-author-id")
        mockSongInfoSuccess(videoId, info)
        every { artistTable.findBySongId(videoId) } returns flowOf(emptyList())

        val result = loadVideoOrSongInfo(videoId, songArtist = "")

        assertEquals(listOf("api-author-id" to "API Author"), result.finalArtists)
        verify(exactly = 0) { artistTable.findByName(any()) }
    }

    @Test
    fun `loadVideoOrSongInfo runs off the caller's thread when dispatched to NzikDispatchers DATA`() = runBlocking {
        val videoId = "v4"
        val callerThreadName = Thread.currentThread().name
        var songInfoThreadName: String? = null
        coEvery { Innertube.songInfo(videoId) } answers {
            songInfoThreadName = Thread.currentThread().name
            resultAnswerFor(Result.success(VideoOrSongInfo(videoId = videoId, title = "Title")))
        }
        every { artistTable.findBySongId(videoId) } returns flowOf(emptyList())

        withContext(NzikDispatchers.DATA) {
            loadVideoOrSongInfo(videoId, songArtist = "")
        }

        assertTrue(songInfoThreadName != null, "expected Innertube.songInfo to have been observed")
        assertNotEquals(
            "loadVideoOrSongInfo's network call must not run on the caller's (UI-simulating) thread once dispatched to DATA",
            callerThreadName,
            songInfoThreadName
        )
    }

    @Test
    fun `loadVideoOrSongInfo falls back to the API author when the DB artist lookup throws`() = runBlocking {
        val videoId = "v5"
        val info = VideoOrSongInfo(videoId = videoId, title = "Title", author = "API Author", authorId = "api-author-id")
        mockSongInfoSuccess(videoId, info)
        every { artistTable.findBySongId(videoId) } throws RuntimeException("DB unavailable")

        val result = loadVideoOrSongInfo(videoId, songArtist = "Some Artist")

        assertEquals(listOf("api-author-id" to "API Author"), result.finalArtists)
        verify(exactly = 0) { artistTable.findByName(any()) }
    }

    @Test
    fun `loadVideoOrSongInfo keeps a blank id when the online artist search throws for a parsed name`() = runBlocking {
        val videoId = "v6"
        val info = VideoOrSongInfo(videoId = videoId, title = "Title")
        mockSongInfoSuccess(videoId, info)
        every { artistTable.findBySongId(videoId) } returns flowOf(emptyList())
        every { artistTable.findByName("Bob") } returns flowOf(null)
        coEvery {
            Innertube.searchPage<Innertube.ArtistItem>(
                query = "Bob",
                params = any(),
                fromMusicShelfRendererContent = any()
            )
        } throws RuntimeException("network down")

        val result = loadVideoOrSongInfo(videoId, songArtist = "Bob")

        assertEquals(listOf("" to "Bob"), result.finalArtists)
        verify(exactly = 0) { artistTable.insertIgnore(any<Artist>()) }
    }

    @Test
    fun `loadVideoOrSongInfo deduplicates DB artists differing only by case, keeping the non-blank id`() = runBlocking {
        val videoId = "v7"
        val info = VideoOrSongInfo(videoId = videoId, title = "Title")
        mockSongInfoSuccess(videoId, info)
        every { artistTable.findBySongId(videoId) } returns flowOf(
            listOf(
                Artist(id = "", name = "Bob"),
                Artist(id = "artist2", name = "bob"),
            )
        )

        val result = loadVideoOrSongInfo(videoId, songArtist = "")

        assertEquals(listOf("artist2" to "bob"), result.finalArtists)
    }

    private fun coVerifyNoSearchPageCalls() {
        io.mockk.coVerify(exactly = 0) {
            Innertube.searchPage<Innertube.ArtistItem>(query = any(), params = any(), fromMusicShelfRendererContent = any())
        }
    }

    /**
     * MockK's suspend-mocking support special-cases any `returns`/`answers` value that is itself a
     * `kotlin.Result` instance: it unwraps that one level and resumes the mocked call's
     * continuation with the unwrapped payload -- sugar meant for expressing "make this suspend call
     * fail" via `returns Result.failure(x)` instead of `throws x`. That collides with
     * `Innertube.songInfo`/`Innertube.searchPage`, whose own *declared* return type is itself
     * `Result<T>?` -- MockK's unwrap leaves the continuation resumed with the raw payload instead
     * of a `Result`, and the real call site's `CHECKCAST Result` (its return type is compiled to
     * `Object`, like any suspend function) then throws `ClassCastException`. Wrapping the intended
     * value in one extra `Result.success(...)` layer absorbs MockK's single unwrap, so the
     * continuation is resumed with a genuine `Result` instance, matching what the call site expects.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> resultAnswerFor(value: Result<T>): Result<T> =
        Result.success(value) as Result<T>

    private fun mockSongInfoSuccess(videoId: String, info: VideoOrSongInfo) {
        coEvery { Innertube.songInfo(videoId) } returns resultAnswerFor(Result.success(info))
    }
}
