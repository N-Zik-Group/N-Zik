package app.n_zik.android.extensions.discord

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import com.metrolist.music.discordrpc.ActivityType
import com.metrolist.music.discordrpc.DiscordRpcConnection
import com.metrolist.music.discordrpc.entities.Timestamps
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Item 5 (upstream parity MS L3601-3610): the playback speed adjusts BOTH timestamp bounds —
 * start = now - position/speed, end = now + (duration - position)/speed — and the details line
 * carries a " [1.50x]" suffix only when the speed differs from 1.0. A speed change re-sends
 * the presence immediately (the manager hook; the 1 s delay lives in the service).
 */
class DiscordPlayingSpeedTest {

    private val managers = mutableListOf<DiscordPresenceManager>()

    @AfterEach
    fun tearDown() {
        managers.forEach { it.onStop() }
        managers.clear()
        unmockkAll()
        DiscordUiState.currentRoute.value = null
    }

    private fun newConnection(): DiscordRpcConnection {
        val connection = mockk<DiscordRpcConnection>(relaxed = true)
        // The manager watches both gateway signals on every connection it creates; give the
        // relaxed mock real StateFlows so the watch jobs do not blow up on property access.
        every { connection.reconnectAbandoned } returns MutableStateFlow(false)
        every { connection.terminalCloseCode } returns MutableStateFlow(null)
        return connection
    }

