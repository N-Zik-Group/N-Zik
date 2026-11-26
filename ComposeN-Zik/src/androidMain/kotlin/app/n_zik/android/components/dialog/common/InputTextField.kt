package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassTextField
import app.n_zik.android.surfaceColor
import app.n_zik.android.utils.textCopyFromClipboard

@Composable
internal fun InputTextField(
    initialValue: String,
    placeholder: String,
    enabled: Boolean,
    onSubmit: (String) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val palette = colorPalette()
    var input by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    val canSubmit = enabled && input.isNotBlank()
    val submit: () -> Unit = {
        if (canSubmit) {
            focusManager.clearFocus()
            onSubmit(input.trim())
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GlassTextField(
            value = input,
            onValueChange = { input = it },
            enabled = enabled && dialogInputEnabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            colors = TextFieldDefaults.colors(
                focusedPlaceholderColor = palette.textDisabled,
                unfocusedPlaceholderColor = palette.textDisabled,
                cursorColor = palette.text,
                focusedTextColor = palette.text,
                unfocusedTextColor = palette.text,
                focusedContainerColor = surfaceColor(palette.background2),
                unfocusedContainerColor = surfaceColor(palette.background2),
                focusedIndicatorColor = palette.accent,
                unfocusedIndicatorColor = palette.textDisabled,
            ),
        )
        ButtonRow(
            buttons = listOf(
                DialogButton(
                    text = stringResource(R.string.common_action_import),
                    enabled = canSubmit,
                    onClick = submit,
                ),
                DialogButton(
                    text = stringResource(R.string.common_action_paste),
                    primary = true,
                    placement = DialogButtonPlacement.Center,
                    enabled = enabled,
                    onClick = { input = textCopyFromClipboard(context) },
                ),
                DialogButton(
                    text = stringResource(R.string.common_action_clear),
                    danger = true,
                    placement = DialogButtonPlacement.End,
                    enabled = enabled && input.isNotEmpty(),
                    onClick = { input = "" },
                ),
            ),
            horizontalPadding = 0.dp,
        )
    }
}
