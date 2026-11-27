package app.n_zik.android.components.menu.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.media.YouTubeLinkImportDialog
import app.n_zik.android.components.dialog.playlist.ImportPlaylistFormatDialog
import app.n_zik.android.components.playlist.launchPlaylistLinkImport
import app.n_zik.android.components.tab.ImportSongsFromCSV
import app.n_zik.android.components.tab.ImportSongsFromServices

internal val LocalLibraryPlaylistImport = staticCompositionLocalOf<ImportPlaylistFormatDialog?> { null }

@Composable
internal fun rememberLibraryPlaylistImportMenu(offlineMode: Boolean): ImportPlaylistFormatDialog {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allowNetwork by rememberUpdatedState(!offlineMode)
    var showYouTubeLink by remember { mutableStateOf(false) }
    val csv = ImportSongsFromCSV()
    val spotify = ImportSongsFromServices.init(source = "SPOTIFY_IMPORT")
    val riplay = ImportSongsFromServices.init(source = "RIPLAY_IMPORT")
    val options = remember {
        ImportPlaylistFormatDialog(
            onImportNzik = csv::onShortClick,
            onImportSpotify = spotify::onShortClick,
            onImportRiplay = riplay::onShortClick,
            onImportYoutubeLink = { showYouTubeLink = true },
            networkOptionsEnabled = { allowNetwork },
        )
    }
    DialogPresence(visible = showYouTubeLink) {
        YouTubeLinkImportDialog(
            onImport = { playlistId -> launchPlaylistLinkImport(context, scope, playlistId) },
            onDismiss = { showYouTubeLink = false },
        )
    }
    options.Render()
    return options
}
