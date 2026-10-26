package app.n_zik.android.components.settings.general

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard

/** Settings Reset card: one-shot reset of all General prefs via [DefaultGeneralSettings]. */
@Composable
fun GeneralResetCard(
    visible: Boolean,
    onRestartServiceSet: () -> Unit,
) {
    var resetToDefault by remember { mutableStateOf(false) }
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
                    resetToDefault = true
                    onRestartServiceSet()
                    Toaster.done()
                }
            )

            if (resetToDefault) {
                DefaultGeneralSettings(context)
                LaunchedEffect(Unit) {
                    resetToDefault = false
                }
            }
        }
    )
}
