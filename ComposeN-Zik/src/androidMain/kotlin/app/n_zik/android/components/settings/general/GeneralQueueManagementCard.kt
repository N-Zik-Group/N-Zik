package app.n_zik.android.components.settings.general

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
import app.it.fast4x.rimusic.enums.MaxSongs
import app.it.fast4x.rimusic.enums.MusicAnimationType
import app.it.fast4x.rimusic.utils.autoLoadSongsInQueueKey
import app.it.fast4x.rimusic.utils.discoverKey
import app.it.fast4x.rimusic.utils.maxSongsInQueueAndroidAutoKey
import app.it.fast4x.rimusic.utils.maxSongsInQueueKey
import app.it.fast4x.rimusic.utils.nowPlayingIndicatorKey
import app.it.fast4x.rimusic.utils.persistentQueueKey
import app.it.fast4x.rimusic.utils.playlistindicatorKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.resumePlaybackOnStartKey
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsSearchTarget

/** Queue Management card: queue persistence, loading, size caps and indicators. */
@Composable
fun GeneralQueueManagementCard(
    visible: Boolean,
    onRestartServiceSet: () -> Unit,
) {
    var persistentQueue by rememberPreference(persistentQueueKey, false)
    var resumePlaybackOnStart by rememberPreference(resumePlaybackOnStartKey, false)
    var autoLoadSongsInQueue by rememberPreference(autoLoadSongsInQueueKey, true)
    var maxSongsInQueue by rememberPreference(maxSongsInQueueKey, MaxSongs.Unlimited)
    var maxSongsInQueueAndroidAuto by rememberPreference(maxSongsInQueueAndroidAutoKey, MaxSongs.Unlimited)
    var discoverIsEnabled by rememberPreference(discoverKey, false)
    var playlistindicator by rememberPreference(playlistindicatorKey, false)
    var nowPlayingIndicator by rememberPreference(nowPlayingIndicatorKey, MusicAnimationType.Bubbles)

    var showMaxSongsDialog by remember { mutableStateOf(false) }
    var showMaxSongsAndroidAutoDialog by remember { mutableStateOf(false) }
    var showNowPlayingIndicatorDialog by remember { mutableStateOf(false) }

    SettingsSectionCard(
        title = stringResource(R.string.settings_queue_management_title),
        icon = R.drawable.playlist,
        visible = visible,
        content = {
            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("general.app_behavior.persistent_queue"),
                title = stringResource(R.string.settings_player_persistent_queue_label),
                text = stringResource(R.string.settings_player_persistent_queue_description),
                isChecked = persistentQueue,
                onCheckedChange = {
                    persistentQueue = it
                    onRestartServiceSet()
                },
                icon = R.drawable.download
            )

            AnimatedVisibility(visible = persistentQueue) {
                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("general.app_behavior.resume_playback_on_start"),
                        title = stringResource(R.string.settings_playback_resume_on_start_label),
                        text = stringResource(R.string.settings_playback_resume_on_start_description),
                        isChecked = resumePlaybackOnStart,
                        onCheckedChange = {
                            resumePlaybackOnStart = it
                            onRestartServiceSet()
                        },
                        icon = R.drawable.play
                    )
                }
            }

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("general.player_controls.player_auto_load_songs_in_queue"),
                title = stringResource(R.string.player_auto_load_songs_in_queue),
                text = stringResource(R.string.player_auto_load_songs_in_queue_description),
                isChecked = autoLoadSongsInQueue,
                onCheckedChange = {
                    autoLoadSongsInQueue = it
                    onRestartServiceSet()
                },
                icon = R.drawable.playlist
            )

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("general.queue_management.max_songs_in_queue"),
                title = stringResource(R.string.settings_player_queue_size_limit_label),
                text = stringResource(R.string.queue_max_songs_in_queue_description),
                selectedValueText = when (maxSongsInQueue) {
                    MaxSongs.Unlimited -> stringResource(R.string.common_limit_unlimited_label)
                    MaxSongs.`50` -> MaxSongs.`50`.name
                    MaxSongs.`100` -> MaxSongs.`100`.name
                    MaxSongs.`200` -> MaxSongs.`200`.name
                    MaxSongs.`300` -> MaxSongs.`300`.name
                    MaxSongs.`500` -> MaxSongs.`500`.name
                    MaxSongs.`1000` -> MaxSongs.`1000`.name
                    MaxSongs.`2000` -> MaxSongs.`2000`.name
                    MaxSongs.`3000` -> MaxSongs.`3000`.name
                },
                onClick = { showMaxSongsDialog = true },
                icon = R.drawable.music_file
            )

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("general.queue_management.max_songs_in_queue_android_auto"),
                title = stringResource(R.string.settings_automotive_queue_size_limit_label),
                text = stringResource(R.string.queue_max_songs_in_queue_android_auto_description),
                selectedValueText = when (maxSongsInQueueAndroidAuto) {
                    MaxSongs.Unlimited -> stringResource(R.string.common_limit_unlimited_label)
                    MaxSongs.`50` -> MaxSongs.`50`.name
                    MaxSongs.`100` -> MaxSongs.`100`.name
                    MaxSongs.`200` -> MaxSongs.`200`.name
                    MaxSongs.`300` -> MaxSongs.`300`.name
                    MaxSongs.`500` -> MaxSongs.`500`.name
                    MaxSongs.`1000` -> MaxSongs.`1000`.name
                    MaxSongs.`2000` -> MaxSongs.`2000`.name
                    MaxSongs.`3000` -> MaxSongs.`3000`.name
                },
                onClick = { showMaxSongsAndroidAutoDialog = true },
                icon = R.drawable.music_file
            )

            DialogPresence(visible = showMaxSongsDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.settings_player_queue_size_limit_label),
                    selectedValue = maxSongsInQueue,
                    onValueSelected = { maxSongsInQueue = it },
                    valueText = {
                        when (it) {
                            MaxSongs.Unlimited -> stringResource(R.string.common_limit_unlimited_label)
                            MaxSongs.`50` -> MaxSongs.`50`.name
                            MaxSongs.`100` -> MaxSongs.`100`.name
                            MaxSongs.`200` -> MaxSongs.`200`.name
                            MaxSongs.`300` -> MaxSongs.`300`.name
                            MaxSongs.`500` -> MaxSongs.`500`.name
                            MaxSongs.`1000` -> MaxSongs.`1000`.name
                            MaxSongs.`2000` -> MaxSongs.`2000`.name
                            MaxSongs.`3000` -> MaxSongs.`3000`.name
                        }
                    },
                    values = MaxSongs.values().toList(),
                    onDismiss = { showMaxSongsDialog = false }
                )
            }

            DialogPresence(visible = showMaxSongsAndroidAutoDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.settings_automotive_queue_size_limit_label),
                    selectedValue = maxSongsInQueueAndroidAuto,
                    onValueSelected = { maxSongsInQueueAndroidAuto = it },
                    valueText = {
                        when (it) {
                            MaxSongs.Unlimited -> stringResource(R.string.common_limit_unlimited_label)
                            MaxSongs.`50` -> MaxSongs.`50`.name
                            MaxSongs.`100` -> MaxSongs.`100`.name
                            MaxSongs.`200` -> MaxSongs.`200`.name
                            MaxSongs.`300` -> MaxSongs.`300`.name
                            MaxSongs.`500` -> MaxSongs.`500`.name
                            MaxSongs.`1000` -> MaxSongs.`1000`.name
                            MaxSongs.`2000` -> MaxSongs.`2000`.name
                            MaxSongs.`3000` -> MaxSongs.`3000`.name
                        }
                    },
                    values = MaxSongs.values().toList(),
                    onDismiss = { showMaxSongsAndroidAutoDialog = false }
                )
            }

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("general.queue_management.discover"),
                title = stringResource(R.string.queue_discover_title),
                text = stringResource(R.string.queue_discover_description),
                isChecked = discoverIsEnabled,
                onCheckedChange = { discoverIsEnabled = it },
                icon = R.drawable.search
            )

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("general.queue_management.playlistindicator"),
                title = stringResource(R.string.settings_library_playlist_indicator_label),
                text = stringResource(R.string.settings_library_playlist_indicator_description),
                isChecked = playlistindicator,
                onCheckedChange = { playlistindicator = it },
                icon = R.drawable.playlist
            )

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("general.queue_management.now_playing_indicator"),
                title = stringResource(R.string.settings_library_now_playing_indicator_label),
                text = stringResource(R.string.queue_now_playing_indicator_description),
                selectedValueText = nowPlayingIndicator.text,
                onClick = { showNowPlayingIndicatorDialog = true },
                icon = R.drawable.playing_indicator
            )

            DialogPresence(visible = showNowPlayingIndicatorDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.settings_library_now_playing_indicator_label),
                    selectedValue = nowPlayingIndicator,
                    onValueSelected = { nowPlayingIndicator = it },
                    valueText = { it.text },
                    values = MusicAnimationType.values().toList(),
                    onDismiss = { showNowPlayingIndicatorDialog = false }
                )
            }
        }
    )
}
