package app.n_zik.android.components.dialog.settings

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.utils.preferences
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.SettingsNestedOption
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.n_zik.android.components.navigation.dock.DockToolbarCatalogue
import app.n_zik.android.components.navigation.dock.DockToolbarPreferences
import app.n_zik.android.components.navigation.dock.DockToolbarTab
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Reorders expanded dock tabs with the same drag interaction as other toolbar dialogs. */
object DockToolbarSettingsDialog : ContentFramedDialog {

    /** Exact searchable options from the same definitions used by this dialog. */
    internal fun searchOptions(): List<SettingsNestedOption> = buildHomeTabDefs().values.map { tab ->
        val id = when (tab.id) {
            "quickpicks" -> "sparkles"
            "songs" -> "musical_notes"
            "artists" -> "people"
            "albums" -> "album"
            else -> "library"
        }
        SettingsNestedOption(id, tab.labelRes)
    }

    override val dialogTitle: String
        @Composable get() = stringResource(R.string.floating_dock_toolbar_order)
    override var isActive: Boolean by mutableStateOf(false)

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        val prefs = context.preferences
        val fallback = buildHomeTabDefs().values.map { tab ->
            DockToolbarTab(context.resources.getResourceEntryName(tab.iconRes), stringResource(tab.labelRes), tab.iconRes)
        }
        val tabs = remember { (DockToolbarCatalogue.entries + fallback).associateBy { it.id }.values.toList() }
        val defaultOrder = remember(tabs) { tabs.map { it.id } }
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
                val tab = tabs.first { it.id == id }
                ToggleItem(tab.id, tab.iconRes, tab.title, "", true)
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
                Toaster.s(R.string.toast_preference_saved)
                hideDialog()
            },
        )
    }
}
