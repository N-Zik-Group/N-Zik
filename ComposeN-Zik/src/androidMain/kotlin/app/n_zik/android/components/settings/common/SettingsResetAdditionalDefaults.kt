package app.n_zik.android.components.settings.common

import android.content.Context
import android.content.SharedPreferences
import app.n_zik.android.utils.checkUpdateStateKey
import app.n_zik.android.utils.checkBetaUpdatesKey
import app.n_zik.android.utils.otherLanguageAppUpdateKey
import app.n_zik.android.utils.playbackDurationKey
import app.n_zik.android.utils.playbackPitchKey
import app.n_zik.android.utils.playbackSpeedKey
import app.n_zik.android.utils.playbackVolumeKey
import app.n_zik.android.utils.playerBackdropKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.listentogether.ListenTogetherClient
import app.n_zik.android.listentogether.ListenTogetherManager
import app.n_zik.android.core.rewind.scheduleRewindWork
import app.n_zik.android.privacy.PermissionUsageJournal
import app.n_zik.android.privacy.blockScreenCaptureKey
import app.n_zik.android.utils.DataStoreUtils

private val additionalProfileSettingKeys = setOf(
    blockScreenCaptureKey, PermissionUsageJournal.PAUSED_KEY,
    checkUpdateStateKey, checkBetaUpdatesKey, otherLanguageAppUpdateKey, "updateTranslationActive",
    // Player menu controls are profile settings; leave the device's physical volume alone.
    playbackSpeedKey, playbackPitchKey, playbackVolumeKey, playbackDurationKey, playerBackdropKey,
)

// DataStoreUtils declares first-run read defaults for these controls. Deep-link consumption
// markers and onboarding flags in the same global store are user/session state, not settings.
private val additionalGlobalSettingKeys = setOf(
    DataStoreUtils.KEY_REWIND_ENABLED, DataStoreUtils.KEY_REWIND_YEARLY_ENABLED,
    DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, DataStoreUtils.KEY_REWIND_GLOBAL_ENABLED,
    DataStoreUtils.KEY_REWIND_MONTHLY_NOTIF_ENABLED, DataStoreUtils.KEY_REWIND_YEARLY_NOTIF_ENABLED,
    DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED,
    DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_NOTIF_ENABLED,
    DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_NOTIF_ENABLED,
    DataStoreUtils.KEY_REWIND_BGM_ENABLED, DataStoreUtils.KEY_REWIND_BGM_VOLUME,
)

private val listenTogetherSettingKeys = setOf(
    ListenTogetherClient.PREF_AUTO_APPROVAL, ListenTogetherClient.PREF_AUTO_APPROVE_SUGGESTIONS,
    ListenTogetherClient.PREF_HISTORY, ListenTogetherManager.PREF_SYNC_HOST_VOLUME,
)

/** Restores controls outside the historical category resets using their first-run read defaults. */
internal fun resetAdditionalSettingsDefaults(context: Context) {
    context.preferences.removeSettings(additionalProfileSettingKeys)
    DataStoreUtils.prefs(context).removeSettings(additionalGlobalSettingKeys)
    scheduleRewindWork(context)
    resetListenTogetherSettingsDefaults(context)
}

/** Restores device-wide room controls while preserving the server, session and blocklist. */
internal fun resetListenTogetherSettingsDefaults(context: Context) {
    DataStoreUtils.prefs(context).removeSettings(listenTogetherSettingKeys)
    ListenTogetherManager.getInstance()?.refreshSyncHostVolumePreference()
}

private fun SharedPreferences.removeSettings(keys: Set<String>) {
    val editor = edit()
    keys.forEach { key -> editor.remove(key) }
    editor.apply()
}
