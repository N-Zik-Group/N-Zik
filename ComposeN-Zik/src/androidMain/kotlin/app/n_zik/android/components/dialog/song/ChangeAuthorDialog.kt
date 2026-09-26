package app.n_zik.android.components.dialog.song

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
import app.n_zik.android.core.migration.NameConvergence
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.components.dialog.song.RenameDialog
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber

class ChangeAuthorDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val getSong: () -> Song?
) : RenameDialog(activeState, valueState) {

    companion object {
        @Composable
        operator fun invoke( getSong: () -> Song? ): ChangeAuthorDialog =
            ChangeAuthorDialog(
                remember { mutableStateOf(false) },
                remember {
                    mutableStateOf( TextFieldValue(getSong()?.cleanArtistsText() ?: "") )
                },
                getSong
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
        // Always reset string so when dialog turns
        // back on it will not show previous value.
        value = TextFieldValue(getSong()?.cleanArtistsText() ?: "")
    }

    override fun onSet( newValue: String ) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        val song = getSong() ?: return
        // A 1:1 rename (exactly one token changed) that matches exactly one linked
        // row renames that row (modified:) and propagates to all of its copies —
        // before the local write, in the same transaction. 0 or 2+ candidate rows:
        // nothing moves on the rows, the local custom write happens only.
        val rename = NameConvergence.diffSingleRename( song.cleanArtistsText(), newValue )
        // Normalize the user input for the local custom write: clean tokens, no
        // prefix ever at a token position (single leading marker).
        val cleanNew = NameConvergence.normalizeText( newValue )
        // Blank input would store a bare "modified:" artistsText: never write it.
        if (cleanNew.isEmpty()) {
            Timber.tag("NameConvergence").d("author edit skipped: blank input for song %s", song.id)
            return
        }
        Database.asyncTransaction {
            // Row rename + propagation + local write land in ONE transaction.
            syncTransaction {
                rename?.let { ( oldToken, newToken ) ->
                    NameConvergence.uniqueCandidate( songArtistMapTable.findArtistsOfDirect( song.id ), oldToken )
                        ?.let { row ->
                            artistTable.update( row.copy(name = "$MODIFIED_PREFIX$newToken") )
                            NameConvergence.propagateRename( this, row.id, oldToken, newToken )
                        }
                }
                songTable.insertIgnore( song )
                songTable.updateArtists( song.id, "$MODIFIED_PREFIX$cleanNew" )
            }
            Toaster.done()
        }

        hideDialog()
    }

}

