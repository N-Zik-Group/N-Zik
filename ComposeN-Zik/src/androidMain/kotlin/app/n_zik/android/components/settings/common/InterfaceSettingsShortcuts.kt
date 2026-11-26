package app.n_zik.android.components.settings.common

import androidx.compose.runtime.Composable

@Composable
internal fun InterfaceSettingsShortcuts(onNavigate: (SettingsPageItem) -> Unit) {
    SettingsResultCard(testTag = "settings.interface.shortcuts") {
        listOf(SettingsThemeStoreItem, SettingsGlassItem).forEach { item ->
            GlobalPageItemRow(item, onItemSelected = onNavigate)
        }
    }
}
