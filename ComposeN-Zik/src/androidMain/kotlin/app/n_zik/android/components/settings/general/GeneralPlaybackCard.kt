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
import app.n_zik.android.utils.secondsLabel
import androidx.compose.ui.unit.dp
import app.n_zik.android.enums.DislikeMode
import app.n_zik.android.enums.DurationInMinutes
import app.n_zik.android.enums.PauseBetweenSongs
import app.n_zik.android.playback.utils.parsePreviousTrackThreshold
import app.n_zik.android.utils.crossfadeDurationKey
import app.n_zik.android.utils.crossfadeEnabledKey
import app.n_zik.android.utils.crossfadeGaplessKey
import app.n_zik.android.utils.excludeDislikedAlbumsKey
import app.n_zik.android.utils.excludeDislikedArtistsKey
import app.n_zik.android.utils.excludeDislikedSongsKey
import app.n_zik.android.utils.excludeSongsWithDurationLimitKey
import app.n_zik.android.utils.isAtLeastAndroid6
import app.n_zik.android.utils.isPauseOnVolumeZeroEnabledKey
import app.n_zik.android.utils.jumpPreviousKey
import app.n_zik.android.utils.pauseBetweenSongsKey
import app.n_zik.android.utils.pauseOnHeadphoneDisconnectKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.resumePlaybackWhenDeviceConnectedKey
import app.n_zik.android.utils.skipMediaOnErrorKey
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.dialog.settings.SettingsInputDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.CardCategoryLabel
import app.n_zik.android.components.settings.common.SettingsCardSectionHeading
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
    var showExcludeSongsDialog by remember { mutableStateOf(false) }
    var showDislikeSongsDialog by remember { mutableStateOf(false) }
    var showDislikeArtistsDialog by remember { mutableStateOf(false) }
    var showDislikeAlbumsDialog by remember { mutableStateOf(false) }
    var showPauseBetweenSongsDialog by remember { mutableStateOf(false) }

    SettingsSectionCard(
        title = stringResource(R.string.common_playback_section_title),
        icon = R.drawable.play_forward,
        visible = visible,
        content = {
            CardCategoryLabel(stringResource(R.string.settings_group_automatic_playback))
            if (isAtLeastAndroid6) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("general.app_behavior.resume_playback"),
                    title = stringResource(R.string.settings_playback_resume_on_connection_label),
                    text = stringResource(R.string.settings_playback_resume_on_connection_description),
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
                text = stringResource(R.string.settings_playback_pause_at_zero_volume_description),
                isChecked = isPauseOnVolumeZeroEnabled,
                onCheckedChange = { isPauseOnVolumeZeroEnabled = it },
                icon = R.drawable.volume_up
            )

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("general.player_controls.pause_on_headphone_disconnect"),
                title = stringResource(R.string.settings_playback_pause_on_headphone_disconnect_label),
                text = stringResource(R.string.settings_playback_pause_on_headphone_disconnect_description),
                isChecked = pauseOnHeadphoneDisconnect,
                onCheckedChange = { pauseOnHeadphoneDisconnect = it },
                icon = R.drawable.headphones
            )

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("general.app_behavior.skip_media_on_error"),
                title = stringResource(R.string.settings_playback_skip_on_error_label),
                text = stringResource(R.string.settings_playback_skip_on_error_description),
                isChecked = skipMediaOnError,
                onCheckedChange = {
                    skipMediaOnError = it
                    onRestartServiceSet()
                },
                icon = R.drawable.alert_circle
            )

            SettingsCardSectionHeading(R.string.settings_group_track_transitions)

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("general.playback.pause_between_songs"),
                title = stringResource(R.string.settings_playback_pause_between_songs_label),
                text = stringResource(R.string.player_pause_between_songs_description),
                selectedValueText = secondsLabel(pauseBetweenSongs.name.toInt()),
                onClick = { showPauseBetweenSongsDialog = true },
                icon = R.drawable.pause
            )

            DialogPresence(visible = showPauseBetweenSongsDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.settings_playback_pause_between_songs_description),
                    selectedValue = pauseBetweenSongs,
                    onValueSelected = { pauseBetweenSongs = it },
                    valueText = {
                        secondsLabel(it.name.toInt())
                    },
                    values = PauseBetweenSongs.values().toList(),
                    onDismiss = { showPauseBetweenSongsDialog = false }
                )
            }

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("general.playback.crossfade"),
                title = stringResource(R.string.settings_audio_crossfade_label),
                text = stringResource(R.string.settings_audio_crossfade_description),
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
                        title = stringResource(R.string.settings_audio_crossfade_duration_label),
                        text = stringResource(R.string.player_crossfade_duration_description),
                        selectedValueText = secondsLabel(crossfadeDuration / 1000),
                        onClick = { showCrossfadeDurationDialog = true },
                        icon = R.drawable.time
                    )

                    DialogPresence(visible = showCrossfadeDurationDialog) {
                        ValueSelectorDialog(
                            title = stringResource(R.string.settings_audio_crossfade_duration_label),
                            selectedValue = crossfadeDuration,
                            onValueSelected = { crossfadeDuration = it },
                            valueText = { secondsLabel(it / 1000) },
                            values = listOf(1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000, 12000, 15000, 20000),
                            onDismiss = { showCrossfadeDurationDialog = false }
                        )
                    }

                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("general.playback.crossfade_gapless"),
                        title = stringResource(R.string.settings_audio_crossfade_album_gapless_label),
                        text = stringResource(R.string.settings_audio_crossfade_album_gapless_description),
                        isChecked = crossfadeGapless,
                        onCheckedChange = { crossfadeGapless = it },
                        icon = R.drawable.album
                    )
                }
            }

            SettingsCardSectionHeading(R.string.settings_group_track_navigation)

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("general.playback.jump_previous"),
                title = stringResource(R.string.settings_playback_previous_restart_threshold_label),
                text = stringResource(R.string.player_previous_track_threshold_description),
                selectedValueText = (parsePreviousTrackThreshold(jumpPrevious) ?: 0).toString(),
                onClick = { showJumpPreviousDialog = true },
                icon = R.drawable.play_skip_back
            )

            DialogPresence(visible = showJumpPreviousDialog) {
                SettingsInputDialog(
                    title = stringResource(R.string.settings_playback_previous_restart_threshold_description),
                    initialValue = jumpPrevious,
                    placeholder = stringResource(R.string.settings_playback_previous_restart_threshold_description),
                    allowEmpty = true,
                    keyboardType = KeyboardType.Number,
                    isValid = { parsePreviousTrackThreshold(it) != null },
                    onDismiss = { showJumpPreviousDialog = false },
                    onSetValue = {
                        parsePreviousTrackThreshold(it)?.let { seconds -> jumpPrevious = seconds.toString() }
                    }
                ).apply {
                    showDialog()
                    Render()
                }
            }

            SettingsCardSectionHeading(R.string.settings_group_playback_exclusions)

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("general.playback.exclude_songs_with_duration_limit"),
                title = stringResource(R.string.settings_playback_duration_limit_label),
                text = stringResource(R.string.player_exclude_songs_with_duration_limit_description),
                selectedValueText = excludeSongWithDurationLimit.text,
                onClick = { showExcludeSongsDialog = true },
                icon = R.drawable.playbackduration
            )

            DialogPresence(visible = showExcludeSongsDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.settings_playback_duration_limit_description),
                    selectedValue = excludeSongWithDurationLimit,
                    onValueSelected = { excludeSongWithDurationLimit = it },
                    valueText = { it.text },
                    values = DurationInMinutes.values().toList(),
                    onDismiss = { showExcludeSongsDialog = false }
                )
            }

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("general.playback.exclude_disliked_songs"),
                title = stringResource(R.string.settings_playback_exclude_disliked_songs_label),
                text = stringResource(R.string.player_exclude_disliked_songs_description),
                selectedValueText = excludeDislikedSongs.text,
                onClick = { showDislikeSongsDialog = true },
                icon = R.drawable.heart_dislike
            )

            DialogPresence(visible = showDislikeSongsDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.settings_playback_exclude_disliked_songs_label),
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
                title = stringResource(R.string.settings_playback_exclude_disliked_artists_label),
                text = stringResource(R.string.player_exclude_disliked_artists_description),
                selectedValueText = excludeDislikedArtists.text,
                onClick = { showDislikeArtistsDialog = true },
                icon = R.drawable.person
            )

            DialogPresence(visible = showDislikeArtistsDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.settings_playback_exclude_disliked_artists_label),
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
                title = stringResource(R.string.settings_playback_exclude_disliked_albums_label),
                text = stringResource(R.string.player_exclude_disliked_albums_description),
                selectedValueText = excludeDislikedAlbums.text,
                onClick = { showDislikeAlbumsDialog = true },
                icon = R.drawable.album
            )

            DialogPresence(visible = showDislikeAlbumsDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.settings_playback_exclude_disliked_albums_label),
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
        }
    )
}
