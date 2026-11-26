package app.n_zik.android.components.settings.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R

@Composable
internal fun InterfaceSettingsShortcuts(onNavigate: (SettingsPageItem) -> Unit) {
    SettingsSectionCard(
        title = stringResource(R.string.settings_interface_shortcuts_title),
        icon = R.drawable.link,
        modifier = Modifier.testTag("settings.interface.shortcuts"),
        content = {
            listOf(SettingsThemeStoreItem, SettingsGlassItem).forEach { item ->
                GlobalPageItemRow(item, onItemSelected = onNavigate)
            }
        },
    )
}
