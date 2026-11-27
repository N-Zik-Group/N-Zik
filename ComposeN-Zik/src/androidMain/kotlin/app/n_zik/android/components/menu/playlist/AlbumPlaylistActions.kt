package app.n_zik.android.components.menu.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import app.n_zik.android.cleanPrefix
import app.n_zik.android.components.dialog.playlist.ImportPlaylistDialog
import app.n_zik.android.components.navigation.onlinePlaylistBrowseIdFromLink
import app.n_zik.android.components.playlist.launchLoadedPlaylistImport
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.Song
import app.n_zik.android.ui.components.MenuState

@Composable
internal fun rememberAlbumPlaylistActions(
    sourceId: String,
    sourcePlaylistId: String?,
    title: String?,
    author: String?,
    menuState: MenuState,
    importAvailable: Boolean,
    loading: Boolean,
    loadedTracks: List<Song>?,
    canAdd: Boolean,
    menuEntry: MenuState.Entry? = null,
    openPicker: (onReturn: (() -> Unit)?) -> Unit,
) = key(sourceId) {
    val context = LocalContext.current
    val status = when {
        loading -> CollectionImportStatus.Loading
        loadedTracks == null -> CollectionImportStatus.Unavailable
        loadedTracks.isEmpty() -> CollectionImportStatus.Empty
        else -> CollectionImportStatus.Ready
    }
    val importDialog = ImportPlaylistDialog(
        initialValue = cleanPrefix(title.orEmpty()),
        unavailableMessage = status.unavailableMessage,
    ) { name ->
        launchLoadedPlaylistImport(
            context,
            Playlist(
                name = name,
                browseId = sourcePlaylistId?.takeIf(String::isNotBlank)?.let(::onlinePlaylistBrowseIdFromLink),
                authorName = author?.let(::cleanPrefix)?.takeIf(String::isNotBlank),
            ),
            loadedTracks,
        )
    }
    importDialog.RenderAboveMenu(menuState)
    rememberCollectionAddAction(
        sourceId = sourceId,
        menuState = menuState,
        canImportCollection = importAvailable && status.canOpenImport,
        hasTracks = canAdd,
        importDialogOpen = importDialog.isActive,
        menuEntry = menuEntry,
        openImport = importDialog::open,
        openPicker = openPicker,
    )
}
