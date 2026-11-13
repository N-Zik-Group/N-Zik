package app.n_zik.android.components.dialog.album

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import timber.log.Timber
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.cleanPrefix
import app.n_zik.android.models.Album
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.components.dialog.song.RenameDialog
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.migration.NameConvergence

class ChangeAlbumAuthorsDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val getAlbum: () -> Album?
) : RenameDialog(activeState, valueState) {

    companion object {
        @Composable
        operator fun invoke(getAlbum: () -> Album?): ChangeAlbumAuthorsDialog {
            val current = rememberUpdatedState(getAlbum)
            return remember(getAlbum()?.id) {
                ChangeAlbumAuthorsDialog(mutableStateOf(false), mutableStateOf(TextFieldValue())) { current.value() }
            }
        }
    }

    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default
    override val iconId: Int = R.drawable.artists_edit
    override val messageId: Int = R.string.metadata_action_change_artists
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )
    override val dialogTitle: String
        @Composable
        get() = menuIconTitle

    override fun showDialog() {
        value = TextFieldValue(cleanPrefix(getAlbum()?.authorsText ?: ""))
        errorMessage = ""
        super.showDialog()
    }

    override fun onSet( newValue: String ) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        val album = getAlbum() ?: return
        val rename = NameConvergence.diffSingleRename( cleanPrefix(album.authorsText ?: ""), newValue )
        // Normalize the user input for the local custom write: clean tokens, no
        // prefix ever at a token position (single leading marker).
        val cleanNew = NameConvergence.normalizeText( newValue )
        // Blank input would store a bare "modified:" authorsText: never write it.
        if (cleanNew.isEmpty()) {
            Timber.tag("NameConvergence").d("album author edit skipped: blank input for %s", album.id)
            return
        }
        Database.asyncTransaction {
            // Row rename + propagation + local write land in ONE transaction.
            syncTransaction {
                rename?.let { ( oldToken, newToken ) ->
                    val linkedArtists = songAlbumMapTable.allSongsOfDirect( album.id )
                        .flatMap { songArtistMapTable.findArtistsOfDirect( it.id ) }
                        .distinctBy { it.id }
                    NameConvergence.uniqueCandidate( linkedArtists, oldToken )
                        ?.let { row ->
                            artistTable.update( row.copy(name = "$MODIFIED_PREFIX$newToken") )
                            NameConvergence.propagateRename( this, row.id, oldToken, newToken )
                        }
                }
                albumTable.insertIgnore( album )
                albumTable.updateAuthors( album.id, "$MODIFIED_PREFIX$cleanNew" )
            }
            Toaster.done()
        }

        hideDialog()
    }
}
