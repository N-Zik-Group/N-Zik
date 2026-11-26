package app.n_zik.android.components.dialog.playlist

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import app.n_zik.android.components.dialog.common.InputDialogConstraints
import app.n_zik.android.components.dialog.common.TextInputDialog
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon

class ImportPlaylistDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val initialValue: () -> String,
    private val onImport: (String) -> Unit
): TextInputDialog(InputDialogConstraints.ALL), MenuIcon, Descriptive {

    companion object {
        @Composable
        operator fun invoke(
            initialValue: String,
            onImport: (String) -> Unit
        ): ImportPlaylistDialog {
            val currentInitialValue = rememberUpdatedState(initialValue)
            val currentOnImport = rememberUpdatedState(onImport)
            return remember {
                ImportPlaylistDialog(
                    mutableStateOf(false),
                    mutableStateOf(TextFieldValue()),
                    initialValue = { currentInitialValue.value },
                    onImport = { currentOnImport.value(it) },
                )
            }
        }
    }

    override val submitText: String
        @Composable get() = stringResource(R.string.common_action_import)

    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default
    override val iconId: Int = R.drawable.import_outline
    override val messageId: Int = R.string.playlist_action_import
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.playlist_name_input_hint)
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.playlist_action_import )

    override var value: TextFieldValue by valueState
    override var isActive: Boolean by activeState

    override fun onShortClick() = showDialog()

    override fun showDialog() {
        if (isActive) return
        value = TextFieldValue(initialValue())
        errorMessage = ""
        super.showDialog()
    }

    @Composable
    override fun LeadingIcon() = Icon(
        imageVector = Icons.Outlined.Edit,
        tint = colorPalette().accent,
        contentDescription = stringResource(R.string.accessibility_import_playlist_name)
    )

    override fun onSet( newValue: String ) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        onImport(newValue)
        hideDialog()
    }
}


