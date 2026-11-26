package app.n_zik.android.components.dialog.playlist

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import app.n_zik.android.R
import app.n_zik.android.components.dialog.song.RenameDialog
import app.n_zik.android.core.database.Database
import app.n_zik.android.models.Playlist

internal class RenamePlaylistAuthorDialog private constructor(
    active: MutableState<Boolean>,
    input: MutableState<TextFieldValue>,
    private val getPlaylist: () -> Playlist?,
) : RenameDialog(active, input) {

    companion object {
        @Composable
        operator fun invoke(getPlaylist: () -> Playlist?): RenamePlaylistAuthorDialog {
            val current = rememberUpdatedState(getPlaylist)
            return remember(getPlaylist()?.id) {
                RenamePlaylistAuthorDialog(mutableStateOf(false), mutableStateOf(TextFieldValue())) {
                    current.value()
                }
            }
        }
    }

    override val allowEmpty = true
    override val iconId = R.drawable.artists_edit
    override val messageId = R.string.metadata_action_change_artists
    override val keyboardOption = KeyboardOptions.Default

    override val menuIconTitle: String
        @Composable get() = stringResource(messageId)

    override val dialogTitle: String
        @Composable get() = menuIconTitle

    override fun showDialog() {
        value = TextFieldValue(getPlaylist()?.displayAuthorName().orEmpty())
        errorMessage = ""
        super.showDialog()
    }

    override fun onSet(newValue: String) {
        super.onSet(newValue)
        if (errorMessage.isNotEmpty()) return
        val playlist = getPlaylist() ?: return
        val name = newValue.trim().takeIf { it.isNotEmpty() && it != playlist.authorName }
        Database.asyncTransaction { playlistTable.updateAuthorNameOverride(playlist.id, name) }
        hideDialog()
    }
}
