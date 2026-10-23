package app.n_zik.android.components.dialog.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.center
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.delay

/** Single-line text input dialog on the shared [DefaultDialog]: a hint-overlaid [BasicTextField] and a cancel / done [ButtonRow]. */
@Composable
fun TextFieldDialog(
    hintText: String,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String = stringResource(R.string.cancel),
    doneText: String = stringResource(R.string.done),
    initialTextInput: String = "",
    singleLine: Boolean = true,
    maxLines: Int = 1,
    onCancel: () -> Unit = onDismiss,
    isTextInputValid: (String) -> Boolean = { it.isNotEmpty() }
) {
    val focusRequester = remember {
        FocusRequester()
    }

    var textFieldValue by rememberSaveable(initialTextInput, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = initialTextInput,
                selection = TextRange(initialTextInput.length)
            )
        )
    }

    DefaultDialog(
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { textFieldValue = it },
            textStyle = typography().xs.semiBold.center,
            singleLine = singleLine,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.None),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (isTextInputValid(textFieldValue.text)) {
                        onDismiss()
                        onDone(textFieldValue.text)
                    }
                }
            ),
            cursorBrush = SolidColor(colorPalette().text),
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    HintText(hintText, visible = textFieldValue.text.isEmpty())

                    innerTextField()
                }
            },
            modifier = Modifier
                .padding(all = 16.dp)
                .weight(weight = 1f, fill = false)
                .focusRequester(focusRequester)

        )

        ButtonRow(
            buttons = listOf(
                DialogButton(
                    text = cancelText,
                    onClick = onCancel,
                    danger = true
                ),
                DialogButton(
                    text = doneText,
                    onClick = {
                        if (isTextInputValid(textFieldValue.text)) {
                            onDismiss()
                            onDone(textFieldValue.text)
                        }
                    },
                    primary = true
                )
            )
        )
    }

    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }
}

/**
 * The hint overlay of [TextFieldDialog], animated in/out with the text content.
 * Kept in its own function so the [AnimatedVisibility] call is not made from
 * inside the decoration box scope, where the dialog's column receiver would
 * shadow the plain overload.
 */
@Composable
private fun HintText(
    text: String,
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(100)),
        exit = fadeOut(tween(100)),
    ) {
        BasicText(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = typography().xs.semiBold.secondary,
        )
    }
}