    private fun newManager(
        dispatcher: TestDispatcher,
        connection: DiscordRpcConnection,
        advanced: DiscordAdvancedSettings = DiscordAdvancedSettings.DEFAULTS,
        networkAvailable: () -> Boolean = { true },
    ): DiscordPresenceManager {
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkAvailable(any()) } answers { networkAvailable() }
        val manager = DiscordPresenceManager(
            context = discordTestContext(),
            getToken = { "test-token" },
            getAdvancedSettings = { advanced },
            externalScope = CoroutineScope(dispatcher),
            connectionFactory = { connection },
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

    /** Captured arguments of the last [DiscordRpcConnection.setActivity] call. */
    private data class ActivityArgs(
        val name: String?,
        val type: ActivityType,
        val state: String?,
        val details: String?,
        val timestamps: Timestamps?,
        val status: String,
        val since: Long?,
    )

    /**
     * Captures the LAST [DiscordRpcConnection.setActivity] call. List captures (not slots):
     * a playing event produces several sends (debounce + refresh tick), and MockK refuses
     * slot captures when the same call is verified more than once. The manager always passes
     * non-null values for the arguments captured here (the nullable parameter types widen,
     * they never null out here).
     */
    private fun lastActivity(connection: DiscordRpcConnection): ActivityArgs {
        val names = mutableListOf<String>()
        val types = mutableListOf<ActivityType>()
        val states = mutableListOf<String>()
        val details = mutableListOf<String>()
        val timestamps = mutableListOf<Timestamps>()
        val statuses = mutableListOf<String>()
        val sinces = mutableListOf<Long>()
        coVerify {
            connection.setActivity(
                capture(names),
                capture(types),
                capture(states),
                capture(details),
                capture(timestamps),
                any(),
                any(),
                any(),
                any(),
                any(),
                capture(statuses),
                capture(sinces),
                any(),
            )
        }
        return ActivityArgs(
            name = names.last(),
            type = types.last(),
            state = states.last(),
            details = details.last(),
            timestamps = timestamps.last(),
            status = statuses.last(),
            since = sinces.last(),
        )
    }

    @Test
    fun `speed 1_5x adjusts both timestamp bounds and carries the 1_50x suffix`() = runTest {
        val connection = newConnection()
        val manager = newManager(UnconfinedTestDispatcher(testScheduler), connection)
        try {
            manager.onPlayingStateChanged(
                mediaItem(),
                isPlaying = true,
                position = 60_000,
                duration = 180_000,
                playbackSpeed = 1.5f,
                getCurrentPosition = { 60_000 },
                isPlayingProvider = { true },
            )
            // The refresh tick and the debounced send share the 5 s deadline (+1 ms: the test
            // scheduler only runs events strictly before now + d).
            advanceTimeBy(5_001)
            coVerify(exactly = 2) {
                connection.setActivity(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
            val now = System.currentTimeMillis()

            val args = lastActivity(connection)
            assertEquals("N-Zik", args.name)
            assertEquals("Song [1.50x]", args.details, "the details line must carry the speed suffix")
            assertEquals("Artist", args.state)
            assertEquals(ActivityType.LISTENING, args.type)
            assertEquals(0L, args.since, "presence updates carry since = 0")
            val timestamps = requireNotNull(args.timestamps) { "timestamps must be set" }
            val start = requireNotNull(timestamps.start) { "start must be set" }
            val end = requireNotNull(timestamps.end) { "end must be set" }
            assertTrue(
                Math.abs(now - start - 40_000) <= 500,
                "start must be ~40 s in the past (60 s / 1.5), was $start (now=$now)"
            )
            assertTrue(
                Math.abs(end - now - 80_000) <= 500,
                "end must be ~80 s in the future ((180 s - 60 s) / 1.5), was $end (now=$now)"
            )
        } finally {
            manager.onStop()
        }
    }

    @Test
    fun `zero speed falls back to 1_0 without a suffix`() = runTest {
        val connection = newConnection()
        val manager = newManager(UnconfinedTestDispatcher(testScheduler), connection)
        try {
            manager.onPlayingStateChanged(
                mediaItem(),
                isPlaying = true,
                position = 60_000,
                duration = 180_000,
                playbackSpeed = 0f,
                getCurrentPosition = { 60_000 },
                isPlayingProvider = { true },
            )
            advanceTimeBy(5_001)
            val now = System.currentTimeMillis()

            val args = lastActivity(connection)
            assertEquals("Song", args.details, "speed 0 must be treated as 1.0 (no suffix)")
            val timestamps = requireNotNull(args.timestamps) { "timestamps must be set" }
            val start = requireNotNull(timestamps.start) { "start must be set" }
            val end = requireNotNull(timestamps.end) { "end must be set" }
            assertTrue(
                Math.abs(now - start - 60_000) <= 500,
                "start must be ~60 s in the past (1.0x), was $start (now=$now)"
            )
            assertTrue(
                Math.abs(end - now - 120_000) <= 500,
                "end must be ~120 s in the future (1.0x), was $end (now=$now)"
            )
        } finally {
            manager.onStop()
        }
    }

    @Test
    fun `speed change re-sends the presence with the new speed and bounds`() = runTest {
        val connection = newConnection()
        val manager = newManager(UnconfinedTestDispatcher(testScheduler), connection)
        try {
            manager.onPlayingStateChanged(
                mediaItem(),
                isPlaying = true,
                position = 60_000,
                duration = 180_000,
                playbackSpeed = 1f,
                getCurrentPosition = { 60_000 },
                isPlayingProvider = { true },
            )
            advanceTimeBy(5_001)
            coVerify(exactly = 2) {
                connection.setActivity(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
            assertEquals("Song", lastActivity(connection).details, "1.0x carries no suffix")

            // The hook re-sends immediately (no debounce): a third write carries the new speed.
            manager.onPlaybackSpeedChanged(2f)
            val now = System.currentTimeMillis()

            coVerify(exactly = 3) {
                connection.setActivity(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
            val args = lastActivity(connection)
            assertEquals("Song [2.00x]", args.details, "the re-sent presence must reflect the new speed")
            val timestamps = requireNotNull(args.timestamps) { "timestamps must be set" }
            val start = requireNotNull(timestamps.start) { "start must be set" }
            val end = requireNotNull(timestamps.end) { "end must be set" }
            assertTrue(
                Math.abs(now - start - 30_000) <= 500,
                "start must be ~30 s in the past (60 s / 2), was $start (now=$now)"
            )
            assertTrue(
                Math.abs(end - now - 60_000) <= 500,
                "end must be ~60 s in the future ((180 s - 60 s) / 2), was $end (now=$now)"
            )
        } finally {
            manager.onStop()
        }
    }
}
