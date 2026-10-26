package app.n_zik.android.components.settings.general

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.RestartActivity
import app.it.fast4x.rimusic.utils.RestartPlayerService
import app.it.fast4x.rimusic.utils.closeWithBackButtonKey
import app.it.fast4x.rimusic.utils.closebackgroundPlayerKey
import app.it.fast4x.rimusic.utils.isAtLeastAndroid6
import app.it.fast4x.rimusic.utils.persistentQueueKey
import app.it.fast4x.rimusic.utils.resumePlaybackOnStartKey
import app.it.fast4x.rimusic.utils.resumePlaybackWhenDeviceConnectedKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.skipMediaOnErrorKey
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard

/** App Behavior card: resume, persistent queue, back/close behavior, skip-on-error. */
@Composable
fun GeneralAppBehaviorCard(
    visible: Boolean,
    restartService: Boolean,
    onRestartServiceSet: () -> Unit,
    onRestartServiceClear: () -> Unit,
    restartActivity: Boolean,
    onRestartActivitySet: () -> Unit,
    onRestartActivityClear: () -> Unit,
) {
    var resumePlaybackWhenDeviceConnected by rememberPreference(
        resumePlaybackWhenDeviceConnectedKey,
        false
    )
    var persistentQueue by rememberPreference(persistentQueueKey, false)
    var resumePlaybackOnStart by rememberPreference(resumePlaybackOnStartKey, false)
    var closeWithBackButton by rememberPreference(closeWithBackButtonKey, true)
    var closebackgroundPlayer by rememberPreference(closebackgroundPlayerKey, false)
    var skipMediaOnError by rememberPreference(skipMediaOnErrorKey, false)

    SettingsSectionCard(
        title = stringResource(R.string.app_behavior),
        icon = R.drawable.settings,
        visible = visible,
        content = {
            if (isAtLeastAndroid6) {
                OtherSwitchSettingEntry(
                    title = stringResource(R.string.resume_playback),
                    text = stringResource(R.string.when_device_is_connected),
                    isChecked = resumePlaybackWhenDeviceConnected,
                    onCheckedChange = {
                        resumePlaybackWhenDeviceConnected = it
                        onRestartServiceSet()
                    },
                    icon = R.drawable.play
                )
                RestartPlayerService(restartService, onRestart = { onRestartServiceClear() })
            }

            OtherSwitchSettingEntry(
                title = stringResource(R.string.persistent_queue),
                text = stringResource(R.string.save_and_restore_playing_songs),
                isChecked = persistentQueue,
                onCheckedChange = {
                    persistentQueue = it
                    onRestartServiceSet()
                },
                icon = R.drawable.download
            )
            RestartPlayerService(restartService, onRestart = { onRestartServiceClear() })

            AnimatedVisibility(visible = persistentQueue) {
                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                    OtherSwitchSettingEntry(
                        title = stringResource(R.string.resume_playback_on_start),
                        text = stringResource(R.string.resume_automatically_when_app_opens),
                        isChecked = resumePlaybackOnStart,
                        onCheckedChange = {
                            resumePlaybackOnStart = it
                            onRestartServiceSet()
                        },
                        icon = R.drawable.play
                    )
                    RestartPlayerService(restartService, onRestart = { onRestartServiceClear() })
                }
            }

            if (Build.VERSION.SDK_INT >= 33) {
                OtherSwitchSettingEntry(
                    title = stringResource(R.string.close_app_with_back_button),
                    text = stringResource(R.string.when_you_use_the_back_button_from_the_home_page),
                    isChecked = closeWithBackButton,
                    onCheckedChange = {
                        closeWithBackButton = it
                        onRestartActivitySet()
                    },
                    icon = R.drawable.close
                )
            }
            RestartActivity(restartActivity, onRestart = { onRestartActivityClear() })

            OtherSwitchSettingEntry(
                title = stringResource(R.string.close_background_player),
                text = stringResource(R.string.when_app_swipe_out_from_task_manager),
                isChecked = closebackgroundPlayer,
                onCheckedChange = {
                    closebackgroundPlayer = it
                    onRestartServiceSet()
                },
                icon = R.drawable.close
            )
            RestartPlayerService(restartService, onRestart = { onRestartServiceClear() })

            OtherSwitchSettingEntry(
                title = stringResource(R.string.skip_media_on_error),
                text = stringResource(R.string.skip_media_on_error_description),
                isChecked = skipMediaOnError,
                onCheckedChange = {
                    skipMediaOnError = it
                    onRestartServiceSet()
                },
                icon = R.drawable.alert_circle
            )
            RestartPlayerService(restartService, onRestart = { onRestartServiceClear() })
        }
    )
}
