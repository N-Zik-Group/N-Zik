package app.n_zik.android.components.settings.general

import android.text.TextUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.DislikeMode
import app.it.fast4x.rimusic.enums.DurationInMinutes
import app.it.fast4x.rimusic.enums.ExoPlayerMinTimeForEvent
import app.it.fast4x.rimusic.enums.PauseBetweenSongs
import app.it.fast4x.rimusic.utils.crossfadeDurationKey
import app.it.fast4x.rimusic.utils.crossfadeEnabledKey
import app.it.fast4x.rimusic.utils.crossfadeGaplessKey
import app.it.fast4x.rimusic.utils.excludeDislikedAlbumsKey
import app.it.fast4x.rimusic.utils.excludeDislikedArtistsKey
import app.it.fast4x.rimusic.utils.excludeDislikedSongsKey
import app.it.fast4x.rimusic.utils.excludeSongsWithDurationLimitKey
import app.it.fast4x.rimusic.utils.exoPlayerMinTimeForEventKey
import app.it.fast4x.rimusic.utils.isAtLeastAndroid6
import app.it.fast4x.rimusic.utils.isPauseOnVolumeZeroEnabledKey
import app.it.fast4x.rimusic.utils.jumpPreviousKey
import app.it.fast4x.rimusic.utils.pauseBetweenSongsKey
import app.it.fast4x.rimusic.utils.pauseOnHeadphoneDisconnectKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.resumePlaybackWhenDeviceConnectedKey
import app.it.fast4x.rimusic.utils.skipMediaOnErrorKey
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.dialog.settings.SettingsInputDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsSearchTarget
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch

