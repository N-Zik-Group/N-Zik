package app.n_zik.android.components.settings.common

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.utils.checkUpdateStateKey
import app.n_zik.android.utils.checkBetaUpdatesKey
import app.n_zik.android.utils.otherLanguageAppUpdateKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.playbackDurationKey
import app.n_zik.android.utils.playbackPitchKey
import app.n_zik.android.utils.playbackSpeedKey
import app.n_zik.android.utils.playbackVolumeKey
import app.n_zik.android.utils.playbackDeviceVolumeKey
import app.n_zik.android.utils.playerBackdropKey
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.listentogether.ListenTogetherClient
import app.n_zik.android.listentogether.ListenTogetherManager
import app.n_zik.android.utils.DataStoreUtils
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

    @Before fun setUp() {
        setActiveProfile("extra-reset", context)
        context.preferences.edit().clear().commit()
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().clear().commit()
        DataStoreUtils.prefs(context).edit().clear().commit()
    }

    @After fun tearDown() {
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

        resetAdditionalSettingsDefaults(context)
        resetAdditionalSettingsDefaults(context)

        flags.forEach { assertTrue(DataStoreUtils.getBoolean(context, it, true)) }
        assertEquals(70, DataStoreUtils.getInt(context, DataStoreUtils.KEY_REWIND_BGM_VOLUME, 70))
        assertTrue(prefs.getBoolean(DataStoreUtils.KEY_ONBOARDING_COMPLETE, false))
        assertEquals("DONE", prefs.getString(DataStoreUtils.KEY_ONBOARDING_STEP, null))
        assertEquals("android.permission.RECORD_AUDIO", prefs.getString(DataStoreUtils.KEY_ONBOARDING_PERMISSIONS_REQUESTED, null))
        assertTrue(prefs.getBoolean(DataStoreUtils.KEY_SAMSUNG_STORAGE_ADVICE_SHOWN, false))
        assertEquals(202610L, prefs.getLong(DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED, 0))
        assertEquals(81L, prefs.getLong(DataStoreUtils.KEY_REWIND_PLAYLIST_LAST_CONSUMED, 0))
    }

    @Test fun resetRestoresListenTogetherControlsAndKeepsAccountData() {
        val prefs = DataStoreUtils.prefs(context)
        prefs.edit()
            .putBoolean(ListenTogetherClient.PREF_AUTO_APPROVAL, true)
            .putBoolean(ListenTogetherClient.PREF_AUTO_APPROVE_SUGGESTIONS, true)
            .putBoolean(ListenTogetherClient.PREF_HISTORY, true)
            .putBoolean(ListenTogetherManager.PREF_SYNC_HOST_VOLUME, false)
            .putString(ListenTogetherClient.PREF_SESSION_TOKEN, "saved-session")
            .putString(ListenTogetherClient.PREF_SERVER_URL, "https://room.example")
            .putString(ListenTogetherClient.PREF_BLOCKED_USERS, "saved-blocklist")
            .putBoolean(DataStoreUtils.KEY_REWIND_ENABLED, false)
            .commit()

        resetListenTogetherSettingsDefaults(context)

        assertFalse(prefs.getBoolean(ListenTogetherClient.PREF_AUTO_APPROVAL, false))
        assertFalse(prefs.getBoolean(ListenTogetherClient.PREF_AUTO_APPROVE_SUGGESTIONS, false))
        assertFalse(prefs.getBoolean(ListenTogetherClient.PREF_HISTORY, false))
        assertTrue(prefs.getBoolean(ListenTogetherManager.PREF_SYNC_HOST_VOLUME, true))
        assertEquals("saved-session", prefs.getString(ListenTogetherClient.PREF_SESSION_TOKEN, null))
        assertEquals("https://room.example", prefs.getString(ListenTogetherClient.PREF_SERVER_URL, null))
        assertEquals("saved-blocklist", prefs.getString(ListenTogetherClient.PREF_BLOCKED_USERS, null))
        assertFalse(prefs.getBoolean(DataStoreUtils.KEY_REWIND_ENABLED, true))
    }

    @Test fun resetRestoresPrivacyControlsWithoutTouchingOtherProfilesOrJournal() {
        val prefs = context.preferences
        prefs.edit()
            .putBoolean("privacyBlockScreenCapture", true)
            .putBoolean("permissionUsageJournalPaused", true)
            .putString("permissionUsageJournalEvents", "saved-journal")
            .commit()
        val principal = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        principal.edit().putBoolean("privacyBlockScreenCapture", true).commit()

        resetAdditionalSettingsDefaults(context)

        assertFalse(prefs.getBoolean("privacyBlockScreenCapture", false))
        assertFalse(prefs.getBoolean("permissionUsageJournalPaused", false))
        assertEquals("saved-journal", prefs.getString("permissionUsageJournalEvents", null))
        assertTrue(principal.getBoolean("privacyBlockScreenCapture", false))
    }

    @Test fun resetRemovesUpdaterOverridesWithoutChangingSessionOrOtherProfiles() {
        val prefs = context.preferences
        prefs.edit().putString(checkUpdateStateKey, "Disabled")
            .putBoolean(checkBetaUpdatesKey, true)
            .putString(otherLanguageAppUpdateKey, "French")
            .putBoolean("updateTranslationActive", false).commit()
        val otherProfile = context.getSharedPreferences("preferences_other-profile", Context.MODE_PRIVATE)
        otherProfile.edit().putBoolean(checkBetaUpdatesKey, true).commit()
        resetAdditionalSettingsDefaults(context)
        listOf(checkUpdateStateKey, checkBetaUpdatesKey, otherLanguageAppUpdateKey, "updateTranslationActive")
            .forEach { assertFalse("Updater must return to its read default: $it", prefs.contains(it)) }
        assertTrue(otherProfile.getBoolean(checkBetaUpdatesKey, false))
    }

    @Test fun resetRestoresPlayerControlsWithoutChangingDeviceVolumeOrOtherProfiles() {
        val defaults = mapOf(playbackSpeedKey to 1f, playbackPitchKey to 1f,
            playbackVolumeKey to 1f, playbackDurationKey to 0f, playerBackdropKey to 0f)
        val prefs = context.preferences
        val principal = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val other = context.getSharedPreferences("preferences_player-reset-other", Context.MODE_PRIVATE)
        listOf(prefs, principal, other).forEach { store ->
            store.edit().apply { defaults.keys.forEach { putFloat(it, .7f) } }.commit()
        }
        prefs.edit().putFloat(playbackDeviceVolumeKey, .4f).putString("queue", "saved queue").commit()

        repeat(2) { resetAdditionalSettingsDefaults(context) }

        defaults.forEach { (key, default) ->
            assertFalse("Return to the player reader's initial default: $key", prefs.contains(key))
            assertEquals(default, prefs.getFloat(key, default), 0f)
            assertEquals(.7f, principal.getFloat(key, -1f), 0f)
            assertEquals(.7f, other.getFloat(key, -1f), 0f)
        }
        assertEquals(.4f, prefs.getFloat(playbackDeviceVolumeKey, -1f), 0f)
        assertEquals("saved queue", prefs.getString("queue", null))
    }

}
