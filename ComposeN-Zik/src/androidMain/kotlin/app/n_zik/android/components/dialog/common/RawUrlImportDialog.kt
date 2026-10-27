package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.textCopyFromClipboard
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor

/** Clipboard-prefilled URL input that forwards the exact nonempty text to [onImport]. */
@Composable
fun RawUrlImportDialog(
    title: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    val context = LocalContext.current
    val palette = colorPalette()
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var copyText by remember { mutableStateOf(true) }
    if (copyText) {
        text = textCopyFromClipboard(context)
        copyText = false
    }

    DialogFrame(
        title = title,
        onDismiss = onDismiss,
        footer = {
            ButtonRow(listOf(
                DialogButton(stringResource(R.string.paste), { copyText = true }),
                DialogButton(stringResource(R.string.clear), { text = "" }),
            ))
            Spacer(Modifier.height(8.dp))
            ButtonRow(listOf(
                DialogButton(stringResource(R.string.cancel), onDismiss, danger = true),
                DialogButton(
                    text = stringResource(R.string.import_button),
                    onClick = {
                        if (text.isEmpty()) error = true else onImport(text)
                    },
                    primary = true,
                ),
            ))
        },
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = TextFieldDefaults.colors(
                focusedPlaceholderColor = palette.textDisabled,
                unfocusedPlaceholderColor = palette.textDisabled,
                cursorColor = palette.text,
                focusedTextColor = palette.text,
                unfocusedTextColor = palette.text,
                focusedContainerColor = if (error) palette.red else surfaceColor(palette.background1),
                unfocusedContainerColor = if (error) palette.red else surfaceColor(palette.background1),
                focusedIndicatorColor = palette.accent,
                unfocusedIndicatorColor = palette.textDisabled,
            ),
        )
    }
}
