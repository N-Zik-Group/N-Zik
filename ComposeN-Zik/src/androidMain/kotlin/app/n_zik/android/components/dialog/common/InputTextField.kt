package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
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
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.textCopyFromClipboard
import app.n_zik.android.uiRoundnessShape

/**
 * URL / text input content block (migrated from the legacy tree):
 * a title, a [TextField] (red container while invalid) and a
 * search / paste / clear [ButtonRow]. It is a content composable, not a
 * dialog window — it is rendered inside an existing dialog or screen.
 */
@Composable
inline fun InputTextField(
    noinline onDismiss: () -> Unit,
    title: String,
    value: String,
    placeholder: String,
    crossinline setValue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = colorPalette()
    val txtFieldError = remember { mutableStateOf("") }
    val txtField = remember { mutableStateOf(value) }
    val value_cannot_empty = stringResource(R.string.value_cannot_be_empty)

    var copyText by remember { mutableStateOf(true) }
    if (copyText) {
        txtField.value = textCopyFromClipboard(context)
        copyText = false
    }

    txtField.value

    Column(
        modifier = modifier
            .padding(all = 10.dp)
            .background(color = palette.background1, shape = uiRoundnessShape())
            .padding(vertical = 16.dp)
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
                    focusedPlaceholderColor = palette.textDisabled,
                    unfocusedPlaceholderColor = palette.textDisabled,
                    cursorColor = palette.text,
                    focusedTextColor = palette.text,
                    unfocusedTextColor = palette.text,
                    focusedContainerColor = if (txtFieldError.value.isEmpty()) palette.background1 else palette.red,
                    unfocusedContainerColor = if (txtFieldError.value.isEmpty()) palette.background1 else palette.red,
                    focusedIndicatorColor = palette.accent,
                    unfocusedIndicatorColor = palette.textDisabled
                ),
                placeholder = { Text(text = placeholder) },
                value = txtField.value,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                onValueChange = {
                    txtField.value = it
                })
        }

        Spacer(modifier = Modifier.height(30.dp))

        ButtonRow(
            buttons = listOf(
                DialogButton(
                    text = stringResource(R.string.search),
                    onClick = {
                        if (txtField.value.isEmpty()) {
                            txtFieldError.value = value_cannot_empty
                        }
                        if (txtField.value.isNotEmpty()) {
                            setValue(txtField.value)
                        }
                    }
                ),
                DialogButton(
                    text = stringResource(R.string.paste),
                    onClick = {
                        copyText = true
                    }
                ),
                DialogButton(
                    text = stringResource(R.string.clear),
                    onClick = { txtField.value = "" }
                )
            )
        )
    }
}
