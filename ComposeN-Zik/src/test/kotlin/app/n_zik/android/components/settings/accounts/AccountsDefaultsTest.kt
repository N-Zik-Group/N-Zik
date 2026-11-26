package app.n_zik.android.components.settings.accounts

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.components.settings.useLoginForSearchKey
import app.n_zik.android.core.network.client.Store
import app.n_zik.android.extensions.discord.DiscordAdvancedSettings
import app.n_zik.android.extensions.youtubelogin.putYouTubeSession
import app.n_zik.android.extensions.youtubelogin.readYouTubeSession
import app.n_zik.android.listentogether.ListenTogetherClient
import app.n_zik.android.listentogether.ListenTogetherManager
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.discordUsernameKey
import app.n_zik.android.utils.enableYouTubeLoginKey
import app.n_zik.android.utils.enableYouTubeSyncKey
import app.n_zik.android.utils.discordPersonalAccessTokenKey
import app.n_zik.android.utils.encryptedPreferences
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.utils.useLoginForBrowseKey
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YouTubeSession
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AccountsDefaultsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val secure: SharedPreferences = context.getSharedPreferences("accounts-test-secure", Context.MODE_PRIVATE)
    private val expected: Map<String, Any> = mapOf(
        "enableYoutubeLogin" to false,
        "enableYoutubeSync" to false,
        "isDiscordPresenceEnabled" to false,
        "isDiscordAdvancedMode" to false,
        "discordAdvancedActivityType" to 2,
        "discordAdvancedPausePresenceEnabled" to true,
        "discordAdvancedName" to "",
        "discordAdvancedStateTemplate" to "",
        "discordAdvancedDetailsTemplate" to "",
        "discordAdvancedPauseTemplate" to "",
        "discordAdvancedButton1Enabled" to true,
        "discordAdvancedButton1Label" to "",
        "discordAdvancedButton1Url" to "",
        "discordAdvancedButton2Enabled" to true,
        "discordAdvancedButton2Label" to "",
        "discordAdvancedButton2Url" to "",
    )

    @Before fun setUp() {
        setActiveProfile("accounts-test", context)
        context.preferences.edit().clear().commit()
        DataStoreUtils.prefs(context).edit().clear().commit()
        secure.edit().clear().commit()
        mockkStatic("app.n_zik.android.utils.EncryptedPreferencesKt")
        every { any<Context>().encryptedPreferences } returns secure
    }

    @After fun tearDown() {
        unmockkAll()
        setActiveProfile("default", context)
    }

    @Test fun resetRestoresOriginalDefaultsThroughTheirReaders() {
        val editor = secure.edit()
        expected.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, !value)
                is Int -> editor.putInt(key, 7)
                is String -> editor.putString(key, "seeded")
            }
        }
        editor.commit()
        context.preferences.edit().putBoolean("restartActivity", true).commit()

        applyAccountsDefaults(context)

        assertEquals(expected.filterKeys { !it.startsWith("discordAdvanced") && it != "isDiscordAdvancedMode" }, secure.all)
        assertEquals(DiscordAdvancedSettings.DEFAULTS, DiscordAdvancedSettings.read(secure))
        assertEquals(
            mapOf(
                "restartActivity" to false,
                useLoginForBrowseKey to false,
                useLoginForSearchKey to false,
                "githubAccountUsageEnabled" to false,
            ),
            context.preferences.all,
        )
    }

    @Test fun accountsResetPausesGitHubChecksAndKeepsTheOptionReversible() {
        assertTrue(GitHubAccount.backgroundChecksEnabled(context))
        context.preferences.edit().putBoolean(GITHUB_BACKGROUND_CHECKS_KEY, false).commit()
        applyAccountsDefaults(context)
        assertFalse(context.preferences.contains(GITHUB_BACKGROUND_CHECKS_KEY))
        assertFalse(GitHubAccount.backgroundChecksEnabled(context))
        GitHubAccount.resumeSavedAccount(context)
        assertTrue(GitHubAccount.backgroundChecksEnabled(context))
    }

    @Test fun resetDisablesTheLiveYouTubeSessionAndRetainsCredentialsForReactivation() {
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        val session = YouTubeSession("SAPISID=fixture", "visitor", "channel", "2")
        secure.edit().apply {
            putYouTubeSession(session)
            putBoolean(enableYouTubeLoginKey, true)
            putBoolean(enableYouTubeSyncKey, true)
        }.commit()
        val previous = Innertube.youtubeSessionSnapshot()
        val previousBrowse = Innertube.useLoginForBrowse
        val previousSearch = Innertube.useLoginForSearch
        val previousStatus = MainApplication.cookieStatus
        try {
            Store.setSession(session)
            Innertube.useLoginForBrowse = true
            Innertube.useLoginForSearch = true
            MainApplication.cookieStatus = MainApplication.CookieStatus.VALID

            applyAccountsDefaults(context)

            assertNull(Innertube.cookie)
            assertFalse(Innertube.useLoginForBrowse)
            assertFalse(Innertube.useLoginForSearch)
            assertEquals(MainApplication.CookieStatus.NOT_LOGGED_IN, MainApplication.cookieStatus)
            assertFalse(secure.getBoolean(enableYouTubeLoginKey, true))
            assertFalse(secure.getBoolean(enableYouTubeSyncKey, true))
            assertEquals(session, secure.readYouTubeSession())
            Store.initSession(context)
            assertEquals(session, Innertube.youtubeSessionSnapshot())
        } finally {
            Store.setSession(previous)
            Innertube.useLoginForBrowse = previousBrowse
            Innertube.useLoginForSearch = previousSearch
            MainApplication.cookieStatus = previousStatus
        }
    }

    @Test fun resetPreservesDeviceWideListenTogetherChoicesAndSession() {
        val enabledChoices = listOf(
            ListenTogetherClient.PREF_AUTO_APPROVAL,
            ListenTogetherClient.PREF_AUTO_APPROVE_SUGGESTIONS,
            ListenTogetherClient.PREF_HISTORY,
        )
        val prefs = DataStoreUtils.prefs(context)
        prefs.edit().apply {
            enabledChoices.forEach { putBoolean(it, true) }
            putBoolean(ListenTogetherManager.PREF_SYNC_HOST_VOLUME, false)
            putString(ListenTogetherClient.PREF_SESSION_TOKEN, "saved-token")
            putString(ListenTogetherClient.PREF_SERVER_URL, "https://room.example")
            putString(ListenTogetherClient.PREF_BLOCKED_USERS, "saved-blocklist")
        }.commit()
        val manager = ListenTogetherManager(mockk(relaxed = true), context)
        assertFalse(manager.syncHostVolumeEnabled.value)

        applyAccountsDefaults(context)

        enabledChoices.forEach { assertTrue(prefs.getBoolean(it, false)) }
        assertFalse(prefs.getBoolean(ListenTogetherManager.PREF_SYNC_HOST_VOLUME, true))
        assertFalse(manager.syncHostVolumeEnabled.value)
        assertEquals("saved-token", prefs.getString(ListenTogetherClient.PREF_SESSION_TOKEN, null))
        assertEquals("https://room.example", prefs.getString(ListenTogetherClient.PREF_SERVER_URL, null))
        assertEquals("saved-blocklist", prefs.getString(ListenTogetherClient.PREF_BLOCKED_USERS, null))
    }

    @Test fun resetRestoresAdvancedDiscordLastFmAndSyncSettingsWhilePreservingIdentity() {
        secure.edit()
            .putString("ytCookie", "test-cookie")
            .putString("ytDataSyncId", "saved-sync-id")
            .putString("ytAccountName", "saved-account")
            .putString(discordPersonalAccessTokenKey, "test-token")
            .putString(discordUsernameKey, "saved-discord")
            .putBoolean("discordAdvancedShowArtwork", false)
            .putBoolean("discordAdvancedIdleCloseEnabled", false)
            .putInt("discordAdvancedRefreshIntervalMs", 17000)
            .putString("discordAdvancedLargeImageText", "custom")
            .putBoolean("isLastfmScrobblingEnabled", true)
            .putBoolean("isLastfmNowPlayingEnabled", false)
            .putInt("lastfmMinTrackDurationSeconds", 60)
            .putString("lastfmSessionKey", "saved-session")
            .putString("lastfmUsername", "saved-lastfm")
            .putString("apiKey", "saved-key")
            .commit()
        val profile = context.preferences
        profile.edit()
            .putString("syncDirection", "PUSH_ONLY")
            .putInt("syncCooldown", 90)
            .putBoolean("syncShowDetails", false)
            .putBoolean("syncBackgroundGuard", false)
            .putBoolean("syncPushSongLike", true)
            .putBoolean("syncImportEpisodes", true)
            .putBoolean("useLoginForSearch", false)
            .putBoolean("useLoginForBrowse", false)
            .putString("syncSessionCursor", "saved-cursor")
            .commit()

        applyAccountsDefaults(context)
        applyAccountsDefaults(context)

        assertEquals(DiscordAdvancedSettings.DEFAULTS, DiscordAdvancedSettings.read(secure))
        assertFalse(secure.getBoolean("isLastfmScrobblingEnabled", false))
        assertTrue(secure.getBoolean("isLastfmNowPlayingEnabled", true))
        assertEquals(30, secure.getInt("lastfmMinTrackDurationSeconds", 30))
        assertEquals("TWO_WAY", profile.getString("syncDirection", "TWO_WAY"))
        assertEquals(30, profile.getInt("syncCooldown", 30))
        assertTrue(profile.getBoolean("syncShowDetails", true))
        assertTrue(profile.getBoolean("syncBackgroundGuard", true))
        assertFalse(profile.getBoolean("syncPushSongLike", false))
        assertFalse(profile.getBoolean("syncImportEpisodes", false))
        assertFalse(profile.getBoolean(useLoginForSearchKey, true))
        assertFalse(profile.getBoolean(useLoginForBrowseKey, true))
        assertEquals("saved-cursor", profile.getString("syncSessionCursor", null))
        assertEquals("test-cookie", secure.getString("ytCookie", null))
        assertEquals("saved-sync-id", secure.getString("ytDataSyncId", null))
        assertEquals("saved-account", secure.getString("ytAccountName", null))
        assertEquals("test-token", secure.getString(discordPersonalAccessTokenKey, null))
        assertEquals("saved-discord", secure.getString(discordUsernameKey, null))
        assertEquals("saved-session", secure.getString("lastfmSessionKey", null))
        assertEquals("saved-lastfm", secure.getString("lastfmUsername", null))
        assertEquals("saved-key", secure.getString("apiKey", null))
    }
}
