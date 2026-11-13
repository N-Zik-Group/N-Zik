package app.n_zik.android.components.settings.accounts

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.enums.SyncDirection
import app.n_zik.android.utils.SyncOperation
import app.n_zik.android.utils.SyncStatus
import app.n_zik.android.utils.autosyncAlbumsKey
import app.n_zik.android.utils.autosyncArtistsKey
import app.n_zik.android.utils.autosyncLikesKey
import app.n_zik.android.utils.autosyncPlaylistsKey
import app.n_zik.android.utils.getLastSyncTime
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.queueSync
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.syncBackgroundGuardKey
import app.n_zik.android.utils.syncCooldownKey
import app.n_zik.android.utils.syncDirectionKey
import app.n_zik.android.utils.syncImportEpisodesKey
import app.n_zik.android.utils.syncImportHistoryKey
import app.n_zik.android.utils.syncImportLibrarySongsKey
import app.n_zik.android.utils.syncImportUploadedAlbumsKey
import app.n_zik.android.utils.syncImportUploadedSongsKey
import app.n_zik.android.utils.syncPushAlbumBookmarkKey
import app.n_zik.android.utils.syncPushArtistFollowKey
import app.n_zik.android.utils.syncPushEpisodeKey
import app.n_zik.android.utils.syncPushHistoryKey
import app.n_zik.android.utils.syncPushPlaylistKey
import app.n_zik.android.utils.syncPushSongLikeKey
import app.n_zik.android.utils.syncShowDetailsKey
import app.n_zik.android.utils.syncStatus
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.components.dialog.settings.SyncStatusDialog
import app.n_zik.android.components.settings.common.OtherEnumValueSelectorSettingsEntry
import app.n_zik.android.components.settings.common.OtherInfoSettingsEntry
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.SliderSettingsEntry
import app.n_zik.android.utils.coroutines.NzikDispatchers

/** Owns the SyncOverviewCard controls. */
@Composable
internal fun AccountsSyncOverviewCard(isYouTubeSyncEnabled: Boolean, isSyncEnabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    var syncDirection by rememberPreference(syncDirectionKey, SyncDirection.TWO_WAY)
    val syncStatusState by syncStatus.collectAsStateWithLifecycle(initialValue = SyncStatus(), context = NzikDispatchers.DATA)
    SettingsSectionCard(
        title = stringResource(R.string.sync_data_with_ytm_account),
        icon = R.drawable.sync,
        content = {
            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("accounts.sync_data_with_ytm_account.sync_data_with_ytm_account"),
                title = stringResource(R.string.sync_data_with_ytm_account),
                text = stringResource(R.string.settings_youtube_library_sync_content_description),
                isChecked = isYouTubeSyncEnabled,
                enabled = BuildConfig.BUILD_TYPE in listOf("debug", "dev64", "dev32", "beta64", "beta32"),
                onCheckedChange = {
                    onEnabledChange(it)
                    if (!it) {
                        // Reset all sub-toggles when sync is disabled
                        appContext().preferences.edit {
                            putBoolean(autosyncArtistsKey, false)
                            putBoolean(autosyncAlbumsKey, false)
                            putBoolean(autosyncPlaylistsKey, false)
                            putBoolean(autosyncLikesKey, false)
                            putBoolean(syncImportHistoryKey, false)
                            putBoolean(syncImportLibrarySongsKey, false)
                            putBoolean(syncImportUploadedSongsKey, false)
                            putBoolean(syncImportUploadedAlbumsKey, false)
                            putBoolean(syncImportEpisodesKey, false)
                            putBoolean(syncPushHistoryKey, false)
                            putBoolean(syncPushSongLikeKey, false)
                            putBoolean(syncPushAlbumBookmarkKey, false)
                            putBoolean(syncPushArtistFollowKey, false)
                            putBoolean(syncPushPlaylistKey, false)
                            putBoolean(syncPushEpisodeKey, false)
                        }
                    }
                },
                icon = R.drawable.sync
            )

            AnimatedVisibility(visible = isSyncEnabled) {
                Column {
                    // Sync status overview
                    val statusText = when {
                        syncStatusState.isRunning -> stringResource(R.string.sync_status_running, syncStatusState.currentOperation)
                        else -> {
                            val lastSync = getLastSyncTime()
                            if (lastSync > 0) {
                                val elapsed = ((System.currentTimeMillis() - lastSync) / 60000).toInt()
                                stringResource(R.string.sync_status_last, elapsed)
                            } else stringResource(R.string.sync_status_never)
                        }
                    }
                    OtherInfoSettingsEntry(
                        title = statusText,
                        text = "",
                        icon = R.drawable.sync
                    )

                    // Sync status detail
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("accounts.sync_data_with_ytm_account.sync_status_detail"),
                        title = stringResource(R.string.sync_status_detail),
                        text = stringResource(R.string.sync_status_detail_description),
                        icon = R.drawable.information,
                        onClick = { SyncStatusDialog.showDialog() }
                    )
                    SyncStatusDialog.Render()

                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("accounts.sync_data_with_ytm_account.sync_now"),
                        title = stringResource(R.string.sync_now),
                        text = stringResource(R.string.sync_now_description),
                        icon = R.drawable.sync,
                        onClick = {
                            // Queue all imports + pushes via sync queue (forced)
                            queueSync(SyncOperation.FullSync)
                            queueSync(SyncOperation.PushLikedSongs(force = true))
                            queueSync(SyncOperation.PushAlbumBookmarks(force = true))
                            queueSync(SyncOperation.PushArtistFollows(force = true))
                            queueSync(SyncOperation.PushPlaylists(force = true))
                            queueSync(SyncOperation.PushEpisodes(force = true))
                        }
                    )
                    OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.sync_direction_description), stringResource(syncDirection.stringResource)),
                        modifier = Modifier.settingsSearchTarget("accounts.sync_data_with_ytm_account.sync_direction"),
                        icon = R.drawable.sync,
                        title = stringResource(R.string.sync_direction),
                        selectedValue = syncDirection,
                        onValueSelected = { syncDirection = it },
                        valueText = { stringResource(it.stringResource) }
                    )

                    var syncCooldown by rememberPreference(syncCooldownKey, 30)
                    SliderSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("accounts.sync_data_with_ytm_account.sync_cooldown"),
                        title = stringResource(R.string.sync_cooldown),
                        text = stringResource(R.string.sync_cooldown_description),
                        state = syncCooldown.toFloat(),
                        range = 5f..120f,
                        stepSize = 15f,
                        onSlide = { syncCooldown = it.toInt() },
                        toDisplay = { stringResource(R.string.common_duration_minutes_short, it.toInt()) },
                        isIntegerOnly = true,
                        icon = R.drawable.time
                    )
                    var syncShowDetails by rememberPreference(syncShowDetailsKey, true)
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("accounts.sync_data_with_ytm_account.sync_show_details"),
                        title = stringResource(R.string.sync_show_details),
                        text = stringResource(R.string.sync_show_details_description),
                        isChecked = syncShowDetails,
                        onCheckedChange = { syncShowDetails = it },
                        icon = R.drawable.information
                    )
                    var syncBackgroundGuard by rememberPreference(syncBackgroundGuardKey, true)
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("accounts.sync_data_with_ytm_account.sync_background_guard"),
                        title = stringResource(R.string.sync_background_guard),
                        text = stringResource(R.string.sync_background_guard_description),
                        isChecked = syncBackgroundGuard,
                        onCheckedChange = { syncBackgroundGuard = it },
                        icon = R.drawable.pause
                    )
                }
            }
        }
    )
}
