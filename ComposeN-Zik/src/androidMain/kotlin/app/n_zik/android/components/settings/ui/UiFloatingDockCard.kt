package app.n_zik.android.components.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.dialog.settings.DockToolbarSettingsDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.settings.common.settingsSearchTarget
import app.n_zik.android.components.tab.Search

/** Dock preferences apply to Bottom Floating with either material mode. */
@Composable
internal fun UiFloatingDockCard(search: Search) {
    val title = stringResource(R.string.settings_dock_title)
    val orderTitle = stringResource(R.string.settings_dock_expanded_tab_order_label)
    val terms = listOf(title, orderTitle, stringResource(R.string.settings_library_toolbar_title))
    val showAll = search.inputValue.isBlank() || title.contains(search.inputValue, true)
    AnimatedVisibility(
        visible = showAll || terms.any { it.contains(search.inputValue, true) },
        enter = settingsCardEnter,
        exit = settingsCardExit,
    ) {
        SettingsSectionCard(
            modifier = Modifier.testTag("ui.card.floating-dock"),
            title = title,
            icon = R.drawable.ellipsis_horizontal,
            content = {
                if (showAll || orderTitle.contains(search.inputValue, true) || stringResource(R.string.settings_library_toolbar_title).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.floating_dock.toolbar_order"),
                        title = orderTitle,
                        text = stringResource(R.string.settings_dock_expanded_tab_order_description),
                        icon = R.drawable.reorder,
                        onClick = { DockToolbarSettingsDialog.showDialog() },
                    )
                }
            },
        )
    }
}
