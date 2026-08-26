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
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.components.dialog.song.RenameDialog
import app.kreate.android.me.knighthat.utils.Toaster
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.hasExplicitPrefix
import app.n_zik.android.colorPalette
import kotlinx.coroutines.flow.first
import app.it.fast4x.rimusic.cleanPrefix

class RenameSongDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val getSong: () -> Song?
) : RenameDialog(activeState, valueState) {

    companion object {
        @Composable
        operator fun invoke( getSong: () -> Song? ): RenameSongDialog =
            RenameSongDialog(
                remember { mutableStateOf(false) },
                remember {
                    mutableStateOf( TextFieldValue(getSong()?.cleanTitle() ?: "") )
                },
                getSong
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

    // Tracks what is actually saved in DB (initialized once from the fresh song at menu-open)
    // Never re-reads getSong() — that lambda always returns the stale song captured at menu open.
    private var committedExplicit = getSong()?.title?.hasExplicitPrefix() == true

    var isExplicit by mutableStateOf(committedExplicit)

    override fun showDialog() {
        super.showDialog()
    }

    @Composable
    override fun DialogBody() {
        // Re-read explicit state from DB every time dialog becomes visible
        LaunchedEffect(isActive) {
            if (isActive) {
                val song = getSong()
                val dbTitle = song?.id?.let { id ->
                    runCatching {
                        Database.songTable.findById(id).first()?.title
                    }.getOrNull()
                }
                committedExplicit = (dbTitle ?: song?.title ?: "").hasExplicitPrefix()
                isExplicit = committedExplicit
                value = TextFieldValue((dbTitle ?: song?.title ?: "").let { t ->
                    cleanPrefix(t)
                })
            }
        }
        Column {
            super.DialogBody()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable { isExplicit = !isExplicit }
            ) {
                Icon(
                    painter = painterResource(if (isExplicit) R.drawable.checked_filled else R.drawable.unchecked_outline),
                    contentDescription = null,
                    tint = if (isExplicit) colorPalette().text else colorPalette().textSecondary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = stringResource(R.string.explicit),
                    color = colorPalette().text,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    override fun hideDialog() {
        super.hideDialog()
        // User cancelled — revert UI to last committed state
        value = TextFieldValue(getSong()?.cleanTitle() ?: "")
        isExplicit = committedExplicit
    }

    override fun onSet(newValue: String) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        val song = getSong() ?: return
        Database.asyncTransaction {
            songTable.insertIgnore( song )
            val prefix = if (isExplicit) "$MODIFIED_PREFIX$EXPLICIT_PREFIX" else MODIFIED_PREFIX
            songTable.updateTitle( song.id, "$prefix$newValue" )
            Toaster.done()
        }

        // Commit the chosen explicit state and close
        committedExplicit = isExplicit
        super.hideDialog()
        value = TextFieldValue(newValue)
    }
}


