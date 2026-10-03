package app.n_zik.android.extensions.discord

import android.content.SharedPreferences
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
 * Item 6: the advanced settings snapshot is read from the encrypted prefs in one access, and
 * the selected activity type / the fixed "online" status / `since = 0` reach the module's
 * [DiscordRpcConnection] exactly as resolved by the manager — the type is always applied,
 * the status is fixed to online (PW-2 — the user-status selector was removed) and an
 * advanced setting change re-sends the live presence (upstream notifySettingsChanged parity).
 */
class DiscordAdvancedSettingsPrefsTest {

    private val managers = mutableListOf<DiscordPresenceManager>()

    @AfterEach
    fun tearDown() {
        managers.forEach { it.onStop() }
        managers.clear()
        unmockkAll()
        DiscordUiState.currentRoute.value = null
    }

    // ─── Prefs mapping ────────────────────────────────────────────────────────────────

    @Test
    fun `read maps every advanced key from the prefs`() {
        val prefs = mockk<SharedPreferences>()
        every { prefs.getBoolean(isDiscordAdvancedModeKey, false) } returns true
        every { prefs.getInt(discordAdvancedActivityTypeKey, 2) } returns 0
        every { prefs.getBoolean(discordAdvancedPausePresenceEnabledKey, true) } returns false
        every { prefs.getString(discordAdvancedNameKey, "") } returns "My Player"
        every { prefs.getString(discordAdvancedStateTemplateKey, "") } returns "{artist.name}"
        every { prefs.getString(discordAdvancedDetailsTemplateKey, "") } returns "{song.name}"
        every { prefs.getString(discordAdvancedPauseTemplateKey, "") } returns "{song.name} ⏸"
        every { prefs.getBoolean(discordAdvancedButton1EnabledKey, true) } returns false
        every { prefs.getString(discordAdvancedButton1LabelKey, "") } returns "Lbl1"
        every { prefs.getString(discordAdvancedButton1UrlKey, "") } returns "https://one"
        every { prefs.getBoolean(discordAdvancedButton2EnabledKey, true) } returns true
        every { prefs.getString(discordAdvancedButton2LabelKey, "") } returns null
        every { prefs.getString(discordAdvancedButton2UrlKey, "") } returns ""
        every { prefs.getBoolean(discordAdvancedShowStateKey, true) } returns false
        every { prefs.getBoolean(discordAdvancedShowDetailsKey, true) } returns true
        every { prefs.getBoolean(discordAdvancedShowArtworkKey, true) } returns false
        every { prefs.getBoolean(discordAdvancedShowSmallImageKey, true) } returns false
        every { prefs.getBoolean(discordAdvancedShowTimestampsKey, true) } returns true
        every { prefs.getString(discordAdvancedLargeImageTextKey, "") } returns "{album.name} v{app.version}"
        every { prefs.getString(discordAdvancedSmallImageTextKey, "") } returns null
        every { prefs.getBoolean(discordAdvancedPauseClearEnabledKey, true) } returns false
        every { prefs.getBoolean(discordAdvancedIdleCloseEnabledKey, true) } returns false

        val settings = DiscordAdvancedSettings.read(prefs)

        assertTrue(settings.advancedMode)
        assertEquals(0, settings.activityType)
        assertTrue(!settings.pausePresenceEnabled, "the stored toggle must be read (off)")
        assertEquals("My Player", settings.activityName)
        assertEquals("{artist.name}", settings.stateTemplate)
        assertEquals("{song.name}", settings.detailsTemplate)
        assertEquals("{song.name} ⏸", settings.pauseTemplate)
        assertTrue(!settings.button1Enabled)
        assertEquals("Lbl1", settings.button1Label)
        assertEquals("https://one", settings.button1Url)
        assertTrue(settings.button2Enabled)
        assertEquals("", settings.button2Label, "a null stored string falls back to empty")
        assertEquals("", settings.button2Url)
        assertTrue(!settings.showState, "the stored state toggle must be read (off)")
        assertTrue(settings.showDetails, "the stored details toggle must be read (on)")
        assertTrue(!settings.showArtwork, "the stored artwork toggle must be read (off)")
        assertTrue(!settings.showSmallImage, "the stored logo toggle must be read (off)")
        assertTrue(settings.showTimestamps, "the stored timestamps toggle must be read (on)")
        assertEquals("{album.name} v{app.version}", settings.largeImageTextTemplate)
        assertEquals("", settings.smallImageTextTemplate, "a null stored string falls back to empty")
        assertTrue(!settings.pauseClearEnabled, "the stored auto-clear toggle must be read (off)")
        assertTrue(!settings.idleCloseEnabled, "the stored idle-close toggle must be read (off)")
    }

