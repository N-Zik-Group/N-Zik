package app.it.fast4x.rimusic.ui.components.themed

import androidx.compose.runtime.Composable
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Dialog
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.settings.SettingsInputDialog

interface IDialog: Dialog {

    var value: String
    val placeholder: String
        @Composable
        get() = ""

    /**
     * What happens when user hits "Confirm" button
     */
    fun onSet( newValue: String )

    /**
     * Triggered when user interacts with back button
     * or with something outside of this menu's scope.
     *
     * By default, this will turn off the dialog
     */
    fun onDismiss() { isActive = false }

    @Composable
    override fun Render() {
        DialogPresence(visible = isActive, inherit = true) {

        SettingsInputDialog(
            title = dialogTitle,
            initialValue = value,
            placeholder = placeholder,
            onDismiss = ::onDismiss,
            onSetValue = ::onSet
        ).apply {
            showDialog()
            Render()
        }
        }
    }
}


