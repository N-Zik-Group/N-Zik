package app.n_zik.android.components.settings.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.it.fast4x.rimusic.enums.NotificationButtons
import app.it.fast4x.rimusic.utils.RestartPlayerService
import app.it.fast4x.rimusic.utils.notificationPlayerFirstIconKey
import app.it.fast4x.rimusic.utils.notificationPlayerSecondIconKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.OtherEnumValueSelectorSettingsEntry
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.testTag
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit

@OptIn(ExperimentalAnimationApi::class)
@UnstableApi
@Composable
internal fun AppearanceNotificationCard(search: Search, restartState: MutableState<Boolean>) {
    var notificationPlayerFirstIcon by rememberPreference(notificationPlayerFirstIconKey, NotificationButtons.Download)
    var notificationPlayerSecondIcon by rememberPreference(notificationPlayerSecondIconKey, NotificationButtons.Favorites)
    var restartService by restartState
    val searchCtx_3 = search.inputValue.isBlank() || stringResource(R.string.notification_player).contains(search.inputValue, true) || stringResource(R.string.notificationPlayerFirstIcon).contains(search.inputValue, true) || stringResource(R.string.notificationPlayerSecondIcon).contains(search.inputValue, true)
    AnimatedVisibility(
                visible = searchCtx_3,
                enter = settingsCardEnter,
                exit = settingsCardExit
            ) {
                SettingsSectionCard(
                    modifier = Modifier.testTag("appearance.card.notification"),
                    title = stringResource(R.string.notification_player),
                    icon = R.drawable.notification1,
                    content = {

            if (search.inputValue.isBlank() || stringResource(R.string.notification_player).contains(
                    search.inputValue,
                    true
                )
            ) {
                if (search.inputValue.isBlank() || stringResource(R.string.notificationPlayerFirstIcon).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        icon = R.drawable.notification2,
                        title = stringResource(R.string.notificationPlayerFirstIcon),
                        selectedValue = notificationPlayerFirstIcon,
                        onValueSelected = {
                            notificationPlayerFirstIcon = it
                            restartService = true
                        },
                        valueText = { it.text },
                    )
                }
                if (search.inputValue.isBlank() || stringResource(R.string.notificationPlayerSecondIcon).contains(search.inputValue, true)) {
                    OtherEnumValueSelectorSettingsEntry(
                        icon = R.drawable.notification2,
                        title = stringResource(R.string.notificationPlayerSecondIcon),
                        selectedValue = notificationPlayerSecondIcon,
                        onValueSelected = {
                            notificationPlayerSecondIcon = it
                            restartService = true
                        },
                        valueText = { it.text },
                    )
                }
                RestartPlayerService(restartService, onRestart = { restartService = false })
            }

                    }
                )
            }
}
