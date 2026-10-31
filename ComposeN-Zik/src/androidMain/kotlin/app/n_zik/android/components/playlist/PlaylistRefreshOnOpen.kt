package app.n_zik.android.components.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.tab.StatefulMenuIcon
import app.n_zik.android.core.database.canRefreshPlaylist
import app.n_zik.android.core.database.isReadOnlyPlaylistSource
import app.n_zik.android.utils.playlist.PlaylistRefreshPreferences

/** Toolbar and menu action share the same profile-scoped per-playlist preference. */
class PlaylistRefreshOnOpenAction(
    readEnabled: () -> Boolean,
    onToggle: () -> Unit,
    menuState: MenuState,
) : StatefulMenuIcon(
    menuState = menuState,
    messageId = R.string.playlist_refresh_on_open,
    readActive = readEnabled,
    readIcon = { if (readEnabled()) R.drawable.sync_off else R.drawable.sync },
    readTitle = {
        if (readEnabled()) R.string.playlist_refresh_on_open_disable else R.string.playlist_refresh_on_open_enable
    },
    performAction = onToggle,
) {
    val enabled: Boolean get() = active
}

/** Presents a stable per-playlist switch without enabling account synchronization. */
@Composable
fun playlistRefreshOnOpenAction(playlist: Playlist?): PlaylistRefreshOnOpenAction {
    val id = playlist?.id ?: 0L
    val menuState = LocalMenuState.current
    return key(id, playlist?.browseId, playlist?.isEditable) {
        val enabled = rememberPreference(
            PlaylistRefreshPreferences.key(id),
            playlist?.let(::isReadOnlyPlaylistSource) == true,
        )
        remember(enabled, menuState) {
            PlaylistRefreshOnOpenAction(
                readEnabled = { enabled.value },
                onToggle = { enabled.value = !enabled.value },
                menuState = menuState,
            )
        }
    }
}

/** One attempt per screen visit, after the row arrives; preference changes do not refetch. */
@Composable
fun RefreshPlaylistOnOpening(
    playlistId: Long,
    playlist: Playlist?,
    offline: Boolean,
    onRefresh: ((Playlist) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attempted by remember(playlistId) { mutableStateOf(false) }
    LaunchedEffect(playlistId, playlist?.browseId, playlist?.isEditable) {
        if (playlist == null || attempted) return@LaunchedEffect
        attempted = true
        if (!offline && canRefreshPlaylist(playlist) && PlaylistRefreshPreferences(context).get(playlist)) {
            if (onRefresh != null) onRefresh(playlist)
            else launchPlaylistSourceRefresh(playlist, scope, silent = true)
        }
    }
}
