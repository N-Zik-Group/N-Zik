package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography

/**
 * Simple confirm/cancel dialog on the shared [DialogFrame].
 */
@Composable
fun ConfirmationDialog(
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String = stringResource(R.string.cancel),
    confirmText: String = stringResource(R.string.confirm),
    onCancel: () -> Unit = onDismiss
) {
    DialogFrame(
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        // Message text
        BasicText(
            text = text,
            style = typography().s.copy(
                color = colorPalette().text
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )

        ButtonRow(
            buttons = listOf(
                DialogButton(
                    text = cancelText,
                    onClick = onCancel,
                    danger = true
                ),
                DialogButton(
                    text = confirmText,
                    onClick = {
                        onConfirm()
                        onDismiss()
                    },
                    primary = true
                )
            )
        )
    }
}
