package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.semiBold

/** Numeric input dialog on the shared [DefaultDialog]: a [TextField] (red container while invalid), the error line and a confirm / cancel [ButtonRow]. */
@Composable
inline fun InputNumericDialog(
    noinline onDismiss: () -> Unit,
    title: String,
    value: String,
    valueMin: String,
    valueMax: String,
    placeholder: String,
    crossinline setValue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorPalette = colorPalette()
    val txtFieldError = remember { mutableStateOf("") }
    val txtField = remember { mutableStateOf(value) }
    val value_cannot_empty = stringResource(R.string.value_cannot_be_empty)
    val value_must_be_greater = stringResource(R.string.value_must_be_greater_than)

    DefaultDialog(
        onDismiss = onDismiss,
        horizontalAlignment = Alignment.Start,
        modifier = modifier
    ) {
        BasicText(
            text = title,
            style = typography().s.semiBold,
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 24.dp)
        )

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            TextField(
                modifier = Modifier
                    .fillMaxWidth(0.7f),
                colors = TextFieldDefaults.colors(
                    focusedPlaceholderColor = colorPalette.textDisabled,
                    unfocusedPlaceholderColor = colorPalette.textDisabled,
                    cursorColor = colorPalette.text,
                    focusedTextColor = colorPalette.text,
                    unfocusedTextColor = colorPalette.text,
                    focusedContainerColor = if (txtFieldError.value.isEmpty()) colorPalette.background1 else colorPalette.red,
                    unfocusedContainerColor = if (txtFieldError.value.isEmpty()) colorPalette.background1 else colorPalette.red,
                    focusedIndicatorColor = colorPalette.accent,
                    unfocusedIndicatorColor = colorPalette.textDisabled
                ),
                placeholder = { Text(text = placeholder) },
                value = txtField.value,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = {
                    txtField.value = it.take(10)
                })
        }

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            BasicText(
                text = if (txtFieldError.value.isNotEmpty()) txtFieldError.value else "---",
                style = typography().xs.medium,
                modifier = Modifier
                    .padding(vertical = 8.dp, horizontal = 24.dp)
            )
        }

        ButtonRow(
            buttons = listOf(
                DialogButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        when {
                            txtField.value.isEmpty() -> txtFieldError.value = value_cannot_empty
                            txtField.value.toInt() < valueMin.toInt() ->
                                txtFieldError.value = value_must_be_greater + valueMin
                            else -> setValue(txtField.value)
                        }
                    }
                ),
                DialogButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    danger = true
                )
            )
        )
    }
}
