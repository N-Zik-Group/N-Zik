package app.n_zik.android.extensions.discord

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import com.metrolist.music.discordrpc.DiscordRpcConnection
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
import org.junit.jupiter.api.Test

/**
 * Item 8 (NETWORK_DOWN_UP): the refresh tick keeps running while the network is down and
 * re-arms the presence send on the next tick (≤ 5 s) after the network returns. The token/network
 * early-return in [DiscordPresenceManager.onPlayingStateChanged] never arms the debounce, so the
 * re-arm comes from the refresh loop — exactly one send on the first healthy tick.
 */
class DiscordNetworkReArmTest {

    private val managers = mutableListOf<DiscordPresenceManager>()

    @AfterEach
    fun tearDown() {
        managers.forEach { it.onStop() }
        managers.clear()
        unmockkAll()
        DiscordUiState.currentRoute.value = null
    }

    private fun newManager(
        dispatcher: TestDispatcher,
        connection: DiscordRpcConnection,
        networkAvailable: () -> Boolean,
        onConnectionCreated: () -> Unit,
    ): DiscordPresenceManager {
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkAvailable(any()) } answers { networkAvailable() }
        every { connection.reconnectAbandoned } returns MutableStateFlow(false)
        every { connection.terminalCloseCode } returns MutableStateFlow(null)
        val manager = DiscordPresenceManager(
            context = discordTestContext(),
            getToken = { "test-token" },
            getAdvancedSettings = { DiscordAdvancedSettings.DEFAULTS },
            externalScope = CoroutineScope(dispatcher),
            connectionFactory = { onConnectionCreated(); connection },
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

    @Test
    fun `network down at play sends nothing, the refresh tick re-arms the send when the network returns`() = runTest {
        var networkAvailable = false
        var connectionsCreated = 0
        val connection = mockk<DiscordRpcConnection>(relaxed = true)
        val manager = newManager(
            UnconfinedTestDispatcher(testScheduler),
            connection,
            networkAvailable = { networkAvailable },
            onConnectionCreated = { connectionsCreated++ },
        )
        try {
            manager.onPlayingStateChanged(
                mediaItem(),
                isPlaying = true,
                position = 60_000,
                duration = 180_000,
                playbackSpeed = 1f,
                getCurrentPosition = { 62_000 },
                isPlayingProvider = { true },
            )
            // Early-return path: state kept, refresh loop armed, but no connection and no write.
            coVerify(exactly = 0) {
                connection.setActivity(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
            assertEquals(0, connectionsCreated, "no connection while the network is down")

            networkAvailable = true
            advanceTimeBy(5_001) // the first refresh tick (the debounce was never armed)

            coVerify(exactly = 1) {
                connection.setActivity(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
            assertEquals(1, connectionsCreated, "the tick re-arms the send and creates the connection")
        } finally {
            manager.onStop()
        }
    }
}
