package app.n_zik.android.components.dialog.artist

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.models.Artist
import app.n_zik.android.components.dialog.song.RenameDialog
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.migration.NameConvergence
import kotlinx.coroutines.launch
import timber.log.Timber

class ChangeArtistTitleDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val getArtist: () -> Artist?
) : RenameDialog(activeState, valueState) {

    companion object {
        @Composable
        operator fun invoke( getArtist: () -> Artist? ): ChangeArtistTitleDialog =
            ChangeArtistTitleDialog(
                remember { mutableStateOf(false) },
                remember {
                    mutableStateOf( TextFieldValue(cleanPrefix(getArtist()?.name ?: "")) )
                },
                getArtist
            )
    }

    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default
    override val iconId: Int = R.drawable.title_edit
    override val messageId: Int = R.string.update_title
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )
    override val dialogTitle: String
        @Composable
        get() = menuIconTitle

    override fun hideDialog() {
        super.hideDialog()
        value = TextFieldValue(cleanPrefix(getArtist()?.name ?: ""))
    }

    override fun onSet( newValue: String ) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        val artist = getArtist() ?: return
        // The old name must be read (cleaned) BEFORE the row is rewritten below.
        val oldName = cleanPrefix(artist.name ?: "").trim()
        // Normalize the user input (clean tokens, no prefix ever at a token
        // position) before it is stored or propagated.
        val cleanNew = NameConvergence.normalizeText( newValue )
        // A row name is ONE name: blank input would store a bare "modified:"
        // row, and a comma (multi-token) input would break the whole-token
        // matching rules for every copy of the row.
        if (cleanNew.isEmpty()) {
            Timber.tag("NameConvergence").d("artist rename skipped: blank input for row %s", artist.id)
            return
        }
        if (cleanNew.contains(',')) {
            Timber.tag("NameConvergence").d(
                "artist rename skipped: multi-token input \"%s\" for row %s", cleanNew, artist.id
            )
            return
        }
        // No change: the row (and its modified: marker) is kept as-is and the
        // full propagation scan is skipped.
        if (cleanNew.equals(oldName, ignoreCase = true)) return
        Database.asyncTransaction {
            // The row rename and every propagated copy must land in ONE real
            // SQLite transaction (asyncTransaction alone retries on lock
            // exceptions but does not group the writes atomically).
            syncTransaction {
                artistTable.insertIgnore( artist )
                artistTable.update( artist.copy(name = "$MODIFIED_PREFIX$cleanNew") )
                // Converge every copy linked to this row (songs + albums of its
                // songs) in the same transaction as the row rename.
                NameConvergence.propagateRename( this, artist.id, oldName, cleanNew )
            }
            Toaster.done()
        }
        hideDialog()
    }
}
