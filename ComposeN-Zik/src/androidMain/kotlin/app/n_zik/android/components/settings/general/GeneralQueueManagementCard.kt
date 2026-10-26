package app.n_zik.android.components.settings.general

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.enums.MaxSongs
import app.it.fast4x.rimusic.enums.MusicAnimationType
import app.it.fast4x.rimusic.utils.discoverKey
import app.it.fast4x.rimusic.utils.maxSongsInQueueAndroidAutoKey
import app.it.fast4x.rimusic.utils.maxSongsInQueueKey
import app.it.fast4x.rimusic.utils.nowPlayingIndicatorKey
import app.it.fast4x.rimusic.utils.playlistindicatorKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard

/** Queue Management card: queue size caps, discover, indicators. */
@Composable
fun GeneralQueueManagementCard(
    visible: Boolean,
) {
    var maxSongsInQueue by rememberPreference(maxSongsInQueueKey, MaxSongs.Unlimited)
    var maxSongsInQueueAndroidAuto by rememberPreference(maxSongsInQueueAndroidAutoKey, MaxSongs.Unlimited)
    var discoverIsEnabled by rememberPreference(discoverKey, false)
    var playlistindicator by rememberPreference(playlistindicatorKey, false)
    var nowPlayingIndicator by rememberPreference(nowPlayingIndicatorKey, MusicAnimationType.Bubbles)

    var showMaxSongsDialog by remember { mutableStateOf(false) }
    var showMaxSongsAndroidAutoDialog by remember { mutableStateOf(false) }
    var showNowPlayingIndicatorDialog by remember { mutableStateOf(false) }

    SettingsSectionCard(
        title = stringResource(R.string.queue_management),
        icon = R.drawable.playlist,
        visible = visible,
        content = {
            OtherSettingsEntry(
                title = stringResource(R.string.max_songs_in_queue),
                text = when (maxSongsInQueue) {
                    MaxSongs.Unlimited -> stringResource(R.string.unlimited)
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
                title = stringResource(R.string.max_songs_in_queue_android_auto),
                text = when (maxSongsInQueueAndroidAuto) {
                    MaxSongs.Unlimited -> stringResource(R.string.unlimited)
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

            if (showMaxSongsDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.max_songs_in_queue),
                    selectedValue = maxSongsInQueue,
                    onValueSelected = { maxSongsInQueue = it },
                    valueText = {
                        when (it) {
                            MaxSongs.Unlimited -> stringResource(R.string.unlimited)
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

            if (showMaxSongsAndroidAutoDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.max_songs_in_queue_android_auto),
                    selectedValue = maxSongsInQueueAndroidAuto,
                    onValueSelected = { maxSongsInQueueAndroidAuto = it },
                    valueText = {
                        when (it) {
                            MaxSongs.Unlimited -> stringResource(R.string.unlimited)
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
                title = stringResource(R.string.discover),
                text = stringResource(R.string.discoverinfo),
                isChecked = discoverIsEnabled,
                onCheckedChange = { discoverIsEnabled = it },
                icon = R.drawable.search
            )

            OtherSwitchSettingEntry(
                title = stringResource(R.string.playlistindicator),
                text = stringResource(R.string.playlistindicatorinfo),
                isChecked = playlistindicator,
                onCheckedChange = { playlistindicator = it },
                icon = R.drawable.playlist
            )

            OtherSettingsEntry(
                title = stringResource(R.string.now_playing_indicator),
                text = nowPlayingIndicator.text,
                onClick = { showNowPlayingIndicatorDialog = true },
                icon = R.drawable.playing_indicator
            )

            if (showNowPlayingIndicatorDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.now_playing_indicator),
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
