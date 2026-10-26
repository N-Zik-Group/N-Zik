package app.n_zik.android.components.settings.general

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.dialog.settings.AppShortcutsSettingsDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard

/** App Shortcuts card: single entry opening the shortcuts settings dialog. */
@Composable
fun GeneralAppShortcutsCard(
    visible: Boolean,
) {
    SettingsSectionCard(
        title = stringResource(R.string.app_shortcuts_settings),
        icon = R.drawable.shortcut_rescue,
        visible = visible,
        content = {
            OtherSettingsEntry(
                title = stringResource(R.string.app_shortcuts_settings),
                text = stringResource(R.string.app_shortcuts_settings_description),
                icon = R.drawable.shortcut_rescue,
                onClick = { AppShortcutsSettingsDialog.showDialog() }
            )
        }
    )
}
