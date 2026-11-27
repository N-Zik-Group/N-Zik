package app.n_zik.android.components.menu.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.DialogActionList
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.AboveMenuDialogPresence
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon

internal enum class CollectionImportStatus(val unavailableMessage: Int?) {
    Loading(R.string.common_loading_message),
    Ready(null),
    Empty(R.string.player_no_song_found),
    Unavailable(R.string.common_load_failed);

    val canOpenImport: Boolean get() = this == Loading || this == Ready
}

internal fun MenuState.isPlaylistChoiceOpen(entry: MenuState.Entry, sourceId: String): Boolean =
    isDisplayed && presentationState.second === entry && entry.playlistActionsSourceId == sourceId

internal fun playlistActionsAnchor(
    order: List<String>,
    exportId: String = "export_dialog",
): String? = order.firstOrNull { it == "add_to_playlist" || it == "import_menu" || it == exportId }

@Composable
internal fun rememberCollectionAddAction(
    sourceId: String,
    menuState: MenuState,
    canImportCollection: Boolean,
    hasTracks: Boolean,
    importDialogOpen: Boolean = false,
    menuEntry: MenuState.Entry? = null,
    openImport: (onAccepted: () -> Unit) -> Unit,
    openPicker: (onReturn: (() -> Unit)?) -> Unit,
    importFromCurrentCollection: Boolean = true,
    canExport: Boolean = false,
    openExport: (() -> Unit)? = null,
): MenuIcon {
    val exportAvailable = canExport && openExport != null
    val hasAvailableAction by rememberUpdatedState(hasTracks || canImportCollection || exportAvailable)
    var toolbarChoiceOpen by remember(sourceId) { mutableStateOf(false) }
    val choiceOpen = menuEntry?.let { menuState.isPlaylistChoiceOpen(it, sourceId) } ?: toolbarChoiceOpen
    fun setChoiceOpen(open: Boolean) {
        when {
            menuEntry == null -> toolbarChoiceOpen = open
            open && menuState.isDisplayed && menuState.presentationState.second === menuEntry ->
                menuEntry.playlistActionsSourceId = sourceId
            !open && menuEntry.playlistActionsSourceId == sourceId ->
                menuEntry.playlistActionsSourceId = null
        }
    }

    PlaylistImportAddDialog(
        visible = choiceOpen && !importDialogOpen,
        canAdd = hasTracks,
        canImport = canImportCollection,
        importFromCurrentCollection = importFromCurrentCollection,
        canExport = exportAvailable,
        onExport = openExport?.let { export ->
            {
                setChoiceOpen(false)
                if (menuState.isDisplayed) menuState.hide()
                export()
            }
        },
        menuState = menuState,
        onDismiss = { setChoiceOpen(false) },
        onImport = { openImport { setChoiceOpen(false) } },
        onAdd = {
            val hasAlternative = canImportCollection || exportAvailable
            if (menuEntry == null || !hasAlternative) setChoiceOpen(false)
            val onReturn = if (menuEntry == null && hasAlternative) {
                { setChoiceOpen(true) }
            } else null
            openPicker(onReturn)
        },
    )
    return object : MenuIcon, Descriptive {
        override val iconId = R.drawable.playlist
        override val messageId = R.string.playlist_actions_title
        override val isEnabled: Boolean get() = hasAvailableAction
        @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
        override fun onShortClick() {
            if (isEnabled) setChoiceOpen(true)
        }
    }
}

internal class PlaylistActionsRequest(
    val openPicker: (((() -> Unit)?) -> Unit)?,
    val canImportFiles: Boolean,
    val parentMenu: MenuState.Entry?,
) {
    fun isOnSource(menuState: MenuState): Boolean =
        parentMenu == null || (menuState.isDisplayed && menuState.presentationState.second === parentMenu)
}

internal val LocalPlaylistAddChoice = staticCompositionLocalOf<((PlaylistActionsRequest) -> Unit)?> { null }

@Composable
internal fun PlaylistImportAddDialog(
    visible: Boolean,
    canAdd: Boolean,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onAdd: () -> Unit,
    menuState: MenuState,
    canImport: Boolean = true,
    importFromCurrentCollection: Boolean = false,
    canExport: Boolean = false,
    onExport: (() -> Unit)? = null,
) {
    AboveMenuDialogPresence(menuState, visible) {
        DialogActionList(
            title = stringResource(R.string.playlist_actions_title),
            onDismiss = onDismiss,
            actions = listOf(
                DialogButton(
                    text = stringResource(R.string.playlist_action_add),
                    icon = R.drawable.add_in_playlist,
                    enabled = canAdd,
                    onClick = onAdd,
                ),
                DialogButton(
                    text = stringResource(R.string.playlist_action_import_collection),
                    icon = R.drawable.import_playlist,
                    enabled = canImport && importFromCurrentCollection,
                    onClick = onImport,
                ),
                DialogButton(
                    text = stringResource(R.string.playlist_action_import_from_source),
                    icon = R.drawable.import_playlist_source,
                    enabled = canImport && !importFromCurrentCollection,
                    onClick = onImport,
                ),
                DialogButton(
                    text = stringResource(R.string.playlist_action_export),
                    icon = R.drawable.export_playlist,
                    enabled = canExport && onExport != null,
                    onClick = { onExport?.invoke() },
                ),
            ),
        )
    }
}
