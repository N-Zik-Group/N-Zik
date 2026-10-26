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
import app.it.fast4x.rimusic.utils.jumpPreviousKey
import app.it.fast4x.rimusic.utils.pauseBetweenSongsKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.dialog.settings.SettingsInputDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch

/** Playback card: jump-previous, min listening time, exclusions, pause, crossfade. */
@Composable
fun GeneralPlaybackCard(
    visible: Boolean,
) {
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
            OtherSettingsEntry(
                title = stringResource(R.string.jump_previous),
                text = jumpPrevious,
                onClick = { showJumpPreviousDialog = true },
                icon = R.drawable.play_skip_back
            )

            if (showJumpPreviousDialog) {
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
                title = stringResource(R.string.min_listening_time),
                text = when (exoPlayerMinTimeForEvent) {
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

            if (showMinListeningTimeDialog) {
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
                title = stringResource(R.string.exclude_songs_with_duration_limit),
                text = when (excludeSongWithDurationLimit) {
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

            if (showExcludeSongsDialog) {
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
                title = stringResource(R.string.exclude_disliked_songs),
                text = excludeDislikedSongs.text,
                onClick = { showDislikeSongsDialog = true },
                icon = R.drawable.heart_dislike
            )

            if (showDislikeSongsDialog) {
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
                title = stringResource(R.string.exclude_disliked_artists),
                text = excludeDislikedArtists.text,
                onClick = { showDislikeArtistsDialog = true },
                icon = R.drawable.person
            )

            if (showDislikeArtistsDialog) {
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
                title = stringResource(R.string.exclude_disliked_albums),
                text = excludeDislikedAlbums.text,
                onClick = { showDislikeAlbumsDialog = true },
                icon = R.drawable.album
            )

            if (showDislikeAlbumsDialog) {
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
                title = stringResource(R.string.pause_between_songs),
                text = when (pauseBetweenSongs) {
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

            if (showPauseBetweenSongsDialog) {
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
                        title = stringResource(R.string.crossfade_duration),
                        text = "${crossfadeDuration / 1000}s",
                        onClick = { showCrossfadeDurationDialog = true },
                        icon = R.drawable.time
                    )

                    if (showCrossfadeDurationDialog) {
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
