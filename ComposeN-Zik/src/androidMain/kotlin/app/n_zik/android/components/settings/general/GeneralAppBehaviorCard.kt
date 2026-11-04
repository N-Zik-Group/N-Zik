package app.n_zik.android.components.settings.general

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.utils.closeWithBackButtonKey
import app.it.fast4x.rimusic.utils.closebackgroundPlayerKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.RestartActivity
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsSearchTarget

/** App Behavior card: back navigation and background-player closing. */
@Composable
fun GeneralAppBehaviorCard(
    visible: Boolean,
    onRestartServiceSet: () -> Unit,
    restartActivity: Boolean,
    onRestartActivitySet: () -> Unit,
    onRestartActivityClear: () -> Unit,
) {
    var closeWithBackButton by rememberPreference(closeWithBackButtonKey, true)
    var closebackgroundPlayer by rememberPreference(closebackgroundPlayerKey, false)

    SettingsSectionCard(
        title = stringResource(R.string.settings_app_behavior_title),
        icon = R.drawable.settings,
        visible = visible,
        content = {
            if (Build.VERSION.SDK_INT >= 33) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("general.app_behavior.close_app_with_back_button"),
                    title = stringResource(R.string.settings_behavior_back_button_exits_label),
                    text = stringResource(R.string.settings_behavior_back_button_exits_description),
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
                modifier = Modifier.settingsSearchTarget("general.app_behavior.close_background_player"),
                title = stringResource(R.string.settings_player_stop_on_task_removal_label),
                text = stringResource(R.string.settings_player_stop_on_task_removal_description),
                isChecked = closebackgroundPlayer,
                onCheckedChange = {
                    closebackgroundPlayer = it
                    onRestartServiceSet()
                },
                icon = R.drawable.close
            )

        }
    )
}