    @Test
    fun `read returns the documented defaults when nothing is stored`() {
        val prefs = mockk<SharedPreferences>()
        every { prefs.getBoolean(isDiscordAdvancedModeKey, false) } returns false
        every { prefs.getInt(discordAdvancedActivityTypeKey, 2) } returns 2
        every { prefs.getBoolean(discordAdvancedPausePresenceEnabledKey, true) } returns true
        every { prefs.getString(discordAdvancedNameKey, "") } returns null
        every { prefs.getString(discordAdvancedStateTemplateKey, "") } returns null
        every { prefs.getString(discordAdvancedDetailsTemplateKey, "") } returns null
        every { prefs.getString(discordAdvancedPauseTemplateKey, "") } returns null
        every { prefs.getBoolean(discordAdvancedButton1EnabledKey, true) } returns true
        every { prefs.getString(discordAdvancedButton1LabelKey, "") } returns null
        every { prefs.getString(discordAdvancedButton1UrlKey, "") } returns null
        every { prefs.getBoolean(discordAdvancedButton2EnabledKey, true) } returns true
        every { prefs.getString(discordAdvancedButton2LabelKey, "") } returns null
        every { prefs.getString(discordAdvancedButton2UrlKey, "") } returns null
        every { prefs.getBoolean(discordAdvancedShowStateKey, true) } returns true
        every { prefs.getBoolean(discordAdvancedShowDetailsKey, true) } returns true
        every { prefs.getBoolean(discordAdvancedShowArtworkKey, true) } returns true
        every { prefs.getBoolean(discordAdvancedShowSmallImageKey, true) } returns true
        every { prefs.getBoolean(discordAdvancedShowTimestampsKey, true) } returns true
        every { prefs.getString(discordAdvancedLargeImageTextKey, "") } returns null
        every { prefs.getString(discordAdvancedSmallImageTextKey, "") } returns null
        every { prefs.getBoolean(discordAdvancedPauseClearEnabledKey, true) } returns true
        every { prefs.getBoolean(discordAdvancedIdleCloseEnabledKey, true) } returns true

        assertEquals(DiscordAdvancedSettings.DEFAULTS, DiscordAdvancedSettings.read(prefs))
    }

    @Test
    fun `the service re-sync key set covers every advanced key`() {
        assertEquals(22, discordAdvancedSettingKeys.size)
        assertTrue(
            discordAdvancedSettingKeys.containsAll(
                listOf(
                    isDiscordAdvancedModeKey,
                    discordAdvancedActivityTypeKey,
                    discordAdvancedPausePresenceEnabledKey,
                    discordAdvancedNameKey,
                    discordAdvancedStateTemplateKey,
                    discordAdvancedDetailsTemplateKey,
                    discordAdvancedPauseTemplateKey,
                    discordAdvancedButton1EnabledKey,
                    discordAdvancedButton1LabelKey,
                    discordAdvancedButton1UrlKey,
                    discordAdvancedButton2EnabledKey,
                    discordAdvancedButton2LabelKey,
                    discordAdvancedButton2UrlKey,
                    discordAdvancedShowStateKey,
                    discordAdvancedShowDetailsKey,
                    discordAdvancedShowArtworkKey,
                    discordAdvancedShowSmallImageKey,
                    discordAdvancedShowTimestampsKey,
                    discordAdvancedLargeImageTextKey,
                    discordAdvancedSmallImageTextKey,
                    discordAdvancedPauseClearEnabledKey,
                    discordAdvancedIdleCloseEnabledKey,
                )
            )
        )
    }

    // ─── Manager wiring (type / online status / since / re-sync / pause toggle) ─────────

