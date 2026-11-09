package app.n_zik.android.components.dialog.song

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import timber.log.Timber
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.models.Song
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.migration.NameConvergence

class ChangeAuthorDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val getSong: () -> Song?
) : RenameDialog(activeState, valueState) {

    companion object {
        @Composable
        operator fun invoke(getSong: () -> Song?): ChangeAuthorDialog {
            val current = rememberUpdatedState(getSong)
            return remember(getSong()?.id) {
                ChangeAuthorDialog(mutableStateOf(false), mutableStateOf(TextFieldValue())) { current.value() }
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
        value = TextFieldValue(getSong()?.cleanArtistsText() ?: "")
        errorMessage = ""
        super.showDialog()
    }

    override fun onSet( newValue: String ) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        val song = getSong() ?: return
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
