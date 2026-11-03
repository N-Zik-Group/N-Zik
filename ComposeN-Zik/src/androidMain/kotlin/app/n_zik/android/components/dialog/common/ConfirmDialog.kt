package app.n_zik.android.components.dialog.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R

interface ConfirmDialog: InteractiveDialog {

    val confirmText: String
        @Composable get() = stringResource(R.string.common_action_apply)

    fun onConfirm()

    @Composable
    override fun Buttons() = ButtonRow(
        buttons = listOf(
            DialogButton(
                text = stringResource(R.string.common_action_cancel),
                onClick = ::hideDialog,
                danger = true
            ),
            DialogButton(
                text = confirmText,
                onClick = ::onConfirm,
                primary = true
            )
        )
    )
}
