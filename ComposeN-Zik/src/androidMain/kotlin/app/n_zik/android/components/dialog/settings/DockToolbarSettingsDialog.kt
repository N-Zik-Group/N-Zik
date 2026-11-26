package app.n_zik.android.components.dialog.settings

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.n_zik.android.utils.preferences
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.SettingsNestedOption
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.n_zik.android.components.navigation.dock.DockToolbarPreferences
import app.n_zik.android.components.navigation.dock.DockToolbarTab
import app.n_zik.android.components.navigation.dock.artistDockTabs
import app.n_zik.android.components.navigation.dock.searchDockTabs
import app.n_zik.android.components.navigation.dock.searchResultDockTabs
import app.n_zik.android.components.navigation.dock.statisticsDockTabs
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Reorders expanded dock tabs with the same drag interaction as other toolbar dialogs. */
object DockToolbarSettingsDialog : ContentFramedDialog {

    /** Exact searchable options from the same definitions used by this dialog. */
    private val homeTabs = buildHomeTabDefs().values.map { tab ->
        val id = when (tab.id) {
            "quickpicks" -> "sparkles"
            "songs" -> "musical_notes"
            "artists" -> "people"
            "albums" -> "album"
            else -> "library"
        }
        DockToolbarTab(id, tab.labelRes, tab.iconRes)
    }
    private val tabsById = (homeTabs + searchDockTabs + searchResultDockTabs + statisticsDockTabs + artistDockTabs)
        .groupBy { it.id }
    private val tabs = tabsById.mapValues { (_, sameIcon) -> sameIcon.first() }

    internal fun searchOptions(): List<SettingsNestedOption> = tabs.values.map { tab ->
        val alternateLabel = tabsById.getValue(tab.id).firstOrNull { it.labelRes != tab.labelRes }?.labelRes
        SettingsNestedOption(tab.id, tab.labelRes, descriptionRes = alternateLabel)
    }

    override val dialogTitle: String
        @Composable get() = stringResource(R.string.settings_dock_expanded_tab_order_label)
    override var isActive: Boolean by mutableStateOf(false)

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        val prefs = context.preferences
        val defaultOrder = tabs.keys.toList()
        var order by remember {
            mutableStateOf(DockToolbarPreferences.orderedIds(defaultOrder,
                prefs.getString(DockToolbarPreferences.orderKey, "").orEmpty()))
        }
        val listState = rememberLazyListState()
        val reorderable = rememberReorderableLazyListState(listState) { from, to ->
            val fromIndex = order.indexOf(from.key)
            val toIndex = order.indexOf(to.key)
            if (fromIndex >= 0 && toIndex >= 0) {
                order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            }
        }
        ToggleListDialog(
            frameTitle = dialogTitle,
            onDismiss = ::hideDialog,
            items = order.map { id ->
                val tab = tabs.getValue(id)
                ToggleItem(tab.id, tab.iconRes, stringResource(tab.labelRes), "", true)
            },
            lazyListState = listState,
            reorderableState = reorderable,
            lockedCheckedIds = order.toSet(),
            checkedStatesOverride = order.map { true },
            onReset = { order = defaultOrder },
            onCancel = ::hideDialog,
            onConfirm = {
                prefs.edit().apply {
                    if (order == defaultOrder) remove(DockToolbarPreferences.orderKey)
                    else putString(DockToolbarPreferences.orderKey, DockToolbarPreferences.serializeOrder(order))
                }.apply()
                Toaster.s(R.string.settings_preference_saved_message)
                hideDialog()
            },
        )
    }
}
