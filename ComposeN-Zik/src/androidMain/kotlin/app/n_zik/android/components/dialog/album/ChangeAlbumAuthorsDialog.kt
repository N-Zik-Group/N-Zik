package app.n_zik.android.components.dialog.album

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.models.Album
import app.n_zik.android.components.dialog.song.RenameDialog
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.migration.NameConvergence
import timber.log.Timber

class ChangeAlbumAuthorsDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val getAlbum: () -> Album?
) : RenameDialog(activeState, valueState) {

    companion object {
        @Composable
        operator fun invoke( getAlbum: () -> Album? ): ChangeAlbumAuthorsDialog =
            ChangeAlbumAuthorsDialog(
                remember { mutableStateOf(false) },
                remember {
                    mutableStateOf( TextFieldValue(cleanPrefix(getAlbum()?.authorsText ?: "")) )
                },
                getAlbum
            )
    }

    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default
    override val iconId: Int = R.drawable.artists_edit
    override val messageId: Int = R.string.update_authors
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )
    override val dialogTitle: String
        @Composable
        get() = menuIconTitle

    override fun hideDialog() {
        super.hideDialog()
        value = TextFieldValue(cleanPrefix(getAlbum()?.authorsText ?: ""))
    }

    override fun onSet( newValue: String ) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        val album = getAlbum() ?: return
        // A 1:1 rename (exactly one token changed) that matches exactly one row linked
        // to this album's songs renames that row (modified:) and propagates to all of
        // its copies — before the local write, in the same transaction. 0 or 2+
        // candidate rows: nothing moves on the rows, the local custom write happens only.
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
