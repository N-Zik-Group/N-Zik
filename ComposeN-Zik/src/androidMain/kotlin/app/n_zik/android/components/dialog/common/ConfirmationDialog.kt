package app.n_zik.android.components.dialog.common

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    confirmText: String,
    onCancel: () -> Unit = onDismiss,
    title: String,
    dismissOnConfirm: Boolean = true
) {
    DialogFrame(
        onDismiss = onDismiss,
        title = title,
        modifier = modifier,
        bodyPadding = PaddingValues(0.dp),
        footer = {
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
                            if (dismissOnConfirm) onDismiss()
                        },
                        primary = true
                    )
                )
            )
        }
    ) {
        BasicText(
            text = text,
            style = typography().s.copy(
                color = colorPalette().text
            ),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(24.dp)
        )
    }
}
