package app.n_zik.android.components.settings.general

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard

/** Restores General preferences and requests a restart after applying the defaults. */
@Composable
fun GeneralResetCard(
    visible: Boolean,
    onRestartServiceSet: () -> Unit,
) {
    val context = LocalContext.current

    SettingsSectionCard(
        title = stringResource(R.string.settings_reset),
        icon = R.drawable.refresh,
        visible = visible,
        content = {
            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("general.settings_reset.settings_reset"),
                title = stringResource(R.string.settings_reset),
                text = stringResource(R.string.settings_restore_default_settings),
                icon = R.drawable.refresh,
                onClick = {
                    applyGeneralDefaults(context)
                    onRestartServiceSet()
                    Toaster.done()
                }
            )
        }
    )
}
