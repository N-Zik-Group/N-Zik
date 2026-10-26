package app.n_zik.android.components.settings.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.components.dialog.settings.PlayerActionBarSettingsDialog
import app.n_zik.android.components.dialog.settings.MiniPlayerButtonsSettingsDialog
import app.n_zik.android.components.tab.Search
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.testTag
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit

@OptIn(ExperimentalAnimationApi::class)
@UnstableApi
@Composable
internal fun AppearanceResetCard(search: Search, restartState: MutableState<Boolean>) {

    var restartService by restartState
    val searchCtx_Reset = search.inputValue.isBlank() || stringResource(R.string.settings_reset).contains(search.inputValue, true) || stringResource(R.string.settings_restore_default_settings).contains(search.inputValue, true)
    AnimatedVisibility(
                visible = searchCtx_Reset,
                enter = settingsCardEnter,
                exit = settingsCardExit
            ) {
                SettingsSectionCard(
                    modifier = Modifier.testTag("appearance.card.reset"),
                    title = stringResource(R.string.settings_reset),
                    icon = R.drawable.refresh,
                    content = {

                        val context = LocalContext.current
                        if (search.inputValue.isBlank() || stringResource(R.string.settings_restore_default_settings).contains(search.inputValue, true) || stringResource(R.string.settings_reset).contains(search.inputValue, true)) {
                            OtherSettingsEntry(
                                title = stringResource(R.string.settings_reset),
                                text = stringResource(R.string.settings_restore_default_settings),
                                icon = R.drawable.refresh,
                                onClick = {
                                    applyAppearanceDefaults(context)
                                    restartService = true
                                    MiniPlayerButtonsSettingsDialog.reset(context)
                                    PlayerActionBarSettingsDialog.reset(context)
                                    Toaster.done()
                                }
                            )
                        }
                    }
                )
            }
}
