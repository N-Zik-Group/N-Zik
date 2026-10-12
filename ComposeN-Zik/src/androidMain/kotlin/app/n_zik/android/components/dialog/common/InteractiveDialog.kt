package app.n_zik.android.components.dialog.common

import androidx.compose.runtime.Composable

interface InteractiveDialog: Dialog {

    /**
     * The dialog's button row, rendered by [Dialog.Render] below the body.
     *
     * Implementations render the shared [ButtonRow] (overflow-safe pill row,
     * issue #896) so every dialog shares the same button UI.
     */
    @Composable
    fun Buttons()
}
