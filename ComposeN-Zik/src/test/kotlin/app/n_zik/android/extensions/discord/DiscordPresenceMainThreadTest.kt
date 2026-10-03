package app.n_zik.android.extensions.discord

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import com.metrolist.music.discordrpc.DiscordRpcConnection
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Collections

/**
 * gh-881 (Phase 3, Fix D): the live player-state providers passed to
 * [DiscordPresenceManager.onPlayingStateChanged] read ExoPlayer state, and ExoPlayer only
 * permits that on the main thread (`verifyApplicationThread` — an off-main read throws
 * `IllegalStateException`, the field crash this test reproduces: the 5 s refresh tick read
 * `player.isPlaying` from a `DefaultDispatcher-worker`). The manager's loops run on the
 * DATA (IO) scope, so the manager must hop to the main thread around EVERY provider call.
 *
 * The manager scope here is the REAL `Dispatchers.IO` pool (like production); "main" is a
 * test dispatcher drained by the test thread (simulating the main looper). The providers
 * record the thread they run on — every recorded thread must be the main (test) thread,
 * never an IO worker.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscordPresenceMainThreadTest {

    private val managers = mutableListOf<DiscordPresenceManager>()
    private val ioScopes = mutableListOf<CoroutineScope>()

    private val providerThreads = Collections.synchronizedList(mutableListOf<Thread>())

    @AfterEach
    fun tearDown() {
        managers.forEach { it.onStop() }
        managers.clear()
        ioScopes.forEach { it.cancel() }
        ioScopes.clear()
        providerThreads.clear()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `live providers are always invoked on the main thread (refresh tick and speed re-send)`() {
        val mainScheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(mainScheduler))

        val ioScope = CoroutineScope(Dispatchers.IO) // real workers, like production
        ioScopes += ioScope

        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkAvailable(any()) } returns true

        val manager = DiscordPresenceManager(
            context = discordTestContext(),
            getToken = { "test-token" },
            getAdvancedSettings = { DiscordAdvancedSettings.DEFAULTS },
            externalScope = ioScope,
            connectionFactory = {
                val connection = mockk<DiscordRpcConnection>(relaxed = true)
                every { connection.reconnectAbandoned } returns MutableStateFlow(false)
                every { connection.terminalCloseCode } returns MutableStateFlow(null)
                connection
            },
            tokenValidator = { true },
            refreshIntervalMs = 10L,
        )
        managers += manager

        try {
            manager.onPlayingStateChanged(
                mediaItem = mediaItem(),
                isPlaying = true,
                position = 10_000L,
                duration = 100_000L,
                getCurrentPosition = {
                    providerThreads += Thread.currentThread()
                    42_000L
                },
                isPlayingProvider = {
                    providerThreads += Thread.currentThread()
                    true
                },
            )

            // Drain the main "looper" until a full tick has run (one tick calls BOTH
            // providers). The tick body itself runs on an IO worker.
            drainUntil(mainScheduler, calls = 2)
            assertTrue(
                providerThreads.size >= 2,
                "expected at least one full refresh tick (isPlaying + position) within ${DRAIN_BUDGET_MS} ms, got ${providerThreads.size} provider calls",
            )

            // The speed-change re-send must hop to main as well (its production caller
            // runs on the DATA scope).
            val callsBeforeSpeedChange = providerThreads.size
            manager.onPlaybackSpeedChanged(1.5f)
            drainUntil(mainScheduler, calls = callsBeforeSpeedChange + 1)
            assertTrue(
                providerThreads.size > callsBeforeSpeedChange,
                "expected the speed re-send to read the live position within ${DRAIN_BUDGET_MS} ms, got no new provider call",
            )

            assertTrue(
                providerThreads.all { it == Thread.currentThread() },
                "live providers must run on the main thread, got: ${providerThreads.distinct().map { it.name }}",
            )
        } finally {
            manager.onStop()
        }
    }

    /**
     * Runs pending main-thread tasks until at least [calls] provider calls have been
     * recorded (or the deadline), simulating the main looper picking up the tasks the
     * IO-side loops hop through.
     *
     * Review patch: the previous fixed 5 s wall-clock deadline flaked on loaded CI
     * runners (the tick runs on the real IO pool at a 10 ms interval and can starve
     * under load). The budget is now generous per drain and the assertion messages
     * report expected vs. recorded calls, so a genuine hang is distinguishable from a
     * slow runner.
     */
    private fun drainUntil(mainScheduler: TestCoroutineScheduler, calls: Int) {
        val deadline = System.currentTimeMillis() + DRAIN_BUDGET_MS
        while (providerThreads.size < calls && System.currentTimeMillis() < deadline) {
            mainScheduler.runCurrent()
            Thread.sleep(5)
        }
    }

    companion object {
        /** Wall-clock budget per drain — generous enough for a loaded CI runner. */
        private const val DRAIN_BUDGET_MS = 15_000L
    }

    private fun mediaItem(
        id: String = "dQw4w9WgXcQ",
        title: String = "Song",
        artist: String = "Artist",
        album: String = "Album",
    ): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()
}
