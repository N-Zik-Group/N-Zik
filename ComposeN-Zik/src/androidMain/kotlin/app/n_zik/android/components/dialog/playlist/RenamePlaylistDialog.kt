package app.n_zik.android.components.dialog.playlist

import app.n_zik.android.core.database.*

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.models.Playlist
import app.n_zik.android.components.dialog.song.RenameDialog
import java.util.UUID

class RenamePlaylistDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,

    private val getPlaylist: () -> Playlist?,
): RenameDialog(activeState, valueState) {

    companion object {
        @Composable
        operator fun invoke( getPlaylist: () -> Playlist? ) =
            RenamePlaylistDialog(
                remember { mutableStateOf(false) },
                remember( getPlaylist()?.name ) {
                    mutableStateOf( TextFieldValue(getPlaylist()?.name ?: "") )
                },

                getPlaylist
            )
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

    override fun hideDialog() {
        super.hideDialog()
        // Always reset string so when dialog turns
        // back on it will not show previous value.
        value = TextFieldValue(getPlaylist()?.name ?: "")
    }

    override fun onSet( newValue: String ) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        val playlist = getPlaylist() ?: return

        Database.asyncTransaction {
            playlist.copy( name = newValue )
                    .let( playlistTable::update )
        }

        hideDialog()
    }
}


