package app.n_zik.android.extensions.discord

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import com.metrolist.music.discordrpc.DiscordRpcConnection
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Item 7: two inactivity timers re-armed by every media event —
 * - 60 s of pause with no event → [DiscordRpcConnection.clearActivity] (connection kept, a
 *   resume is cheap) — only when the pause presence is DISABLED; with it enabled the
 *   paused presence is the active state and stays as long as the player is paused,
 * - 10 min with no event at all → [DiscordRpcConnection.closeDirect] (terminal in the
 *   module) — unless an active paused presence (enabled) is shown, in which case the
 *   timer re-arms to keep the connection open. The next event after a close must create
 *   a FRESH connection (same pattern as the token change) and re-send the presence on it.
 */
class DiscordInactivityTimersTest {

    private val managers = mutableListOf<DiscordPresenceManager>()
    private val createdConnections = mutableListOf<DiscordRpcConnection>()

    @AfterEach
    fun tearDown() {
        managers.forEach { it.onStop() }
        managers.clear()
        createdConnections.clear()
        unmockkAll()
        DiscordUiState.currentRoute.value = null
    }

    private fun newManager(
        dispatcher: TestDispatcher,
        settings: DiscordAdvancedSettings = DiscordAdvancedSettings.DEFAULTS,
        networkAvailable: () -> Boolean = { true },
    ): DiscordPresenceManager {
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkAvailable(any()) } answers { networkAvailable() }
        val manager = DiscordPresenceManager(
            context = discordTestContext(),
            getToken = { "test-token" },
            getAdvancedSettings = { settings },
            externalScope = CoroutineScope(dispatcher),
            connectionFactory = {
                val connection = mockk<DiscordRpcConnection>(relaxed = true)
                // The manager watches both gateway signals on every connection it creates.
                every { connection.reconnectAbandoned } returns MutableStateFlow(false)
                every { connection.terminalCloseCode } returns MutableStateFlow(null)
                createdConnections += connection
                connection
            },
            tokenValidator = { true },
            playerStateDispatcher = dispatcher
        )
        managers += manager
        return manager
    }

    private fun mediaItem(
        id: String = "dQw4w9WgXcQ",
        title: String = "Song",
        artist: String = "Artist",
        album: String = "Album",
    ) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()

    private fun requireSingleConnection(): DiscordRpcConnection {
        assertEquals(1, createdConnections.size, "exactly one connection expected so far")
        return createdConnections.first()
    }

    @Test
    fun `paused 60 s with pause presence enabled keeps the paused presence`() = runTest {
        val manager = newManager(UnconfinedTestDispatcher(testScheduler))
        try {
            manager.onPlayingStateChanged(mediaItem(), isPlaying = false, position = 10_000, duration = 100_000)
            advanceTimeBy(5_001) // the debounced paused presence (t = 5001: the test scheduler
            // only runs events strictly before now + d)

            val connection = requireSingleConnection()
            coVerify(exactly = 1) {
                connection.setActivity(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
            // The paused line keeps the frozen representation.
            val details = slot<String>()
            coVerify {
                connection.setActivity(
                    any(), any(), any(), capture(details), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
            assertEquals("⏸︎ Paused: Song", details.captured)
            // Item 10: the connection created by the write path emptied the artwork cache.
            coVerify(exactly = 1) { connection.clearArtworkCache() }

            // The 60 s pause-clear deadline passes — with the pause presence enabled, the
            // paused presence IS the active state: it stays on Discord (no clear, connection kept).
            advanceTimeBy(55_000) // t = 60001 > 60000: the pause-clear deadline, no event in between
            coVerify(exactly = 0) { connection.clearActivity(any()) }
            coVerify(exactly = 0) { connection.closeDirect() } // the connection is kept (onStop not called yet)
        } finally {
            manager.onStop()
        }
    }

    @Test
    fun `paused 60 s with no event and pause presence disabled skips the paused update`() = runTest {
        val manager = newManager(
            UnconfinedTestDispatcher(testScheduler),
            settings = DiscordAdvancedSettings.DEFAULTS.copy(pausePresenceEnabled = false),
        )
        try {
            manager.onPlayingStateChanged(mediaItem(), isPlaying = false, position = 10_000, duration = 100_000)
            advanceTimeBy(5_001) // the debounced paused update fires — and is skipped

            // The event still creates the connection (before the skip), but nothing is ever sent.
            val connection = requireSingleConnection()
            coVerify(exactly = 0) {
                connection.setActivity(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }

            // The 60 s pause-clear deadline: the stale clear fires on the activity-less
            // connection (a no-op) — the connection itself is kept.
            advanceTimeBy(55_000) // t = 60001 > 60000
            coVerify(exactly = 1) { connection.clearActivity(any()) }
            coVerify(exactly = 0) { connection.closeDirect() }
        } finally {
            manager.onStop()
        }
    }

    @Test
    fun `idle 10 min without an active pause presence closes the connection, the next event re-creates it and re-sends presence`() = runTest {
        val manager = newManager(
            UnconfinedTestDispatcher(testScheduler),
            settings = DiscordAdvancedSettings.DEFAULTS.copy(pausePresenceEnabled = false),
        )
        try {
            manager.onPlayingStateChanged(mediaItem(), isPlaying = false, position = 10_000, duration = 100_000)
            advanceTimeBy(600_001) // t = 600001 > 600000: the 60 s pause clear (t = 60 s) then the idle close

            val first = requireSingleConnection()
            // Pause presence off: the paused update is skipped, so nothing is ever sent on
            // the first connection — only the stale clear (a no-op) and the idle close.
            coVerify(exactly = 0) {
                first.setActivity(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
            coVerify(exactly = 1) { first.clearActivity(any()) }
            coVerify(exactly = 1) { first.closeDirect() } // the idle timer closed it (onStop not called yet)

            // The module close is terminal: the next media event (same token) must create a brand-new
            // connection and re-send the presence on it. The isPlaying provider goes false at the
            // first tick so the refresh loop stops without re-sending (the debounced send stands alone).
            manager.onPlayingStateChanged(
                mediaItem(),
                isPlaying = true,
                position = 10_000,
                duration = 100_000,
                isPlayingProvider = { false },
            )
            advanceTimeBy(5_001)

            assertEquals(2, createdConnections.size, "a fresh connection must be created after the idle close")
            val second = createdConnections[1]
            coVerify(exactly = 2) { first.closeDirect() } // the stale connection is closed before the new one
            // Item 10: both connections emptied the process-wide artwork cache at creation.
            coVerify(exactly = 1) { first.clearArtworkCache() }
            coVerify(exactly = 1) { second.clearArtworkCache() }
            coVerify(exactly = 1) {
                second.setActivity(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
        } finally {
            manager.onStop()
        }
    }

    @Test
    fun `idle 10 min with an active pause presence keeps the connection open`() = runTest {
        val manager = newManager(UnconfinedTestDispatcher(testScheduler))
        try {
            manager.onPlayingStateChanged(mediaItem(), isPlaying = false, position = 10_000, duration = 100_000)
            advanceTimeBy(5_001) // the debounced paused presence

            val connection = requireSingleConnection()
            // t = 605001: the 10 min idle-close deadline passed — with an active paused
            // presence (enabled) the timer re-arms instead of closing, so the presence
            // survives long pauses.
            advanceTimeBy(600_000)
            coVerify(exactly = 0) { connection.closeDirect() }
            coVerify(exactly = 0) { connection.clearActivity(any()) }
        } finally {
            manager.onStop()
        }
    }

    @Test
    fun `paused 60 s with auto-clear disabled keeps the presence`() = runTest {
        val manager = newManager(
            UnconfinedTestDispatcher(testScheduler),
            settings = DiscordAdvancedSettings.DEFAULTS.copy(pausePresenceEnabled = false, pauseClearEnabled = false),
        )
        try {
            manager.onPlayingStateChanged(mediaItem(), isPlaying = false, position = 10_000, duration = 100_000)
            advanceTimeBy(5_001) // the debounced paused update fires — and is skipped

            val connection = requireSingleConnection()
            coVerify(exactly = 0) {
                connection.setActivity(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }

            // The 60 s pause-clear deadline passes — the auto-clear is disabled, so the
            // (activity-less) connection is left alone: no clear, no close.
            advanceTimeBy(55_000) // t = 60001 > 60000
            coVerify(exactly = 0) { connection.clearActivity(any()) }
            coVerify(exactly = 0) { connection.closeDirect() }
        } finally {
            manager.onStop()
        }
    }

    @Test
    fun `idle 10 min with the idle close disabled keeps the connection open`() = runTest {
        val manager = newManager(
            UnconfinedTestDispatcher(testScheduler),
            settings = DiscordAdvancedSettings.DEFAULTS.copy(idleCloseEnabled = false),
        )
        try {
            manager.onPlayingStateChanged(mediaItem(), isPlaying = false, position = 10_000, duration = 100_000)
            advanceTimeBy(5_001) // the debounced paused presence

            val connection = requireSingleConnection()
            coVerify(exactly = 1) {
                connection.setActivity(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }

            // t = 605001: the 10 min idle-close deadline passed (the timer re-arms at each
            // deadline while disabled) — the connection stays open with the paused presence.
            advanceTimeBy(600_000)
            coVerify(exactly = 0) { connection.closeDirect() }
            coVerify(exactly = 0) { connection.clearActivity(any()) }
        } finally {
            manager.onStop()
        }
    }
}
