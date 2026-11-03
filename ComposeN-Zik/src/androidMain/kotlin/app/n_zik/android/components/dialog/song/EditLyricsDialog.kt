package app.n_zik.android.components.dialog.song

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.components.dialog.common.TextInputDialog
import app.n_zik.android.components.dialog.common.InputDialogConstraints
import app.n_zik.android.core.database.Database
import app.n_zik.android.models.Lyrics
import app.n_zik.android.enums.lyrics.LyricsType
import androidx.compose.material3.Text

class EditLyricsDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val mediaId: String,
    private val lyricsType: LyricsType,
    private val getLyrics: () -> Lyrics?,
    private val ensureSongInserted: () -> Unit
) : TextInputDialog(InputDialogConstraints.ALL) {

    companion object {
        /** Row saved when the user confirms an edit; always flagged so automatic fetches keep it (gh-765). */
        internal fun editedLyrics(mediaId: String, lyricsType: LyricsType, newValue: String): Lyrics =
            Lyrics(
                songId = mediaId,
                type = lyricsType.name,
                data = newValue,
                isEdited = true
            )

        @Composable
        operator fun invoke(
            mediaId: String,
            lyricsType: LyricsType,
            getLyrics: () -> Lyrics?,
            ensureSongInserted: () -> Unit
        ): EditLyricsDialog {
            val lyrics = getLyrics()
            val initialText = lyrics?.data
            
            return EditLyricsDialog(
                remember { mutableStateOf(false) },
                remember(initialText) { mutableStateOf(TextFieldValue(initialText ?: "")) },
                mediaId,
                lyricsType,
                getLyrics,
                ensureSongInserted
            )
        }
    }

    override var value: TextFieldValue by valueState
    override var isActive: Boolean by activeState

    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default
    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.lyrics_editor_title)

    fun onShortClick() = showDialog()

    override fun hideDialog() {
        super.hideDialog()
        val lyrics = getLyrics()
        val text = lyrics?.data
        value = TextFieldValue(text ?: "")
    }

    @Composable
    override fun DialogBody() {
        TextField(
            value = value,
            onValueChange = { value = it },
            placeholder = { 
                Text(
                    text = stringResource(R.string.lyrics_editor_title),
                    color = colorPalette().textDisabled
                ) 
            },
            maxLines = Int.MAX_VALUE,
            singleLine = false,
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth().heightIn(
                max = (LocalConfiguration.current.screenHeightDp * 0.4f).dp
            ),
            colors = InputDialog.defaultTextFieldColors()
        )
    }

    override fun onSet(newValue: String) {
        super.onSet(newValue)
        val lyrics = getLyrics()
        Database.asyncTransaction {
            ensureSongInserted()
            Database.lyricsTable.upsert(editedLyrics(mediaId, lyricsType, newValue))
        }
        hideDialog()
    }
}



