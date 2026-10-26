package app.n_zik.android.components.settings.general

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.utils.RestartPlayerService
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.useVolumeKeysToChangeSongKey
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard

/** Gestures & Events card: volume keys to change song. */
@Composable
fun GeneralGesturesCard(
    visible: Boolean,
    restartService: Boolean,
    onRestartServiceSet: () -> Unit,
    onRestartServiceClear: () -> Unit,
) {
    var useVolumeKeysToChangeSong by rememberPreference(useVolumeKeysToChangeSongKey, false)

    SettingsSectionCard(
        title = stringResource(R.string.gestures_events),
        icon = R.drawable.gesture,
        visible = visible,
        content = {
            OtherSwitchSettingEntry(
                title = stringResource(R.string.event_volumekeys),
                text = stringResource(R.string.event_volumekeysinfo),
                isChecked = useVolumeKeysToChangeSong,
                onCheckedChange = {
                    useVolumeKeysToChangeSong = it
                    onRestartServiceSet()
                },
                icon = R.drawable.volume_control
            )
            RestartPlayerService(restartService, onRestart = { onRestartServiceClear() })
        }
    )
}
