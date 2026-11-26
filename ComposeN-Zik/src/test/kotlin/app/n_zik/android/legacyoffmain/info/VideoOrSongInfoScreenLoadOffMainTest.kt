package app.n_zik.android.legacyoffmain.info

import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongArtistMapTable
import app.n_zik.android.models.Artist
import app.n_zik.android.models.SongArtistMap
import app.n_zik.android.ui.screens.info.loadVideoOrSongInfo
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.VideoOrSongInfo
import app.n_zik.innertube.requests.searchPage
import app.n_zik.innertube.requests.songInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
    fun `information keeps exact stored credits and retains names without links`() = runBlocking {
        val videoId = "track-1"
        val artistId = "modified:UC1234567890"
        val info = VideoOrSongInfo(videoId = videoId, title = "Title", author = "Uploader")
        mockSongInfoSuccess(videoId, info)
        coEvery { artistTable.findBySongIdDirect(videoId) } returns
            listOf(Artist(id = artistId, name = "modified:Artist One"))

        val result = loadVideoOrSongInfo(videoId, songArtist = "modified:Artist One, Artist Two")

        assertEquals(info, result.info)
        assertEquals(listOf(artistId to "modified:Artist One", "" to "Artist Two"), result.finalArtists)
        verifyNoArtistGuess()
    }

    @Test
    fun `information leaves an unlinked credit available without searching or saving an artist`() = runBlocking {
        val videoId = "track-2"
        mockSongInfoSuccess(videoId, VideoOrSongInfo(videoId = videoId, title = "Title"))
        coEvery { artistTable.findBySongIdDirect(videoId) } returns emptyList()

        val result = loadVideoOrSongInfo(videoId, songArtist = "Alice, alice")

        assertEquals(listOf("" to "Alice"), result.finalArtists)
        verifyNoArtistGuess()
    }

    @Test
    fun `information keeps the uploader name available when the track has no artist credits`() = runBlocking {
        val videoId = "track-3"
        val info = VideoOrSongInfo(videoId = videoId, title = "Title", author = "Uploader", authorId = "UC1234567890")
        mockSongInfoSuccess(videoId, info)
        coEvery { artistTable.findBySongIdDirect(videoId) } returns emptyList()

        val result = loadVideoOrSongInfo(videoId, songArtist = "")

        assertEquals(listOf("" to "Uploader"), result.finalArtists)
        verifyNoArtistGuess()
    }

    @Test
    fun `information keeps the track credit when stored artist lookup fails`() = runBlocking {
        val videoId = "track-4"
        val info = VideoOrSongInfo(videoId = videoId, title = "Title", author = "Uploader")
        mockSongInfoSuccess(videoId, info)
        coEvery { artistTable.findBySongIdDirect(videoId) } throws IllegalStateException("DB unavailable")

        val result = loadVideoOrSongInfo(videoId, songArtist = "Artist One")

        assertEquals(info, result.info)
        assertEquals(listOf("" to "Artist One"), result.finalArtists)
        verifyNoArtistGuess()
    }

    @Test
    fun `information never requests online stats for a device track`() = runBlocking {
        val videoId = "local:track-5"
        coEvery { artistTable.findBySongIdDirect(videoId) } returns emptyList()

        val result = loadVideoOrSongInfo(videoId, songArtist = "Device Artist")

        assertNull(result.info)
        assertEquals(listOf("" to "Device Artist"), result.finalArtists)
        coVerify(exactly = 0) { Innertube.songInfo(any()) }
    }

    @Test
    fun `information runs network and database reads off the caller thread on DATA`() = runBlocking {
        val videoId = "track-6"
        val callerThread = Thread.currentThread()
        var networkThread: Thread? = null
        var databaseThread: Thread? = null
        coEvery { Innertube.songInfo(videoId) } answers {
            networkThread = Thread.currentThread()
            resultAnswerFor(Result.success(VideoOrSongInfo(videoId = videoId)))
        }
        coEvery { artistTable.findBySongIdDirect(videoId) } coAnswers {
            databaseThread = Thread.currentThread()
            emptyList()
        }

        withContext(NzikDispatchers.DATA) { loadVideoOrSongInfo(videoId, songArtist = "") }

        assertTrue(networkThread != null)
        assertTrue(databaseThread != null)
        assertNotEquals(callerThread, networkThread)
        assertNotEquals(callerThread, databaseThread)
    }

    private fun verifyNoArtistGuess() {
        verify(exactly = 0) { artistTable.findByName(any()) }
        coVerify(exactly = 0) {
            Innertube.searchPage<Innertube.ArtistItem>(
                query = any(),
                params = any(),
                fromMusicShelfRendererContent = any(),
            )
        }
        coVerify(exactly = 0) { artistTable.insertIgnore(any<Artist>()) }
        coVerify(exactly = 0) { songArtistMapTable.insertIgnore(any<SongArtistMap>()) }
    }

    /** MockK unwraps one Result layer from a suspend answer. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> resultAnswerFor(value: Result<T>): Result<T> = Result.success(value) as Result<T>

    private fun mockSongInfoSuccess(videoId: String, info: VideoOrSongInfo) {
        coEvery { Innertube.songInfo(videoId) } returns resultAnswerFor(Result.success(info))
    }
}
