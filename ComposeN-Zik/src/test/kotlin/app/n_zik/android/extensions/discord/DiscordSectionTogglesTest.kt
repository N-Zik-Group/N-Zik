package app.n_zik.android.extensions.discord

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.core.network.utils.NetworkQualityHelper
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Per-section visibility toggles (advanced mode only): a disabled section must reach the
 * module as NULL — the module then omits the field from the JSON (no empty line, no asset,
 * no progress bar). Normal mode keeps the frozen NZik identity: the toggles are ignored
 * there, so every section is still sent.
 */
class DiscordSectionTogglesTest {

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
        advanced: DiscordAdvancedSettings,
    ): DiscordPresenceManager {
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkAvailable(any()) } returns true
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

    /** Captured arguments of the LAST [DiscordRpcConnection.setActivity] call. */
    private data class ActivityArgs(
        val state: String?,
        val details: String?,
        val timestamps: Timestamps?,
        val largeImage: String?,
        val smallImage: String?,
        val smallText: String?,
    )

    /**
     * Captures the LAST [DiscordRpcConnection.setActivity] call via `match` (not
     * `capture(list)`): a playing event produces several sends (debounce + refresh
     * tick), MockK refuses slot captures when the same call is verified more than
     * once, and plain `capture` cannot hold nullable arguments (disabled sections
     * are null).
     */
    private fun lastActivity(connection: DiscordRpcConnection): ActivityArgs {
        val states = mutableListOf<String?>()
        val details = mutableListOf<String?>()
        val timestamps = mutableListOf<Timestamps?>()
        val largeImages = mutableListOf<String?>()
        val smallImages = mutableListOf<String?>()
        val smallTexts = mutableListOf<String?>()
        // `any()` and `match {}` do NOT match null in MockK — disabled sections arrive
        // as null, so every nullable slot needs `anyNullable()` / `matchNullable {}`
        // (the matcher also captures the null into the list).
        coVerify {
            connection.setActivity(
                any(), any(),
                matchNullable { states += it; true },
                matchNullable { details += it; true },
                matchNullable { timestamps += it; true },
                matchNullable { largeImages += it; true },
                anyNullable(),
                matchNullable { smallImages += it; true },
                matchNullable { smallTexts += it; true },
                any(), any(), any(), any(),
            )
        }
        return ActivityArgs(
            state = states.last(),
            details = details.last(),
            timestamps = timestamps.last(),
            largeImage = largeImages.last(),
            smallImage = smallImages.last(),
            smallText = smallTexts.last(),
        )
    }

    private fun play(manager: DiscordPresenceManager, scope: TestScope) {
        manager.onPlayingStateChanged(
            mediaItem(),
            isPlaying = true,
            position = 10_000,
            duration = 100_000,
            getCurrentPosition = { 10_000 },
            isPlayingProvider = { true },
        )
        scope.advanceTimeBy(5_001)
    }

    @Test
    fun `disabled sections arrive at the module as null in advanced mode`() = runTest {
        val connection = newConnection()
        val manager = newManager(
            UnconfinedTestDispatcher(testScheduler),
            connection,
            DiscordAdvancedSettings.DEFAULTS.copy(
                advancedMode = true,
                showState = false,
                showDetails = false,
                showArtwork = false,
                showSmallImage = false,
                showTimestamps = false,
            ),
        )
        try {
            play(manager, this)

            val args = lastActivity(connection)
            assertNull(args.state, "the disabled state section must not be sent")
            assertNull(args.details, "the disabled details section must not be sent")
            assertNull(args.timestamps, "the disabled progress bar must not be sent")
            assertNull(args.largeImage, "the disabled artwork must not be sent")
            assertNull(args.smallImage, "the disabled app logo must not be sent")
            assertNull(args.smallText, "no version text without the app logo")
        } finally {
            manager.onStop()
        }
    }

    @Test
    fun `enabled sections keep the current behavior in advanced mode`() = runTest {
        val connection = newConnection()
        val manager = newManager(
            UnconfinedTestDispatcher(testScheduler),
            connection,
            DiscordAdvancedSettings.DEFAULTS.copy(advancedMode = true),
        )
        try {
            play(manager, this)

            val args = lastActivity(connection)
            assertEquals("Artist", args.state)
            assertEquals("Song", args.details)
            assertNotNull(args.timestamps, "the progress bar must be sent")
            assertNotNull(args.largeImage, "the artwork (fallback URL) must be sent")
            assertNotNull(args.smallImage, "the app logo must be sent")
            assertNotNull(args.smallText, "the version text must be sent with the app logo")
        } finally {
            manager.onStop()
        }
    }

    @Test
    fun `normal mode ignores the section toggles (frozen identity)`() = runTest {
        val connection = newConnection()
        val manager = newManager(
            UnconfinedTestDispatcher(testScheduler),
            connection,
            DiscordAdvancedSettings.DEFAULTS.copy(
                // advancedMode = false: every toggle off, all sections must still be sent.
                showState = false,
                showDetails = false,
                showArtwork = false,
                showSmallImage = false,
                showTimestamps = false,
            ),
        )
        try {
            play(manager, this)

            val args = lastActivity(connection)
            assertEquals("Artist", args.state, "normal mode keeps the fixed state line")
            assertEquals("Song", args.details, "normal mode keeps the fixed details line")
            assertNotNull(args.timestamps, "normal mode keeps the progress bar")
            assertNotNull(args.largeImage, "normal mode keeps the artwork")
            assertNotNull(args.smallImage, "normal mode keeps the app logo")
            assertTrue(args.smallText.orEmpty().startsWith("v"), "the version text follows the app logo")
        } finally {
            manager.onStop()
        }
    }
}
