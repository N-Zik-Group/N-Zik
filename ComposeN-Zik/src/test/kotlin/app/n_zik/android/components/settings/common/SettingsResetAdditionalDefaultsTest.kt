package app.n_zik.android.components.settings.common

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.checkUpdateStateKey
import app.it.fast4x.rimusic.utils.checkBetaUpdatesKey
import app.it.fast4x.rimusic.utils.otherLanguageAppUpdateKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.core.backup.BackupManager
import app.n_zik.android.extensions.discord.DiscordAdvancedSettings
import app.n_zik.android.utils.DataStoreUtils
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsResetAdditionalDefaultsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val secure: SharedPreferences = context.getSharedPreferences("extra-reset-secure", Context.MODE_PRIVATE)

    @Before fun setUp() {
        setActiveProfile("extra-reset", context)
        context.preferences.edit().clear().commit()
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().clear().commit()
        DataStoreUtils.prefs(context).edit().clear().commit()
        secure.edit().clear().commit()
        mockkStatic("app.it.fast4x.rimusic.utils.EncryptedPreferencesKt")
        every { any<Context>().encryptedPreferences } returns secure
    }

    @After fun tearDown() {
        unmockkAll()
        setActiveProfile("default", context)
    }

    @Test fun resetRestoresRewindReadDefaultsWithoutReplayingOnboardingOrNotifications() {
        val flags = listOf(
            DataStoreUtils.KEY_REWIND_ENABLED, DataStoreUtils.KEY_REWIND_YEARLY_ENABLED,
            DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, DataStoreUtils.KEY_REWIND_GLOBAL_ENABLED,
            DataStoreUtils.KEY_REWIND_MONTHLY_NOTIF_ENABLED, DataStoreUtils.KEY_REWIND_YEARLY_NOTIF_ENABLED,
            DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED,
            DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_NOTIF_ENABLED,
            DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_NOTIF_ENABLED, DataStoreUtils.KEY_REWIND_BGM_ENABLED,
        )
        val prefs = DataStoreUtils.prefs(context)
        val editor = prefs.edit()
        flags.forEach { editor.putBoolean(it, false) }
        editor.putInt(DataStoreUtils.KEY_REWIND_BGM_VOLUME, 2)
            .putBoolean(DataStoreUtils.KEY_ONBOARDING_COMPLETE, true)
            .putString(DataStoreUtils.KEY_ONBOARDING_STEP, "DONE")
            .putString(DataStoreUtils.KEY_ONBOARDING_PERMISSIONS_REQUESTED, "android.permission.RECORD_AUDIO")
            .putBoolean(DataStoreUtils.KEY_SAMSUNG_STORAGE_ADVICE_SHOWN, true)
            .putLong(DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED, 202610)
            .putLong(DataStoreUtils.KEY_REWIND_PLAYLIST_LAST_CONSUMED, 81)
            .commit()

        resetAdditionalSettingsDefaults(context) {}
        resetAdditionalSettingsDefaults(context) {}

        flags.forEach { assertTrue(DataStoreUtils.getBoolean(context, it, true)) }
        assertEquals(70, DataStoreUtils.getInt(context, DataStoreUtils.KEY_REWIND_BGM_VOLUME, 70))
        assertTrue(prefs.getBoolean(DataStoreUtils.KEY_ONBOARDING_COMPLETE, false))
        assertEquals("DONE", prefs.getString(DataStoreUtils.KEY_ONBOARDING_STEP, null))
        assertEquals("android.permission.RECORD_AUDIO", prefs.getString(DataStoreUtils.KEY_ONBOARDING_PERMISSIONS_REQUESTED, null))
        assertTrue(prefs.getBoolean(DataStoreUtils.KEY_SAMSUNG_STORAGE_ADVICE_SHOWN, false))
        assertEquals(202610L, prefs.getLong(DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED, 0))
        assertEquals(81L, prefs.getLong(DataStoreUtils.KEY_REWIND_PLAYLIST_LAST_CONSUMED, 0))
    }

    @Test fun resetRestoresSyncPrivacyAndBackupControlsWithoutTouchingDestinationProfileOrJournal() {
        context.preferences.edit()
            .putString("syncDirection", "PUSH_ONLY")
            .putInt("syncCooldown", 90)
            .putBoolean("syncShowDetails", false)
            .putBoolean("syncBackgroundGuard", false)
            .putBoolean("syncPushSongLike", true)
            .putBoolean("syncImportEpisodes", true)
            .putBoolean("useLoginForSearch", false)
            .putBoolean("useLoginForBrowse", false)
            .putBoolean("privacyBlockScreenCapture", true)
            .putBoolean("permissionUsageJournalPaused", true)
            .putString("permissionUsageJournalEvents", "saved-journal")
            .putInt(BackupManager.PREF_INTERVAL, BackupManager.INTERVAL_DAILY)
            .putInt(BackupManager.PREF_MAX_BACKUPS, 15)
            .putBoolean(BackupManager.PREF_PRE_INSTALL, true)
            .putString(BackupManager.PREF_URI, "content://backup-folder")
            .putString("syncSessionCursor", "saved-cursor")
            .commit()
        val principal = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        principal.edit().putBoolean("privacyBlockScreenCapture", true)
            .putInt(BackupManager.PREF_INTERVAL, BackupManager.INTERVAL_DAILY)
            .putBoolean(BackupManager.PREF_DB_CHANGE, true)
            .putString(BackupManager.PREF_URI, "content://shared-backup-folder").commit()
        var cancellations = 0

        resetAdditionalSettingsDefaults(context) { cancellations++ }

        assertEquals(1, cancellations)
        val prefs = context.preferences
        assertEquals("TWO_WAY", prefs.getString("syncDirection", "TWO_WAY"))
        assertEquals(30, prefs.getInt("syncCooldown", 30))
        assertTrue(prefs.getBoolean("syncShowDetails", true))
        assertTrue(prefs.getBoolean("syncBackgroundGuard", true))
        assertFalse(prefs.getBoolean("syncPushSongLike", false))
        assertFalse(prefs.getBoolean("syncImportEpisodes", false))
        assertTrue(prefs.getBoolean("useLoginForSearch", true))
        assertTrue(prefs.getBoolean("useLoginForBrowse", true))
        assertFalse(prefs.getBoolean("privacyBlockScreenCapture", false))
        assertFalse(prefs.getBoolean("permissionUsageJournalPaused", false))
        assertEquals(BackupManager.INTERVAL_NONE, prefs.getInt(BackupManager.PREF_INTERVAL, BackupManager.INTERVAL_NONE))
        assertEquals(5, prefs.getInt(BackupManager.PREF_MAX_BACKUPS, 5))
        assertFalse(prefs.getBoolean(BackupManager.PREF_PRE_INSTALL, false))
        assertEquals("content://backup-folder", prefs.getString(BackupManager.PREF_URI, null))
        assertEquals("saved-journal", prefs.getString("permissionUsageJournalEvents", null))
        assertEquals("saved-cursor", prefs.getString("syncSessionCursor", null))
        assertTrue(principal.getBoolean("privacyBlockScreenCapture", false))
        assertFalse(principal.contains(BackupManager.PREF_INTERVAL))
        assertFalse(principal.contains(BackupManager.PREF_DB_CHANGE))
        assertEquals("content://shared-backup-folder", principal.getString(BackupManager.PREF_URI, null))
    }

    @Test fun resetRemovesUpdaterOverridesWithoutChangingSessionOrOtherProfiles() {
        val prefs = context.preferences
        prefs.edit().putString(checkUpdateStateKey, "Disabled")
            .putBoolean(checkBetaUpdatesKey, true)
            .putString(otherLanguageAppUpdateKey, "French")
            .putBoolean("updateTranslationActive", false).commit()
        val otherProfile = context.getSharedPreferences("preferences_other-profile", Context.MODE_PRIVATE)
        otherProfile.edit().putBoolean(checkBetaUpdatesKey, true).commit()
        resetAdditionalSettingsDefaults(context) {}
        listOf(checkUpdateStateKey, checkBetaUpdatesKey, otherLanguageAppUpdateKey, "updateTranslationActive")
            .forEach { assertFalse("Updater must return to its read default: $it", prefs.contains(it)) }
        assertTrue(otherProfile.getBoolean(checkBetaUpdatesKey, false))
    }

    @Test fun resetRestoresDiscordAndLastFmBehaviorWhilePreservingAllAccountSecretsAndIdentity() {
        secure.edit()
            .putBoolean("isDiscordAdvancedMode", true)
            .putBoolean("discordAdvancedShowArtwork", false)
            .putBoolean("discordAdvancedIdleCloseEnabled", false)
            .putInt("discordAdvancedRefreshIntervalMs", 17000)
            .putString("discordAdvancedLargeImageText", "custom")
            .putBoolean("isLastfmScrobblingEnabled", true)
            .putBoolean("isLastfmNowPlayingEnabled", false)
            .putInt("lastfmMinTrackDurationSeconds", 60)
            .putBoolean("enableYoutubeLogin", true)
            .putBoolean("enableYoutubeSync", true)
            .putString("ytCookie", "saved-cookie")
            .putString("ytDataSyncId", "saved-sync-id")
            .putString("ytAccountName", "saved-account")
            .putString("discordPersonalAccessToken", "saved-token")
            .putString("discordUsername", "saved-discord")
            .putString("lastfmSessionKey", "saved-session")
            .putString("lastfmUsername", "saved-lastfm")
            .putString("apiKey", "saved-key")
            .commit()

        resetAdditionalSettingsDefaults(context) {}

        assertEquals(DiscordAdvancedSettings.DEFAULTS, DiscordAdvancedSettings.read(secure))
        assertFalse(secure.getBoolean("isLastfmScrobblingEnabled", false))
        assertTrue(secure.getBoolean("isLastfmNowPlayingEnabled", true))
        assertEquals(30, secure.getInt("lastfmMinTrackDurationSeconds", 30))
        assertTrue(secure.getBoolean("enableYoutubeLogin", false))
        assertTrue(secure.getBoolean("enableYoutubeSync", false))
        assertEquals("saved-cookie", secure.getString("ytCookie", null))
        assertEquals("saved-sync-id", secure.getString("ytDataSyncId", null))
        assertEquals("saved-account", secure.getString("ytAccountName", null))
        assertEquals("saved-token", secure.getString("discordPersonalAccessToken", null))
        assertEquals("saved-discord", secure.getString("discordUsername", null))
        assertEquals("saved-session", secure.getString("lastfmSessionKey", null))
        assertEquals("saved-lastfm", secure.getString("lastfmUsername", null))
        assertEquals("saved-key", secure.getString("apiKey", null))
    }
}
