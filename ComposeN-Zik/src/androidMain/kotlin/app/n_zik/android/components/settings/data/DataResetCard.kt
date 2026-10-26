package app.n_zik.android.components.settings.data

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search

/** Applies defaults once in response to the reset action. */
@Composable
internal fun DataResetCard(search: Search) {
    val context = LocalContext.current
    val visible = search.inputValue.isBlank() || stringResource(R.string.settings_reset).contains(search.inputValue, true) || stringResource(R.string.settings_restore_default_settings).contains(search.inputValue, true)
    SettingsSectionCard(
        title = stringResource(R.string.settings_reset),
        icon = R.drawable.refresh,
        modifier = Modifier.testTag("data.card.reset"),
        visible = visible,
        content = {
            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("data.settings_reset.settings_reset"),
                title = stringResource(R.string.settings_reset),
                text = stringResource(R.string.settings_restore_default_settings),
                icon = R.drawable.refresh,
                onClick = { applyDataDefaults(context); Toaster.done() }
            )
        }
    )
}
