package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor

interface InputDialog: InteractiveDialog {

    companion object {
        @Composable
        @JvmStatic
        fun defaultTextFieldColors(): TextFieldColors =
            TextFieldDefaults.colors(
                focusedTextColor = colorPalette().text,
                unfocusedTextColor = colorPalette().textDisabled,
                focusedContainerColor = surfaceColor(colorPalette().background1),
                unfocusedContainerColor = colorPalette().background0,
                focusedIndicatorColor = colorPalette().accent,
                unfocusedIndicatorColor = colorPalette().textDisabled,
            )
    }

    val submitText: String
        @Composable get() = stringResource(R.string.dialog_save)

    val keyboardOption: KeyboardOptions

    var value: TextFieldValue

    fun onValueChanged( newValue: String ): Boolean

    fun onSet( newValue: String )

    /** Shows a placeholder when [value] is empty without contributing a value. */
    @Composable
    fun TextPlaceholder() {}

    @Composable
    fun LeadingIcon() {}

    @Composable
    fun TrailingIcon() {}

    @Composable
    override fun Buttons() = ButtonRow(
        buttons = listOf(
            DialogButton(
                text = stringResource(R.string.cancel),
                onClick = ::hideDialog,
                danger = true
            ),
            DialogButton(
                text = submitText,
                onClick = { onSet( value.text ) },
                primary = true
            )
        )
    )

    @Composable
    override fun DialogBody() {
        TextField(
            value = value,
            onValueChange = {
                if( onValueChanged( it.text ) )
                    value = it
            },
            placeholder = { TextPlaceholder() },
            maxLines = 1,
            keyboardOptions = keyboardOption,
            leadingIcon = { LeadingIcon() },
            trailingIcon = { TrailingIcon() },
            modifier = Modifier.fillMaxWidth(),
            colors = defaultTextFieldColors()
        )
    }
}