    private fun newManager(
        dispatcher: TestDispatcher,
        connection: DiscordRpcConnection,
        advanced: () -> DiscordAdvancedSettings,
    ): DiscordPresenceManager {
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkAvailable(any()) } returns true
        every { connection.reconnectAbandoned } returns MutableStateFlow(false)
        every { connection.terminalCloseCode } returns MutableStateFlow(null)
        val manager = DiscordPresenceManager(
            context = discordTestContext(),
            getToken = { "test-token" },
            getAdvancedSettings = advanced,
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

    /** Captured arguments of the last [DiscordRpcConnection.setActivity] call. */
    private data class ActivityArgs(
        val type: ActivityType,
        val status: String,
        val since: Long?,
        val name: String?,
        val state: String?,
        val details: String?,
        val timestamps: Timestamps?,
    )

    private fun lastActivity(connection: DiscordRpcConnection): ActivityArgs {
        // List captures (not slots): a playing event produces several sends (debounce +
        // refresh tick), and MockK refuses slot captures on multiple matching calls.
        val types = mutableListOf<ActivityType>()
        val statuses = mutableListOf<String>()
        val sinces = mutableListOf<Long>()
        val names = mutableListOf<String>()
        val states = mutableListOf<String>()
        val details = mutableListOf<String>()
        val timestamps = mutableListOf<Timestamps>()
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
            type = types.last(),
            status = statuses.last(),
            since = sinces.last(),
            name = names.last(),
            state = states.last(),
            details = details.last(),
            timestamps = timestamps.last(),
        )
    }

    @Test
    fun `advanced on, playing type, online status and since 0 reach setActivity`() = runTest {
        val connection = mockk<DiscordRpcConnection>(relaxed = true)
        val manager = newManager(
            UnconfinedTestDispatcher(testScheduler),
            connection,
            {
                DiscordAdvancedSettings.DEFAULTS.copy(
                    advancedMode = true,
                    activityType = 0,
                )
            },
        )
        try {
            manager.onPlayingStateChanged(
                mediaItem(),
                isPlaying = true,
                position = 10_000,
                duration = 100_000,
                getCurrentPosition = { 10_000 },
                isPlayingProvider = { true },
            )
            advanceTimeBy(5_001) // the debounced send (the test scheduler only runs events
            // strictly before now + d)

            val args = lastActivity(connection)
            assertEquals(ActivityType.PLAYING, args.type, "the selected activity type must be applied")
            assertEquals(DISCORD_STATUS_ONLINE, args.status, "the status is fixed to online (PW-2)")
            assertEquals(0L, args.since, "presence updates carry since = 0")
            // The default templates keep the NZik identity lines.
            assertEquals("N-Zik", args.name)
            assertEquals("Artist", args.state)
            assertEquals("Song", args.details)
        } finally {
            manager.onStop()
        }
    }

    @Test
    fun `an advanced setting change re-sends the live presence with the new content`() = runTest {
        val connection = mockk<DiscordRpcConnection>(relaxed = true)
        var current = DiscordAdvancedSettings.DEFAULTS.copy(
            advancedMode = true,
            detailsTemplate = "{artist.name}",
        )
        val manager = newManager(
            UnconfinedTestDispatcher(testScheduler),
            connection,
            { current },
        )
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
            assertEquals("Artist", lastActivity(connection).details, "the details template must be rendered")

            // The user edits a template in settings → the service forwards the change.
            current = current.copy(detailsTemplate = "{song.name}")
            manager.onAdvancedSettingsChanged()
            testScheduler.runCurrent()

            assertEquals("Song", lastActivity(connection).details, "the re-sync must carry the re-rendered content")
        } finally {
            manager.onStop()
        }
    }

    @Test
    fun `a disabled pause presence sends no paused update`() = runTest {
        val connection = mockk<DiscordRpcConnection>(relaxed = true)
        val manager = newManager(
            UnconfinedTestDispatcher(testScheduler),
            connection,
            { DiscordAdvancedSettings.DEFAULTS.copy(pausePresenceEnabled = false) },
        )
        try {
            // Playing first, so a presence exists (debounce send + refresh tick).
            manager.onPlayingStateChanged(
                mediaItem(),
                isPlaying = true,
                position = 10_000,
                duration = 100_000,
                getCurrentPosition = { 10_000 },
                isPlayingProvider = { true },
            )
            advanceTimeBy(5_001)
            coVerify(exactly = 2) {
                connection.setActivity(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }

            // Then a pause: the paused update must be short-circuited (no new send).
            manager.onPlayingStateChanged(mediaItem(), isPlaying = false, position = 20_000, duration = 100_000)
            advanceTimeBy(5_001)
            coVerify(exactly = 2) {
                connection.setActivity(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
        } finally {
            manager.onStop()
        }
    }
}
