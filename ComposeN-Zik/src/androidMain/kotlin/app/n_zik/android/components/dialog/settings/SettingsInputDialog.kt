package app.n_zik.android.components.dialog.settings

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import app.n_zik.android.R
import app.n_zik.android.appTextResources
import app.n_zik.android.components.dialog.common.InputDialogConstraints
import app.n_zik.android.components.dialog.common.TextInputDialog

class SettingsInputDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val titleStr: String,
    private val placeholderStr: String,
    constraint: String,
    override val allowEmpty: Boolean,
    private val isPassword: Boolean,
    keyboardType: KeyboardType,
    private val isValid: (String) -> Boolean,
    private val onDismiss: () -> Unit,
    private val onSetValue: (String) -> Unit
): TextInputDialog(constraint) {

    companion object {
        @Composable
        operator fun invoke(
            title: String,
            initialValue: String = "",
            placeholder: String = "",
            constraint: String = InputDialogConstraints.ALL,
            allowEmpty: Boolean = false,
            isPassword: Boolean = false,
            keyboardType: KeyboardType = KeyboardType.Text,
            isValid: (String) -> Boolean = { true },
            onDismiss: () -> Unit,
            onSetValue: (String) -> Unit
        ): SettingsInputDialog {
            val instance = SettingsInputDialog(
                remember { mutableStateOf(true) },
                remember(initialValue) { mutableStateOf(TextFieldValue(initialValue)) },
                title,
                placeholder,
                constraint,
                allowEmpty,
                isPassword,
                keyboardType,
                isValid,
                onDismiss,
                onSetValue
            )
            return instance
        }
    }

    override val keyboardOption: KeyboardOptions = if (isPassword) {
        KeyboardOptions(keyboardType = KeyboardType.Password)
    } else {
        KeyboardOptions(keyboardType = keyboardType)
    }

    override val visualTransformation: VisualTransformation = if (isPassword) {
        PasswordVisualTransformation()
    } else {
        VisualTransformation.None
    }

    override val dialogTitle: String
        @Composable
        get() = titleStr

    override var value: TextFieldValue by valueState
    override var isActive: Boolean by activeState

    override fun hideDialog() {
        if (!isActive) return
        isActive = false
        onDismiss()
    }

    override fun onSet(newValue: String) {
        super.onSet(newValue)
        if (errorMessage.isNotEmpty()) return
        if (!isValid(newValue)) {
            errorMessage = appTextResources().getString(R.string.common_validation_invalid_input)
            return
        }
        onSetValue(newValue)
        hideDialog()
    }
}


