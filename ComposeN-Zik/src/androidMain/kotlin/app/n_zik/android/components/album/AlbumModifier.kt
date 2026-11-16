package app.n_zik.android.components.album


import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import app.n_zik.android.core.database.Database
import app.n_zik.android.components.dialog.song.RenameDialog
import app.n_zik.android.core.database.AlbumTable

class AlbumModifier private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    override val iconId: Int,
    override val messageId: Int,
    private val getDefaultValue: () -> String,
    private val onConfirm: suspend AlbumTable.(String) -> Unit
): RenameDialog(activeState, valueState) {

    companion object {
        @Composable
        operator fun invoke(
            iconId: Int,
            messageId: Int,
            getDefaultValue: () -> String,
            onConfirm: suspend AlbumTable.(String) -> Unit
        ) = AlbumModifier(
            remember { mutableStateOf(false) },
            remember( getDefaultValue() ) {
                mutableStateOf( TextFieldValue(getDefaultValue()) )
            },
            iconId,
            messageId,
            getDefaultValue,
            onConfirm
        )
    }

    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default

    override val dialogTitle: String
        @Composable
        get() = menuIconTitle
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun hideDialog() {
        super.hideDialog()
        // Always reset string so when dialog turns
        // back on it will not show previous value.
        value = TextFieldValue(getDefaultValue())
    }

    override fun onSet( newValue: String ) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        Database.asyncTransaction {
            albumTable.onConfirm( newValue )
        }
    }
}


