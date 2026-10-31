package app.n_zik.android.components.settings.common

import android.content.Context
import android.content.SharedPreferences
import app.it.fast4x.rimusic.utils.checkUpdateStateKey
import app.it.fast4x.rimusic.utils.checkBetaUpdatesKey
import app.it.fast4x.rimusic.utils.otherLanguageAppUpdateKey
import app.it.fast4x.rimusic.utils.autosyncAlbumsKey
import app.it.fast4x.rimusic.utils.autosyncArtistsKey
import app.it.fast4x.rimusic.utils.autosyncKey
import app.it.fast4x.rimusic.utils.autosyncLikesKey
import app.it.fast4x.rimusic.utils.autosyncPlaylistsKey
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.syncBackgroundGuardKey
import app.it.fast4x.rimusic.utils.syncCooldownKey
import app.it.fast4x.rimusic.utils.syncDirectionKey
import app.it.fast4x.rimusic.utils.syncImportEpisodesKey
import app.it.fast4x.rimusic.utils.syncImportHistoryKey
import app.it.fast4x.rimusic.utils.syncImportLibrarySongsKey
import app.it.fast4x.rimusic.utils.syncImportUploadedAlbumsKey
import app.it.fast4x.rimusic.utils.syncImportUploadedSongsKey
import app.it.fast4x.rimusic.utils.syncPushAlbumBookmarkKey
import app.it.fast4x.rimusic.utils.syncPushArtistFollowKey
import app.it.fast4x.rimusic.utils.syncPushEpisodeKey
import app.it.fast4x.rimusic.utils.syncPushHistoryKey
import app.it.fast4x.rimusic.utils.syncPushPlaylistKey
import app.it.fast4x.rimusic.utils.syncPushSongLikeKey
import app.it.fast4x.rimusic.utils.syncShowDetailsKey
import app.it.fast4x.rimusic.utils.useLoginForBrowseKey
import app.n_zik.android.components.settings.useLoginForSearchKey
import app.n_zik.android.core.backup.BackupManager
import app.n_zik.android.extensions.discord.discordAdvancedSettingKeys
import app.n_zik.android.extensions.lastfm.isLastfmNowPlayingEnabledKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobbleEnabledKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobblingEnabledKey
import app.n_zik.android.extensions.lastfm.lastfmMaxScrobbleDelaySecondsKey
import app.n_zik.android.extensions.lastfm.lastfmMinTrackDurationSecondsKey
import app.n_zik.android.extensions.lastfm.lastfmScrobbleThresholdPercentKey
import app.n_zik.android.privacy.PermissionUsageJournal
import app.n_zik.android.privacy.blockScreenCaptureKey
import app.n_zik.android.utils.DataStoreUtils

// AccountsSync*Card, YtSearchAccountToggle and LocalPlaylistSongs define these defaults.
// Session credentials, sync cursors and account identity are intentionally absent.
private val additionalProfileSettingKeys = setOf(
    autosyncKey, autosyncArtistsKey, autosyncAlbumsKey, autosyncPlaylistsKey, autosyncLikesKey,
    syncDirectionKey, syncCooldownKey, syncShowDetailsKey, syncBackgroundGuardKey,
    syncPushSongLikeKey, syncPushAlbumBookmarkKey, syncPushArtistFollowKey,
    syncPushPlaylistKey, syncPushHistoryKey, syncPushEpisodeKey,
    syncImportHistoryKey, syncImportLibrarySongsKey, syncImportUploadedSongsKey,
    syncImportUploadedAlbumsKey, syncImportEpisodesKey, useLoginForBrowseKey, useLoginForSearchKey,
    blockScreenCaptureKey, PermissionUsageJournal.PAUSED_KEY, DEBUG_UPDATER_ACCESS_KEY,
    checkUpdateStateKey, checkBetaUpdatesKey, otherLanguageAppUpdateKey, "updateTranslationActive",
)

// AutoBackupSettingsBlock is the canonical reader. Keep PREF_URI and the SAF grant:
// resetting the schedule must not disconnect a chosen destination or delete a backup.
private val additionalBackupSettingKeys = setOf(
    BackupManager.PREF_INTERVAL, BackupManager.PREF_DB_CHANGE, BackupManager.PREF_MAX_BACKUPS,
    BackupManager.PREF_CUSTOM_INTERVAL_VALUE, BackupManager.PREF_CUSTOM_INTERVAL_UNIT,
    BackupManager.PREF_SCHEDULE_HOUR, BackupManager.PREF_SCHEDULE_DAY_OF_WEEK,
    BackupManager.PREF_SCHEDULE_DAY_OF_MONTH, BackupManager.PREF_TARGET,
    BackupManager.PREF_INCLUDE_YTB, BackupManager.PREF_INCLUDE_DISCORD,
    BackupManager.PREF_INCLUDE_LASTFM, BackupManager.PREF_INCLUDE_PROXY, BackupManager.PREF_PRE_INSTALL,
)

// LastFmSettingsCard owns behavior; Last.fm session, username and avatar remain untouched.
private val additionalSecureSettingKeys = discordAdvancedSettingKeys + setOf(
    isLastfmScrobblingEnabledKey, isLastfmNowPlayingEnabledKey, isLastfmScrobbleEnabledKey,
    lastfmMinTrackDurationSecondsKey, lastfmScrobbleThresholdPercentKey, lastfmMaxScrobbleDelaySecondsKey,
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

/** Restores controls outside the historical category resets using their first-run read defaults. */
internal fun resetAdditionalSettingsDefaults(
    context: Context,
    cancelAutoBackup: () -> Unit = { BackupManager.scheduleBackup(context, BackupManager.INTERVAL_NONE) },
) {
    context.preferences.removeSettings(additionalProfileSettingKeys + additionalBackupSettingKeys)
    // The scheduler/worker own device-wide backup options in the principal store.
    // Clear only those options as well; keep its destination and every unrelated profile setting.
    context.getSharedPreferences("preferences", Context.MODE_PRIVATE).removeSettings(additionalBackupSettingKeys)
    context.encryptedPreferences.removeSettings(additionalSecureSettingKeys)
    DataStoreUtils.prefs(context).removeSettings(additionalGlobalSettingKeys)
    cancelAutoBackup()
}

private fun SharedPreferences.removeSettings(keys: Set<String>) {
    val editor = edit()
    keys.forEach { key -> editor.remove(key) }
    editor.apply()
}