/** Playback card: automatic resume/pause, error handling, exclusions and transitions. */
@Composable
fun GeneralPlaybackCard(
    visible: Boolean,
    onRestartServiceSet: () -> Unit,
) {
    var resumePlaybackWhenDeviceConnected by rememberPreference(
        resumePlaybackWhenDeviceConnectedKey,
        false
    )
    var isPauseOnVolumeZeroEnabled by rememberPreference(isPauseOnVolumeZeroEnabledKey, false)
    var pauseOnHeadphoneDisconnect by rememberPreference(pauseOnHeadphoneDisconnectKey, true)
    var skipMediaOnError by rememberPreference(skipMediaOnErrorKey, false)
    var exoPlayerMinTimeForEvent by rememberPreference(
        exoPlayerMinTimeForEventKey,
        ExoPlayerMinTimeForEvent.`20s`
    )
    var jumpPrevious by rememberPreference(jumpPreviousKey, "3")
    var excludeSongWithDurationLimit by rememberPreference(excludeSongsWithDurationLimitKey, DurationInMinutes.Disabled)
    var excludeDislikedSongs by rememberPreference(excludeDislikedSongsKey, DislikeMode.Enabled)
    var excludeDislikedArtists by rememberPreference(excludeDislikedArtistsKey, DislikeMode.Enabled)
    var excludeDislikedAlbums by rememberPreference(excludeDislikedAlbumsKey, DislikeMode.Enabled)
    var pauseBetweenSongs by rememberPreference(pauseBetweenSongsKey, PauseBetweenSongs.`0`)
    var crossfadeEnabled by rememberPreference(crossfadeEnabledKey, false)
    var crossfadeDuration by rememberPreference(crossfadeDurationKey, 3000)
    var crossfadeGapless by rememberPreference(crossfadeGaplessKey, false)

    var showJumpPreviousDialog by remember { mutableStateOf(false) }
    var showMinListeningTimeDialog by remember { mutableStateOf(false) }
    var showExcludeSongsDialog by remember { mutableStateOf(false) }
    var showDislikeSongsDialog by remember { mutableStateOf(false) }
    var showDislikeArtistsDialog by remember { mutableStateOf(false) }
    var showDislikeAlbumsDialog by remember { mutableStateOf(false) }
    var showPauseBetweenSongsDialog by remember { mutableStateOf(false) }

    SettingsSectionCard(
        title = stringResource(R.string.playback),
        icon = R.drawable.play_forward,
        visible = visible,
        content = {
            if (isAtLeastAndroid6) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("general.app_behavior.resume_playback"),
                    title = stringResource(R.string.resume_playback),
                    text = stringResource(R.string.when_device_is_connected),
                    isChecked = resumePlaybackWhenDeviceConnected,
                    onCheckedChange = {
                        resumePlaybackWhenDeviceConnected = it
                        onRestartServiceSet()
                    },
                    icon = R.drawable.play
                )
            }

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("general.player_controls.player_pause_on_volume_zero"),
                title = stringResource(R.string.player_pause_on_volume_zero),
                text = stringResource(R.string.info_pauses_player_when_volume_zero),
                isChecked = isPauseOnVolumeZeroEnabled,
                onCheckedChange = { isPauseOnVolumeZeroEnabled = it },
                icon = R.drawable.volume_up
            )

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("general.player_controls.pause_on_headphone_disconnect"),
                title = stringResource(R.string.pause_on_headphone_disconnect),
                text = stringResource(R.string.pause_on_headphone_disconnect_description),
                isChecked = pauseOnHeadphoneDisconnect,
                onCheckedChange = { pauseOnHeadphoneDisconnect = it },
                icon = R.drawable.headphones
            )

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("general.app_behavior.skip_media_on_error"),
                title = stringResource(R.string.skip_media_on_error),
                text = stringResource(R.string.skip_media_on_error_description),
                isChecked = skipMediaOnError,
                onCheckedChange = {
                    skipMediaOnError = it
                    onRestartServiceSet()
                },
                icon = R.drawable.alert_circle
            )

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("general.playback.jump_previous"),
                title = stringResource(R.string.jump_previous),
                text = stringResource(R.string.settings_desc_general_playback_jump_previous),
                selectedValueText = jumpPrevious,
                onClick = { showJumpPreviousDialog = true },
                icon = R.drawable.play_skip_back
            )

            DialogPresence(visible = showJumpPreviousDialog) {
                SettingsInputDialog(
                    title = stringResource(R.string.jump_previous_blank),
                    initialValue = jumpPrevious,
                    placeholder = stringResource(R.string.jump_previous_blank),
                    onDismiss = { showJumpPreviousDialog = false },
                    onSetValue = {
                        if (TextUtils.isDigitsOnly(it))
                            jumpPrevious = it
                    }
                ).apply {
                    showDialog()
                    Render()
                }
            }

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("general.playback.min_listening_time"),
                title = stringResource(R.string.min_listening_time),
                text = stringResource(R.string.settings_desc_general_playback_min_listening_time),
                selectedValueText = when (exoPlayerMinTimeForEvent) {
                    ExoPlayerMinTimeForEvent.`10s` -> "10s"
                    ExoPlayerMinTimeForEvent.`15s` -> "15s"
                    ExoPlayerMinTimeForEvent.`20s` -> "20s"
                    ExoPlayerMinTimeForEvent.`30s` -> "30s"
                    ExoPlayerMinTimeForEvent.`40s` -> "40s"
                    ExoPlayerMinTimeForEvent.`60s` -> "60s"
                },
                onClick = { showMinListeningTimeDialog = true },
                icon = R.drawable.time
            )

            DialogPresence(visible = showMinListeningTimeDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.is_min_list_time_for_tips_or_quick_pics),
                    selectedValue = exoPlayerMinTimeForEvent,
                    onValueSelected = { exoPlayerMinTimeForEvent = it },
                    valueText = {
                        when (it) {
                            ExoPlayerMinTimeForEvent.`10s` -> "10s"
                            ExoPlayerMinTimeForEvent.`15s` -> "15s"
                            ExoPlayerMinTimeForEvent.`20s` -> "20s"
                            ExoPlayerMinTimeForEvent.`30s` -> "30s"
                            ExoPlayerMinTimeForEvent.`40s` -> "40s"
                            ExoPlayerMinTimeForEvent.`60s` -> "60s"
                        }
                    },
                    values = ExoPlayerMinTimeForEvent.values().toList(),
                    onDismiss = { showMinListeningTimeDialog = false }
                )
            }

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("general.playback.exclude_songs_with_duration_limit"),
                title = stringResource(R.string.exclude_songs_with_duration_limit),
                text = stringResource(R.string.settings_desc_general_playback_exclude_songs_with_duration_limit),
                selectedValueText = when (excludeSongWithDurationLimit) {
                    DurationInMinutes.Disabled -> stringResource(R.string.vt_disabled)
                    DurationInMinutes.`3` -> "3m"
                    DurationInMinutes.`5` -> "5m"
                    DurationInMinutes.`10` -> "10m"
                    DurationInMinutes.`15` -> "15m"
                    DurationInMinutes.`20` -> "20m"
                    DurationInMinutes.`25` -> "25m"
                    DurationInMinutes.`30` -> "30m"
                    DurationInMinutes.`60` -> "60m"
                },
                onClick = { showExcludeSongsDialog = true },
                icon = R.drawable.playbackduration
            )

            DialogPresence(visible = showExcludeSongsDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.exclude_songs_with_duration_limit_description),
                    selectedValue = excludeSongWithDurationLimit,
                    onValueSelected = { excludeSongWithDurationLimit = it },
                    valueText = {
                        when (it) {
                            DurationInMinutes.Disabled -> stringResource(R.string.vt_disabled)
                            DurationInMinutes.`3` -> "3m"
                            DurationInMinutes.`5` -> "5m"
                            DurationInMinutes.`10` -> "10m"
                            DurationInMinutes.`15` -> "15m"
                            DurationInMinutes.`20` -> "20m"
                            DurationInMinutes.`25` -> "25m"
                            DurationInMinutes.`30` -> "30m"
                            DurationInMinutes.`60` -> "60m"
                        }
                    },
                    values = DurationInMinutes.values().toList(),
                    onDismiss = { showExcludeSongsDialog = false }
                )
            }

            // Dislike exclusion settings
            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("general.playback.exclude_disliked_songs"),
                title = stringResource(R.string.exclude_disliked_songs),
                text = stringResource(R.string.settings_desc_general_playback_exclude_disliked_songs),
                selectedValueText = excludeDislikedSongs.text,
                onClick = { showDislikeSongsDialog = true },
                icon = R.drawable.heart_dislike
            )

            DialogPresence(visible = showDislikeSongsDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.exclude_disliked_songs),
                    selectedValue = excludeDislikedSongs,
                    onValueSelected = {
                        excludeDislikedSongs = it
                        if (it == DislikeMode.Disabled) {
                            NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                Database.songTable.clearAllDisliked()
                            }
                        }
                    },
                    valueText = { it.text },
                    values = DislikeMode.values().toList(),
                    onDismiss = { showDislikeSongsDialog = false }
                )
            }

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("general.playback.exclude_disliked_artists"),
                title = stringResource(R.string.exclude_disliked_artists),
                text = stringResource(R.string.settings_desc_general_playback_exclude_disliked_artists),
                selectedValueText = excludeDislikedArtists.text,
                onClick = { showDislikeArtistsDialog = true },
                icon = R.drawable.person
            )

            DialogPresence(visible = showDislikeArtistsDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.exclude_disliked_artists),
                    selectedValue = excludeDislikedArtists,
                    onValueSelected = {
                        excludeDislikedArtists = it
                        if (it == DislikeMode.Disabled) {
                            NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                Database.artistTable.clearAllDisliked()
                            }
                        }
                    },
                    valueText = { it.text },
                    values = DislikeMode.values().toList(),
                    onDismiss = { showDislikeArtistsDialog = false }
                )
            }

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("general.playback.exclude_disliked_albums"),
                title = stringResource(R.string.exclude_disliked_albums),
                text = stringResource(R.string.settings_desc_general_playback_exclude_disliked_albums),
                selectedValueText = excludeDislikedAlbums.text,
                onClick = { showDislikeAlbumsDialog = true },
                icon = R.drawable.album
            )

            DialogPresence(visible = showDislikeAlbumsDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.exclude_disliked_albums),
                    selectedValue = excludeDislikedAlbums,
                    onValueSelected = {
                        excludeDislikedAlbums = it
                        if (it == DislikeMode.Disabled) {
                            NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                Database.albumTable.clearAllDisliked()
                            }
                        }
                    },
                    valueText = { it.text },
                    values = DislikeMode.values().toList(),
                    onDismiss = { showDislikeAlbumsDialog = false }
                )
            }

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("general.playback.pause_between_songs"),
                title = stringResource(R.string.pause_between_songs),
                text = stringResource(R.string.settings_desc_general_playback_pause_between_songs),
                selectedValueText = when (pauseBetweenSongs) {
                    PauseBetweenSongs.`0` -> "0s"
                    PauseBetweenSongs.`5` -> "5s"
                    PauseBetweenSongs.`10` -> "10s"
                    PauseBetweenSongs.`15` -> "15s"
                    PauseBetweenSongs.`20` -> "20s"
                    PauseBetweenSongs.`30` -> "30s"
                    PauseBetweenSongs.`40` -> "40s"
                    PauseBetweenSongs.`50` -> "50s"
                    PauseBetweenSongs.`60` -> "60s"
                },
                onClick = { showPauseBetweenSongsDialog = true },
                icon = R.drawable.pause
            )

            DialogPresence(visible = showPauseBetweenSongsDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.pause_between_songs_description),
                    selectedValue = pauseBetweenSongs,
                    onValueSelected = { pauseBetweenSongs = it },
                    valueText = {
                        when (it) {
                            PauseBetweenSongs.`0` -> "0s"
                            PauseBetweenSongs.`5` -> "5s"
                            PauseBetweenSongs.`10` -> "10s"
                            PauseBetweenSongs.`15` -> "15s"
                            PauseBetweenSongs.`20` -> "20s"
                            PauseBetweenSongs.`30` -> "30s"
                            PauseBetweenSongs.`40` -> "40s"
                            PauseBetweenSongs.`50` -> "50s"
                            PauseBetweenSongs.`60` -> "60s"
                        }
                    },
                    values = PauseBetweenSongs.values().toList(),
                    onDismiss = { showPauseBetweenSongsDialog = false }
                )
            }

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("general.playback.crossfade"),
                title = stringResource(R.string.crossfade),
                text = stringResource(R.string.crossfade_description),
                isChecked = crossfadeEnabled,
                onCheckedChange = { crossfadeEnabled = it },
                icon = R.drawable.shuffle
            )

            AnimatedVisibility(visible = crossfadeEnabled) {
                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                    var showCrossfadeDurationDialog by remember { mutableStateOf(false) }
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("general.playback.crossfade_duration"),
                        title = stringResource(R.string.crossfade_duration),
                        text = stringResource(R.string.settings_desc_general_playback_crossfade_duration),
                        selectedValueText = "${crossfadeDuration / 1000}s",
                        onClick = { showCrossfadeDurationDialog = true },
                        icon = R.drawable.time
                    )

                    DialogPresence(visible = showCrossfadeDurationDialog) {
                        ValueSelectorDialog(
                            title = stringResource(R.string.crossfade_duration),
                            selectedValue = crossfadeDuration,
                            onValueSelected = { crossfadeDuration = it },
                            valueText = { "${it / 1000}s" },
                            values = listOf(1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000, 12000, 15000, 20000),
                            onDismiss = { showCrossfadeDurationDialog = false }
                        )
                    }

                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("general.playback.crossfade_gapless"),
                        title = stringResource(R.string.crossfade_gapless),
                        text = stringResource(R.string.crossfade_gapless_description),
                        isChecked = crossfadeGapless,
                        onCheckedChange = { crossfadeGapless = it },
                        icon = R.drawable.album
                    )
                }
            }
        }
    )
}
