package app.n_zik.android.components.settings.general

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.n_zik.android.utils.closeWithBackButtonKey
import app.n_zik.android.utils.closebackgroundPlayerKey
import app.n_zik.android.utils.isAtLeastAndroid13
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsSearchTarget

/** App Behavior card: back navigation and background-player closing. */
@Composable
fun GeneralAppBehaviorCard(
    visible: Boolean,
    onRestartServiceSet: () -> Unit,
) {
    var closeWithBackButton by rememberPreference(closeWithBackButtonKey, true)
    var closebackgroundPlayer by rememberPreference(closebackgroundPlayerKey, false)

    SettingsSectionCard(
        title = stringResource(R.string.settings_app_behavior_title),
        icon = R.drawable.settings,
        visible = visible,
        content = {
            if (isAtLeastAndroid13) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("general.app_behavior.close_app_with_back_button"),
                    title = stringResource(R.string.settings_behavior_back_button_exits_label),
                    text = stringResource(R.string.settings_behavior_back_button_exits_description),
                    isChecked = closeWithBackButton,
                    onCheckedChange = { closeWithBackButton = it },
                    icon = R.drawable.close
                )
            }
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
