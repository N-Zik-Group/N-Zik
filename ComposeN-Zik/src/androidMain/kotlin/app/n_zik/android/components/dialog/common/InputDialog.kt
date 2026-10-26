package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
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

    val keyboardOption: KeyboardOptions

    var value: TextFieldValue

    fun onValueChanged( newValue: String ): Boolean

    fun onSet( newValue: String )

    /**
     * When [value] is empty, this component
     * will be triggered to place a placeholder
     * to the text box.
     *
     * NOTE: It is not counted as valid value.
     */
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
                text = stringResource(R.string.confirm),
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
            modifier = Modifier.padding( vertical = 20.dp )
                               .fillMaxWidth(),
            colors = defaultTextFieldColors()
        )
    }
}


