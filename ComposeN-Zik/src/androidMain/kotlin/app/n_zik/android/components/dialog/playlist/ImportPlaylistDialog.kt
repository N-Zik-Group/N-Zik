package app.n_zik.android.components.dialog.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.dialog.common.AboveMenuDialogPresence
import app.n_zik.android.components.dialog.common.InputDialogConstraints
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.TextInputDialog
import app.n_zik.android.R
import app.n_zik.android.appTextResources
import app.n_zik.android.colorPalette
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.typography
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon

class ImportPlaylistDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val initialValue: () -> String,
    private val unavailableMessage: () -> Int?,
    private val onImport: (String) -> Unit
): TextInputDialog(InputDialogConstraints.ALL), MenuIcon, Descriptive {

    private var onImportAccepted: (() -> Unit)? = null
    private var nameEdited by mutableStateOf(false)

    companion object {
        @Composable
        operator fun invoke(
            initialValue: String,
            unavailableMessage: Int? = null,
            onImport: (String) -> Unit
        ): ImportPlaylistDialog {
            val currentInitialValue = rememberUpdatedState(initialValue)
            val currentUnavailableMessage = rememberUpdatedState(unavailableMessage)
            val currentOnImport = rememberUpdatedState(onImport)
            return remember {
                ImportPlaylistDialog(
                    mutableStateOf(false),
                    mutableStateOf(TextFieldValue()),
                    initialValue = { currentInitialValue.value },
                    unavailableMessage = { currentUnavailableMessage.value },
                    onImport = { currentOnImport.value(it) },
                )
            }
        }
    }

    override val submitText: String
        @Composable get() = stringResource(R.string.common_action_import)

    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default
    override val iconId: Int = R.drawable.import_playlist
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

    internal fun open(onAccepted: () -> Unit) {
        if (isActive) return
        onImportAccepted = onAccepted
        showDialog()
    }

    override fun showDialog() {
        if (isActive) return
        value = TextFieldValue(initialValue())
        nameEdited = false
        errorMessage = ""
        super.showDialog()
    }

    override fun hideDialog() {
        onImportAccepted = null
        super.hideDialog()
    }

    @Composable
    internal fun RenderAboveMenu(menuState: MenuState) {
        AboveMenuDialogPresence(menuState, visible = isActive) { Render() }
    }

    @Composable
    override fun LeadingIcon() = Icon(
        imageVector = Icons.Outlined.Edit,
        tint = colorPalette().accent,
        contentDescription = stringResource(R.string.accessibility_import_playlist_name)
    )

    @Composable
    override fun DialogBody() {
        val sourceTitle = initialValue()
        LaunchedEffect(isActive, sourceTitle) {
            if (isActive && !nameEdited) value = TextFieldValue(sourceTitle)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            super.DialogBody()
            unavailableMessage()?.let { message ->
                BasicText(
                    text = stringResource(message),
                    style = typography().xs.copy(color = colorPalette().textSecondary),
                )
            }
        }
    }

    @Composable
    override fun Buttons() = ButtonRow(
        listOf(
            DialogButton(stringResource(R.string.common_action_cancel), ::hideDialog, danger = true),
            DialogButton(submitText, { onSet(value.text) }, primary = true, enabled = unavailableMessage() == null),
        ),
    )

    override fun onValueChanged(newValue: String): Boolean {
        if (newValue != value.text) nameEdited = true
        return super.onValueChanged(newValue)
    }

    override fun onSet( newValue: String ) {
        if (unavailableMessage() != null) return
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        importPlaylistNameError(newValue)?.let { error ->
            errorMessage = appTextResources().getString(error)
            return
        }

        onImport(newValue)
        onImportAccepted?.invoke()
        hideDialog()
    }
}

internal fun importPlaylistNameError(name: String): Int? =
    R.string.playlist_name_reserved.takeIf { RewindPlaylists.isReservedName(name) }


