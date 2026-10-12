package app.n_zik.android.components.dialog.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R

interface ConfirmDialog: InteractiveDialog {

    fun onConfirm()

    @Composable
    override fun Buttons() = ButtonRow(
        buttons = listOf(
            DialogButton(
                text = stringResource(R.string.cancel),
                onClick = ::hideDialog,
                danger = true
            ),
            DialogButton(
                text = stringResource(R.string.confirm),
                onClick = ::onConfirm,
                primary = true
            )
        )
    )
}
