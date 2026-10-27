package app.it.fast4x.rimusic.ui.components.tab.toolbar

import androidx.compose.runtime.Composable
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.ConfirmationDialog

interface ConfirmDialog: Dialog {

    /** Short action heading; [dialogTitle] remains the confirmation question. */
    @get:Composable
    val confirmationTitle: String

    @get:Composable
    val confirmationAction: String


    /**
     * What happens when user hits "Confirm" button.
     */
    fun onConfirm()

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

        ConfirmationDialog(
            title = confirmationTitle,
            confirmText = confirmationAction,
            text = dialogTitle,
            onDismiss = ::onDismiss,
            onConfirm = ::onConfirm
        )
        }
    }
}


