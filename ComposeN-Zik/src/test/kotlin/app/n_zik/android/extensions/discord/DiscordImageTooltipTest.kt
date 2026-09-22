package app.n_zik.android.extensions.discord

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.R
import app.n_zik.android.core.database.AlbumTable
import app.n_zik.android.core.database.Database
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Image tooltips: the advanced mode can override the large image tooltip (built-in
 * default: the album when the app knows it, otherwise "details - state") and the small
 * image tooltip (default "v{app.version}") with templates — `{app.version}` resolves to
 * the app version name. Normal mode keeps the built-in defaults (the templates are
 * ignored, frozen identity).
 */
class DiscordImageTooltipTest {

    private val managers = mutableListOf<DiscordPresenceManager>()

    @AfterEach
    fun tearDown() {
        managers.forEach { it.onStop() }
        managers.clear()
        unmockkAll()
        DiscordUiState.currentRoute.value = null
    }

    /** [discordTestContext] plus a resolvable version name for `{app.version}`. */
    private fun contextWithVersion(): Context {
        val context = discordTestContext()
        val packageManager = mockk<PackageManager>()
        // PackageInfo.versionName is a public FIELD, not a getter — MockK cannot `every`
        // it; assign it in the mock construction block instead.
        val packageInfo = mockk<PackageInfo> { versionName = "2.3.4" }
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "com.nevar.nzik.debug"
        every { packageManager.getPackageInfo("com.nevar.nzik.debug", 0) } returns packageInfo
        return context
    }

    private fun newConnection(): DiscordRpcConnection {
        val connection = mockk<DiscordRpcConnection>(relaxed = true)
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
            context = contextWithVersion(),
            getToken = { "test-token" },
            getAdvancedSettings = { advanced },
            externalScope = CoroutineScope(dispatcher),
            connectionFactory = { connection },
            tokenValidator = { true }
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

    /** Captured image arguments of the LAST [DiscordRpcConnection.setActivity] call. */
    private data class ImageArgs(
        val largeImage: String?,
        val largeText: String?,
        val smallImage: String?,
        val smallText: String?,
    )

    private fun lastImages(connection: DiscordRpcConnection): ImageArgs {
        // `match` captures (not `capture(list)`): a playing event produces several sends
        // (debounce + refresh tick), MockK refuses slot captures on multiple matching
        // calls, and plain `capture` cannot hold nullable arguments.
        val largeImages = mutableListOf<String?>()
        val largeTexts = mutableListOf<String?>()
        val smallImages = mutableListOf<String?>()
        val smallTexts = mutableListOf<String?>()
        coVerify {
            connection.setActivity(
                any(), any(), any(), any(), any(),
                match { largeImages += it; true },
                match { largeTexts += it; true },
                match { smallImages += it; true },
                match { smallTexts += it; true },
                any(), any(), any(), any(),
            )
        }
        return ImageArgs(
            largeImage = largeImages.last(),
            largeText = largeTexts.last(),
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
    fun `custom image tooltips are rendered from the templates in advanced mode`() = runTest {
        val connection = newConnection()
        val manager = newManager(
            UnconfinedTestDispatcher(testScheduler),
            connection,
            DiscordAdvancedSettings.DEFAULTS.copy(
                advancedMode = true,
                largeImageTextTemplate = "{album.name} v{app.version}",
                smallImageTextTemplate = "{song.id} {app.version}",
            ),
        )
        try {
            play(manager, this)

            val args = lastImages(connection)
            assertEquals("Album v2.3.4", args.largeText, "the large image template must be rendered")
            assertEquals("dQw4w9WgXcQ 2.3.4", args.smallText, "the small image template must be rendered")
            assertNotNull(args.largeImage, "the template does not hide the artwork itself")
            assertNotNull(args.smallImage, "the template does not hide the app logo itself")
        } finally {
            manager.onStop()
        }
    }

    @Test
    fun `empty templates keep the built-in image text defaults in advanced mode`() = runTest {
        val connection = newConnection()
        val manager = newManager(
            UnconfinedTestDispatcher(testScheduler),
            connection,
            DiscordAdvancedSettings.DEFAULTS.copy(advancedMode = true),
        )
        try {
            play(manager, this)

            val args = lastImages(connection)
            assertEquals("Album", args.largeText, "default = the album value when the app knows it")
            assertEquals("v2.3.4", args.smallText, "default = v{app.version}")
        } finally {
            manager.onStop()
        }
    }

    @Test
    fun `normal mode keeps the built-in image text defaults (templates ignored)`() = runTest {
        val connection = newConnection()
        val manager = newManager(
            UnconfinedTestDispatcher(testScheduler),
            connection,
            DiscordAdvancedSettings.DEFAULTS.copy(
                // advancedMode = false: the templates must not apply.
                largeImageTextTemplate = "custom large",
                smallImageTextTemplate = "custom small",
            ),
        )
        try {
            play(manager, this)

            val args = lastImages(connection)
            assertEquals("Album", args.largeText, "normal mode keeps the album-first default")
            assertEquals("v2.3.4", args.smallText, "normal mode keeps the default small text")
        } finally {
            manager.onStop()
        }
    }

    @Test
    fun `the default large image text template is the album name`() {
        // The image text field's effective default (empty field) is the album template —
        // the same pattern as the state/details defaults; the real presence renders it
        // live to the album value (unknown album → "details - state", never "Unknown Album").
        assertEquals("{album.name}", DiscordActivityBuilder.DEFAULT_LARGE_IMAGE_TEXT_TEMPLATE)
        assertEquals(
            "My Album",
            DiscordTemplateRenderer.render(
                DiscordActivityBuilder.DEFAULT_LARGE_IMAGE_TEXT_TEMPLATE,
                "Title", "Artist", "My Album", "id", "Unknown Album", "1.0",
            ),
        )
    }

    @Test
    fun `unknown album falls back to the details-state combination for the large image default`() = runTest {
        // No albumTitle in the metadata and no DB row: the built-in default keeps the
        // previous "details - state" combination (same principle as before).
        mockkObject(Database)
        val albumTable = mockk<AlbumTable>()
        every { Database.albumTable } returns albumTable
        every { albumTable.findBySongIdDirect("dQw4w9WgXcQ") } returns null
        val connection = newConnection()
        val manager = newManager(
            UnconfinedTestDispatcher(testScheduler),
            connection,
            DiscordAdvancedSettings.DEFAULTS,
        )
        try {
            manager.onPlayingStateChanged(
                MediaItem.Builder()
                    .setMediaId("dQw4w9WgXcQ")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("Song")
                            .setArtist("Artist")
                            .build()
                    )
                    .build(),
                isPlaying = true,
                position = 10_000,
                duration = 100_000,
                getCurrentPosition = { 10_000 },
                isPlayingProvider = { true },
            )
            advanceTimeBy(5_001)

            val args = lastImages(connection)
            assertEquals("Song - Artist", args.largeText, "unknown album keeps the previous details - state default")
        } finally {
            manager.onStop()
        }
    }
}
