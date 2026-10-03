package app.n_zik.android.extensions.discord

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import com.metrolist.music.discordrpc.DiscordRpcConnection
import io.mockk.Runs
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Item 9: the durable Discord RPC error state — the gateway's terminal close code (4004 =
 * invalid token) and the exhausted reconnection budget are surfaced through
 * [DiscordRpcErrorState] for the settings banner, and a fresh session (new token / re-created
 * connection) clears it. The state is process-wide, so it is reset after every test.
 */
class DiscordRpcErrorStateTest {

    private val managers = mutableListOf<DiscordPresenceManager>()

    @AfterEach
    fun tearDown() {
        managers.forEach { it.onStop() }
        managers.clear()
        DiscordRpcErrorState.clear()
        unmockkAll()
        DiscordUiState.currentRoute.value = null
    }

    private fun newConnection(
        reconnectAbandoned: MutableStateFlow<Boolean> = MutableStateFlow(false),
        terminalCloseCode: MutableStateFlow<Int?> = MutableStateFlow(null),
    ): DiscordRpcConnection {
        val connection = mockk<DiscordRpcConnection>(relaxed = true)
        // Real flows: the manager's watch jobs collect them (relaxed property access would
        // return a mock StateFlow, which the collect would not observe).
        every { connection.reconnectAbandoned } returns reconnectAbandoned
        every { connection.terminalCloseCode } returns terminalCloseCode
        return connection
    }

    private fun newManager(
        dispatcher: TestDispatcher,
        connection: DiscordRpcConnection,
        token: () -> String = { "test-token" },
    ): DiscordPresenceManager {
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkAvailable(any()) } returns true
        val manager = DiscordPresenceManager(
            context = mockk<Context>(relaxed = true),
            getToken = token,
            getAdvancedSettings = { DiscordAdvancedSettings.DEFAULTS },
            externalScope = CoroutineScope(dispatcher),
            connectionFactory = { connection },
            tokenValidator = { true },
            playerStateDispatcher = dispatcher
        )
        managers += manager
        return manager
    }

    private fun mediaItem() = MediaItem.Builder()
        .setMediaId("dQw4w9WgXcQ")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("Song")
                .setArtist("Artist")
                .setAlbumTitle("Album")
                .build()
        )
        .build()

    @Test
    fun `gateway 4004 surfaces the durable INVALID_TOKEN error`() = runTest {
        val terminal = MutableStateFlow<Int?>(null)
        val connection = newConnection(terminalCloseCode = terminal)
        val manager = newManager(UnconfinedTestDispatcher(testScheduler), connection)
        try {
            // The connection (and its terminal-close watch) is created by the write path.
            manager.onPlayingStateChanged(mediaItem(), isPlaying = true, position = 10_000, duration = 100_000)
            advanceTimeBy(5_001)
            assertNull(DiscordRpcErrorState.error.value, "no error while the session is healthy")

            terminal.value = 4004
            testScheduler.runCurrent() // the watch resumes on the test scheduler

            assertEquals(DiscordRpcError.INVALID_TOKEN, DiscordRpcErrorState.error.value)
            // Item 10: the write path that created the connection emptied the artwork cache.
            coVerify(exactly = 1) { connection.clearArtworkCache() }
        } finally {
            manager.onStop()
        }
    }

    @Test
    fun `reconnect abandonment surfaces the durable RECONNECT_FAILED error and the toast`() = runTest {
        // The watch re-dispatches to the UI dispatcher to show the toast.
        val abandoned = MutableStateFlow(false)
        val connection = newConnection(reconnectAbandoned = abandoned)
        val manager = newManager(UnconfinedTestDispatcher(testScheduler), connection)
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            mockkObject(Toaster)
            every { Toaster.e(any<Int>()) } just Runs

            manager.onPlayingStateChanged(mediaItem(), isPlaying = true, position = 10_000, duration = 100_000)
            advanceTimeBy(5_001)

            abandoned.value = true
            testScheduler.runCurrent() // the watch (and the UI-dispatched toast) run on the scheduler

            assertEquals(DiscordRpcError.RECONNECT_FAILED, DiscordRpcErrorState.error.value)
            verify(exactly = 1) { Toaster.e(R.string.discord_rpc_reconnect_failed) }
        } finally {
            manager.onStop()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `a new token clears the durable error and re-sends the presence on the fresh connection`() = runTest {
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkAvailable(any()) } returns true

        val terminalFirst = MutableStateFlow<Int?>(null)
        val first = newConnection(terminalCloseCode = terminalFirst)
        val second = newConnection()
        var currentConnection: DiscordRpcConnection = first
        var token = "token-1"

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val manager = DiscordPresenceManager(
            context = mockk<Context>(relaxed = true),
            getToken = { token },
            getAdvancedSettings = { DiscordAdvancedSettings.DEFAULTS },
            externalScope = CoroutineScope(dispatcher),
            connectionFactory = { currentConnection },
            tokenValidator = { true },
            playerStateDispatcher = dispatcher
        )
        managers += manager
        try {
            manager.onPlayingStateChanged(
                mediaItem(),
                isPlaying = true,
                position = 10_000,
                duration = 100_000,
                getCurrentPosition = { 10_000 },
                isPlayingProvider = { true },
            )
            advanceTimeBy(5_001)

            // The gateway says the token is invalid → durable error for the banner.
            terminalFirst.value = 4004
            testScheduler.runCurrent() // the watch resumes on the test scheduler
            assertEquals(DiscordRpcError.INVALID_TOKEN, DiscordRpcErrorState.error.value)

            // The user re-authenticates: the next event re-creates the connection and clears the
            // durable error (the fresh connection's flow starts null — the module resets it in connect()).
            token = "token-2"
            currentConnection = second
            manager.onPlayingStateChanged(
                mediaItem(),
                isPlaying = true,
                position = 10_000,
                duration = 100_000,
                getCurrentPosition = { 10_000 },
                isPlayingProvider = { true },
            )
            advanceTimeBy(5_001) // the restarted refresh tick and the debounce both fire on the fresh connection

            assertNull(DiscordRpcErrorState.error.value, "the durable error must be cleared by the fresh session")
            // Item 10: every fresh connection empties the process-wide artwork cache.
            coVerify(exactly = 1) { first.clearArtworkCache() }
            coVerify(exactly = 1) { second.clearArtworkCache() }
            coVerify(exactly = 2) {
                second.setActivity(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
        } finally {
            manager.onStop()
        }
    }
}
