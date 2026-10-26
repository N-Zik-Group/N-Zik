package app.n_zik.android.components.settings.general

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.utils.RestartPlayerService
import app.it.fast4x.rimusic.utils.autoLoadSongsInQueueKey
import app.it.fast4x.rimusic.utils.disableClosingPlayerSwipingDownKey
import app.it.fast4x.rimusic.utils.isPauseOnVolumeZeroEnabledKey
import app.it.fast4x.rimusic.utils.keepPlayerMinimizedKey
import app.it.fast4x.rimusic.utils.pauseOnHeadphoneDisconnectKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard

/** Player Controls card: pause/keep-minimized/swipe/auto-load switches. */
@Composable
fun GeneralPlayerControlsCard(
    visible: Boolean,
    restartService: Boolean,
    onRestartServiceSet: () -> Unit,
    onRestartServiceClear: () -> Unit,
) {
    var isPauseOnVolumeZeroEnabled by rememberPreference(isPauseOnVolumeZeroEnabledKey, false)
    var pauseOnHeadphoneDisconnect by rememberPreference(pauseOnHeadphoneDisconnectKey, true)
    var keepPlayerMinimized by rememberPreference(keepPlayerMinimizedKey, true)
    var disableClosingPlayerSwipingDown by rememberPreference(disableClosingPlayerSwipingDownKey, false)
    var autoLoadSongsInQueue by rememberPreference(autoLoadSongsInQueueKey, true)

    SettingsSectionCard(
        title = stringResource(R.string.player_controls),
        icon = R.drawable.player_control,
        visible = visible,
        content = {
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
                modifier = Modifier.settingsSearchTarget("general.player_controls.player_keep_minimized"),
                title = stringResource(R.string.player_keep_minimized),
                text = stringResource(R.string.when_click_on_a_song_player_start_minimized),
                isChecked = keepPlayerMinimized,
                onCheckedChange = { keepPlayerMinimized = it },
                icon = R.drawable.maximize
            )

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("general.player_controls.player_collapsed_disable_swiping_down"),
                title = stringResource(R.string.player_collapsed_disable_swiping_down),
                text = stringResource(R.string.avoid_closing_the_player_cleaning_queue_by_swiping_down),
                isChecked = disableClosingPlayerSwipingDown,
                onCheckedChange = { disableClosingPlayerSwipingDown = it },
                icon = R.drawable.reorder
            )

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
            RestartPlayerService(restartService, onRestart = { onRestartServiceClear() })
        }
    )
}
