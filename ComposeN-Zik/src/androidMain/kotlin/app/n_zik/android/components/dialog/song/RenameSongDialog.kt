package app.n_zik.android.components.dialog.song

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.hasExplicitPrefix
import app.it.fast4x.rimusic.models.Song
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.core.database.Database

class RenameSongDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val getSong: () -> Song?
) : RenameDialog(activeState, valueState) {

    companion object {
        @Composable
        operator fun invoke(getSong: () -> Song?): RenameSongDialog {
            val current = rememberUpdatedState(getSong)
            return remember(getSong()?.id) {
                RenameSongDialog(mutableStateOf(false), mutableStateOf(TextFieldValue())) { current.value() }
            }
        }
    }

    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default
    override val iconId: Int = R.drawable.title_edit
    override val messageId: Int = R.string.metadata_action_change_title
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )
    override val dialogTitle: String
        @Composable
        get() = menuIconTitle

    var isExplicit by mutableStateOf(false)

    override fun showDialog() {
        val song = getSong()
        value = TextFieldValue(song?.cleanTitle().orEmpty())
        isExplicit = song?.title?.hasExplicitPrefix() == true
        errorMessage = ""
        super.showDialog()
    }

    @Composable
    override fun DialogBody() {
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
                    text = stringResource(R.string.common_content_explicit_label),
                    color = colorPalette().text,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    override fun onSet(newValue: String) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        val song = getSong() ?: return
        val explicit = isExplicit
        Database.asyncTransaction {
            songTable.insertIgnore( song )
            val prefix = if (explicit) "$MODIFIED_PREFIX$EXPLICIT_PREFIX" else MODIFIED_PREFIX
            songTable.updateTitle( song.id, "$prefix$newValue" )
            Toaster.done()
        }

        super.hideDialog()
        value = TextFieldValue(newValue)
    }
}
