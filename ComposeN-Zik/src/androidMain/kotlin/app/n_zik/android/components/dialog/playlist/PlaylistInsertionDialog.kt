package app.n_zik.android.components.dialog.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Dialog
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.utils.playlist.PlaylistInsertionPosition
import app.n_zik.android.utils.playlist.PlaylistInsertionPreferences
import app.n_zik.android.utils.playlist.supportsInsertionPosition

/** Shared playlist-menu and detail-toolbar entry for canonical insertion order. */
class PlaylistInsertionDialog private constructor(
    active: MutableState<Boolean>,
    private val preferences: PlaylistInsertionPreferences,
    private val getPlaylist: () -> Playlist?
) : Dialog, MenuIcon, Descriptive {
    override var isActive: Boolean by active
    override val iconId: Int = R.drawable.sort_vertical
    override val messageId: Int = R.string.playlist_insertion_position
    override val menuIconTitle: String
        @Composable get() = stringResource(messageId)
    override val dialogTitle: String
        @Composable get() = menuIconTitle

    override fun onShortClick() { isActive = true }

    @Composable
    override fun Render() {
        DialogPresence(target = getPlaylist()?.takeIf { isActive && it.supportsInsertionPosition() }) { playlist ->
        ValueSelectorDialog(
            onDismiss = { isActive = false },
            title = dialogTitle,
            titleSecondary = stringResource(R.string.playlist_insertion_description),
            selectedValue = preferences.get(playlist.id),
            values = PlaylistInsertionPosition.entries,
            onValueSelected = { preferences.set(playlist.id, it) },
            valueText = {
                stringResource(if (it == PlaylistInsertionPosition.Beginning)
                    R.string.playlist_insertion_beginning else R.string.playlist_insertion_end)
            }
        )
        }
    }

    companion object {
        @Composable
        operator fun invoke(getPlaylist: () -> Playlist?): PlaylistInsertionDialog {
            val context = LocalContext.current
            return PlaylistInsertionDialog(
                remember { mutableStateOf(false) },
                remember(context) { PlaylistInsertionPreferences(context) },
                getPlaylist
            )
        }
    }
}
