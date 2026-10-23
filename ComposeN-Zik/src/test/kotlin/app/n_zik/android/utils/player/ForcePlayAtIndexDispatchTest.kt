package app.n_zik.android.utils.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.it.fast4x.rimusic.utils.forcePlayAtIndex
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.playback.services.upsertSongInfo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Issue #606 (VG-1) -- pins the dispatch contract of `Player.forcePlayAtIndex`
 * (`app.it.fast4x.rimusic.utils.Player.kt`) after the G4 migration: the queue conversion +
 * metadata pre-fetch body must run on `NzikDispatchers.MEDIA` (the `nzik-media` pool, formerly
 * `Dispatchers.Default`), while the playback start (`setMediaItems`/`prepare`) must run on
 * `NzikDispatchers.UI` (Main). A regression toward `Dispatchers.Main` would be caught by
 * assertion (1) (the `nzik-media-` prefix check); assertion (3) is the separate guard that the
 * body never runs on the caller's thread itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ForcePlayAtIndexDispatchTest {

    private lateinit var fakeMainExecutor: ExecutorService

    @BeforeEach
    fun setup() {
        // Named single-thread executor standing in for the real Main dispatcher (same technique
        // as ShufflerTest.kt's Dispatchers.setMain), so the test can prove the playback start

        // lands on Main without driving a real Looper.
        fakeMainExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "fake-main") }
        Dispatchers.setMain(fakeMainExecutor.asCoroutineDispatcher())
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
        fakeMainExecutor.shutdownNow()
        unmockkAll()
    }

    private fun mediaItem(id: String) = MediaItem.Builder().setMediaId(id).build()

    @Test
    fun `forcePlayAtIndex converts the queue on MEDIA and starts playback on Main`() {
        val player = mockk<Player>(relaxed = true)
        val items = listOf(mediaItem("a"), mediaItem("b"))

        var startThread: String? = null
        every { player.setMediaItems(any(), any(), any()) } answers {
            startThread = Thread.currentThread().name
        }

        // upsertSongInfo is called from inside the body, after both the queue conversion and the
        // Main-dispatched playback start, so its caller thread is the join point for the body.
        mockkStatic("app.n_zik.android.playback.services.StreamResolverKt")
        var bodyThread: String? = null
        coEvery { upsertSongInfo(any()) } answers {
            bodyThread = Thread.currentThread().name
        }

        // The pre-fetch DB read goes through the mocked SongTable (findByIdDirect returns null),
        // so the enrichment branch stays out of the test without touching Room.
        mockkObject(Database)
        every { Database.songTable } returns mockk<SongTable>(relaxed = true)

        val callerThreadName = Thread.currentThread().name
        player.forcePlayAtIndex(items, 0)

        // The body is fire-and-forget; poll until it reaches its metadata pre-fetch.
        val deadline = System.currentTimeMillis() + 10_000
        while (bodyThread == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }

        // The coroutines runtime appends an "@coroutine#N" debug suffix to the thread name while
        // a coroutine is active, so match on the prefix, not the full name.
        val body = bodyThread ?: fail("forcePlayAtIndex body never reached its metadata pre-fetch")
        assertTrue(
            body.startsWith("nzik-media-"),
            "queue conversion + pre-fetch must run on NzikDispatchers.MEDIA, ran on: $body"
        )
        assertTrue(
            startThread?.startsWith("fake-main") == true,
            "playback start (setMediaItems) must run on Main (NzikDispatchers.UI), ran on: $startThread"
        )
        assertNotEquals(
            "forcePlayAtIndex body must not run on the caller's thread",
            callerThreadName,
            body
        )
    }
}
