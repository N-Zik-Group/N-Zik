package app.n_zik.android.components.dialog.settings

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.n_zik.android.components.navigation.dock.DockToolbarPreferences
import app.n_zik.android.components.settings.common.SettingsNestedOption
import app.n_zik.android.components.settings.ui.ToolbarAction
import app.n_zik.android.components.settings.ui.ToolbarSurface
import app.n_zik.android.components.settings.ui.queuePlaylistActions
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.utils.preferences
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Toolbar settings rows also supply the search index and the dialogs' reset targets. */
internal open class CustomToolbarSettingsDialog(
    val entryId: String,
    val label: Int,
    val icon: Int,
    val surfaces: List<ToolbarSurface>,
    private val showToolbarTitleSuffix: Boolean = true,
) : ContentFramedDialog {
    val dialogId = "toolbar.$entryId"
    override val dialogTitle: String
        @Composable get() = if (showToolbarTitleSuffix) {
            stringResource(R.string.settings_dialog_toolbar_title_format, stringResource(label))
        } else stringResource(label)
    override var isActive by mutableStateOf(false)

    fun searchOptions(): List<SettingsNestedOption> = surfaces.flatMapIndexed { index, surface ->
        surface.editableActions.map { action ->
            SettingsNestedOption(
                itemId(surface, action), action.label, index, surface.label.takeIf { surfaces.size > 1 },
                descriptionRes = when {
                    action == ToolbarAction.ADD_TO_PLAYLIST -> R.string.playlist_action_add
                    surface == ToolbarSurface.QUEUE && action in queuePlaylistActions -> R.string.playlist_actions_title
                    action in surface.overflowActions -> R.string.common_menu_label
                    else -> null
                },
            )
        }
    }

    @Composable
    override fun DialogBody() {
        val prefs = LocalContext.current.preferences
        val tabTransition = rememberSettingsDialogTabTransition(surfaces.size, scopeKey = prefs)
        val tabIndex = tabTransition.displayedIndex
        var orders by remember(prefs) {
            mutableStateOf(surfaces.associateWith { toolbar ->
                toolbar.resolveOrder(prefs.getString(toolbar.orderKey, "").orEmpty()).filterNot { it == toolbar.fixedFirst }
            })
        }
        var visibility by remember(prefs) {
            mutableStateOf(surfaces.associateWith { surface ->
                surface.editableActions.associateWith { action ->
                    action in surface.locked || prefs.getBoolean(surface.visibilityKey(action), surface.defaultVisible(prefs, action))
                }
            })
        }
        val surface = surfaces[tabIndex]
        val order = orders.getValue(surface)
        val visible = visibility.getValue(surface)
        val lazyListState = rememberLazyListState()
        val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val moved = order.toMutableList()
            val fromIndex = moved.indexOfFirst { itemId(surface, it) == from.key }
            val toIndex = moved.indexOfFirst { itemId(surface, it) == to.key }
            if (fromIndex >= 0 && toIndex >= 0) {
                moved.add(toIndex, moved.removeAt(fromIndex))
                orders = orders + (surface to moved)
            }
        }
        val items = order.map { action ->
            ToggleItem(
                itemId(surface, action), action.icon, stringResource(action.label),
                surface.visibilityKey(action), surface.defaultVisible(prefs, action),
                description = when {
                    action in surface.overflowActions -> stringResource(R.string.common_menu_label)
                    else -> null
                },
            )
        }
        ToggleListDialog(
            items = items,
            lazyListState = lazyListState,
            reorderableState = reorderState,
            lockedCheckedIds = surface.locked.mapTo(mutableSetOf()) { itemId(surface, it) },
            checkedStatesOverride = order.map(visible::getValue),
            onCheckedChange = { index, checked ->
                visibility = visibility + (surface to (visible + (order[index] to checked)))
            },
            onReset = {
                orders = orders + (surface to surface.editableActions)
                visibility = visibility + (surface to surface.editableActions.associateWith { surface.defaultVisible(prefs, it) })
            },
            frameTitle = dialogTitle,
            onDismiss = ::hideDialog,
            onCancel = ::hideDialog,
            onConfirm = {
                val editor = prefs.edit()
                surfaces.forEach { toolbar ->
                    val ordered = orders.getValue(toolbar)
                    toolbar.fixedFirst?.let { editor.remove(toolbar.visibilityKey(it)) }
                    if (ordered == toolbar.editableActions) editor.remove(toolbar.orderKey)
                    else editor.putString(toolbar.orderKey,
                        DockToolbarPreferences.serializeOrder(ordered.map(ToolbarAction::id)))
                    visibility.getValue(toolbar).forEach { (action, checked) ->
                        if (action in toolbar.locked || checked == toolbar.defaultVisible(prefs, action)) {
                            editor.remove(toolbar.visibilityKey(action))
                        } else editor.putBoolean(toolbar.visibilityKey(action), checked)
                    }
                }
                editor.apply()
                Toaster.s(R.string.settings_preference_saved_message)
                hideDialog()
            },
            tabContentAlpha = if (surfaces.size > 1) ({ tabTransition.alpha }) else null,
            bodyHeader = {
                if (surfaces.size > 1) {
                    SettingsDialogTabRow(
                        labels = surfaces.map { stringResource(it.label) },
                        selectedTabIndex = tabTransition.selectedIndex,
                        onTabSelected = tabTransition::select,
                    )
                    Spacer(Modifier.fillMaxWidth().height(8.dp))
                }
            },
        )
    }

    private fun itemId(surface: ToolbarSurface, action: ToolbarAction): String = "${surface.id}.${action.id}"
}

internal fun playerTopThemeHiddenActions(showTopActionsBar: Boolean, menuInControls: Boolean): Set<ToolbarAction> =
    when {
        !showTopActionsBar -> setOf(ToolbarAction.COLLAPSE_PLAYER, ToolbarAction.MENU)
        menuInControls -> setOf(ToolbarAction.MENU)
        else -> emptySet()
    }

internal val customToolbarSettingsDialogs = listOf(
    AlbumDetailsToolbarSettingsDialog,
    ArtistDetailsToolbarSettingsDialog,
    OnlinePlaylistToolbarSettingsDialog,
    PodcastToolbarSettingsDialog,
    HeaderToolbarSettingsDialog,
    QueueToolbarSettingsDialog,
)

internal fun resetInterfaceToolbarSettings(context: Context) {
    val editor = context.preferences.edit()
    interfaceToolbarSettingsDialogs.flatMap { it.surfaces }.forEach { surface ->
        editor.remove(surface.orderKey)
        surface.actions.forEach { editor.remove(surface.visibilityKey(it)) }
    }
    editor.apply()
}
