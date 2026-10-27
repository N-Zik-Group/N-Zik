package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface Dialog {

    companion object {
        /**
         * Space between top and bottom line of dialog.
         */
        const val VERTICAL_PADDING = 15

        /**
         * Represents the maximum width allowed
         * for this component in portrait mode.
         *
         * Larger number may cause accessibility issue
         */
        const val MAX_WIDTH_PORTRAIT = .95f

        /**
         * Represents the maximum width allowed
         * for this component in landscape mode.
         *
         * Larger number may cause accessibility issue
         */
        const val MAX_WIDTH_LANDSCAPE = .6f

        /**
         * Represents the maximum height allowed
         * for this component in portrait mode.
         *
         * Larger number may cause accessibility issue
         */
        const val MAX_HEIGHT_PORTRAIT = .8f

        /**
         * Represents the maximum height allowed
         * for this component in landscape mode.
         *
         * Larger number may cause accessibility issue
         */
        const val MAX_HEIGHT_LANDSCAPE = .8f

        /**
         * Space between sections in dialog.
         *
         * A section is an individual part of dialog,
         * i.e. title, body, buttons
         */
        const val SPACE_BETWEEN_SECTIONS = 20
    }

    val bodyPadding: PaddingValues get() = PaddingValues(24.dp)

    @get:Composable
    val dialogTitle: String

    /**
     * Whether the dialog should be shown or not.
     *
     * **Avoid** changing this value directly, instead,
     * use [showDialog] and/or [hideDialog] to
     * modify dialog's visibility.
     */
    var isActive: Boolean

    fun showDialog() { isActive = true }

    fun hideDialog() { isActive = false }

    @Composable
    fun DialogBody()

    @Composable
    fun Render() {
        if( !isActive ) return

        DialogFrame(
            onDismiss = ::hideDialog,
            title = dialogTitle,
            bodyPadding = bodyPadding,
            footer = if (this@Dialog is InteractiveDialog) ({ Buttons() }) else null
        ) {
            Box( modifier = Modifier.weight( 1f, fill = false ) ) {
                DialogBody()
            }
        }
    }
}
