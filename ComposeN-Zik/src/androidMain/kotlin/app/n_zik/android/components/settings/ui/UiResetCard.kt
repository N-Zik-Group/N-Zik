package app.n_zik.android.components.settings.ui

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.R

/** Applies the original Interface defaults once per explicit reset action. */
@Composable
internal fun UiResetCard(search: Search) {
    val context = LocalContext.current
    val matches = search.inputValue.isBlank() ||
        stringResource(R.string.settings_reset).contains(search.inputValue, true) ||
        stringResource(R.string.settings_restore_default_settings).contains(search.inputValue, true)
    AnimatedVisibility(
        visible = matches,
        enter = settingsCardEnter,
        exit = settingsCardExit
    ) {
        SettingsSectionCard(
            modifier = Modifier.testTag("ui.card.reset"),
            title = stringResource(R.string.settings_reset),
            icon = R.drawable.refresh,
            content = {
                if (matches) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.settings_reset.settings_reset"),
                        title = stringResource(R.string.settings_reset),
                        text = stringResource(R.string.settings_restore_default_settings),
                        icon = R.drawable.refresh,
                        onClick = {
                            applyUiDefaults(context)
                            Toaster.done()
                        }
                    )
                }
            }
        )
    }
}
