package app.n_zik.android.components.settings.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.it.fast4x.rimusic.enums.SyncDirection
import app.it.fast4x.rimusic.utils.SyncOperation
import app.it.fast4x.rimusic.utils.SyncStatus
import app.it.fast4x.rimusic.utils.autosyncAlbumsKey
import app.it.fast4x.rimusic.utils.autosyncArtistsKey
import app.it.fast4x.rimusic.utils.autosyncLikesKey
import app.it.fast4x.rimusic.utils.autosyncPlaylistsKey
import app.it.fast4x.rimusic.utils.getLastSyncTime
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.queueSync
import app.it.fast4x.rimusic.utils.rememberPreference
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
import app.it.fast4x.rimusic.utils.syncStatus
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
                title = stringResource(R.string.sync_data_with_ytm_account),
                text = stringResource(R.string.playlists_albums_artists_history_like_etc),
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
                        title = stringResource(R.string.sync_status_detail),
                        text = stringResource(R.string.sync_status_detail_description),
                        icon = R.drawable.information,
                        onClick = { SyncStatusDialog.showDialog() }
                    )
                    SyncStatusDialog.Render()

                    OtherSettingsEntry(
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
                        icon = R.drawable.sync,
                        title = stringResource(R.string.sync_direction),
                        selectedValue = syncDirection,
                        onValueSelected = { syncDirection = it },
                        valueText = { stringResource(it.stringResource) }
                    )

                    var syncCooldown by rememberPreference(syncCooldownKey, 30)
                    SliderSettingsEntry(
                        title = stringResource(R.string.sync_cooldown),
                        text = stringResource(R.string.sync_cooldown_description),
                        state = syncCooldown.toFloat(),
                        range = 5f..120f,
                        stepSize = 15f,
                        onSlide = { syncCooldown = it.toInt() },
                        toDisplay = { "${it.toInt()} min" },
                        isIntegerOnly = true,
                        icon = R.drawable.time
                    )
                    var syncShowDetails by rememberPreference(syncShowDetailsKey, true)
                    OtherSwitchSettingEntry(
                        title = stringResource(R.string.sync_show_details),
                        text = stringResource(R.string.sync_show_details_description),
                        isChecked = syncShowDetails,
                        onCheckedChange = { syncShowDetails = it },
                        icon = R.drawable.information
                    )
                    var syncBackgroundGuard by rememberPreference(syncBackgroundGuardKey, true)
                    OtherSwitchSettingEntry(
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
