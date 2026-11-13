package app.n_zik.android.components.dialog.playlist

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import app.n_zik.android.models.Playlist
import app.n_zik.android.cleanPrefix
import app.n_zik.android.R
import app.n_zik.android.components.dialog.song.RenameDialog
import app.n_zik.android.core.database.Database

class RenamePlaylistDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,

    private val getPlaylist: () -> Playlist?,
): RenameDialog(activeState, valueState) {

    companion object {
        @Composable
        operator fun invoke(getPlaylist: () -> Playlist?): RenamePlaylistDialog {
            val current = rememberUpdatedState(getPlaylist)
            return remember(getPlaylist()?.id) {
                RenamePlaylistDialog(mutableStateOf(false), mutableStateOf(TextFieldValue())) { current.value() }
            }
        }
    }

    override val iconId: Int = R.drawable.title_edit
    override val messageId: Int = R.string.playlist_rename_dialog_title
    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.playlist_name_input_hint )
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun showDialog() {
        value = TextFieldValue(getPlaylist()?.cleanName().orEmpty())
        errorMessage = ""
        super.showDialog()
    }

    override fun onSet( newValue: String ) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        val playlist = getPlaylist() ?: return
        val storedPrefixes = playlist.name.dropLast(cleanPrefix(playlist.name).length)

        Database.asyncTransaction {
            playlist.copy( name = storedPrefixes + newValue )
                    .let( playlistTable::update )
        }

        hideDialog()
    }
}
