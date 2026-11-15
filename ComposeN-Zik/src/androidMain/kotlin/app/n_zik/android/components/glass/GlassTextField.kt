package app.n_zik.android.components.glass

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape

@Composable
private fun Modifier.inputGlass(settings: GlassSettings, shape: Shape, role: Color): Modifier {
    val area = GlassArea.SearchBars
    return if (settings.usesGlassMaterial(area)) {
        val background = colorPalette().background0
        glassMaterial(settings.forArea(area), shape = shape, filmColor = role, drawShadow = false,
            ownBackdrop = LocalGlassMaterialSource.current ?: LocalGlassBackdrop.current ?: rememberInlineGlassBackdrop(background))
    } else glassAreaContour(settings, area, shape)
}

private fun TextFieldColors.clearContainer(): TextFieldColors = copy(
    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent, errorContainerColor = Color.Transparent,
)

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = uiRoundnessShape(),
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    val glass = rememberGlassSettings()
    val material = glass.usesGlassMaterial(GlassArea.SearchBars)
    val role = if (isError) colors.errorContainerColor else if (!enabled) colors.disabledContainerColor
        else colors.unfocusedContainerColor
    TextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.inputGlass(glass, shape, role.takeUnless { it == Color.Transparent } ?: colorPalette().background1),
        enabled = enabled, readOnly = readOnly, textStyle = textStyle, label = label, placeholder = placeholder,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon, isError = isError,
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        singleLine = singleLine, maxLines = maxLines, minLines = minLines, shape = shape,
        colors = if (material) colors.clearContainer() else colors,
    )
}

@Composable
fun GlassTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = uiRoundnessShape(),
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    val glass = rememberGlassSettings()
    val material = glass.usesGlassMaterial(GlassArea.SearchBars)
    val role = if (isError) colors.errorContainerColor else if (!enabled) colors.disabledContainerColor
        else colors.unfocusedContainerColor
    TextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.inputGlass(glass, shape, role.takeUnless { it == Color.Transparent } ?: colorPalette().background1),
        enabled = enabled, readOnly = readOnly, textStyle = textStyle, label = label, placeholder = placeholder,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon, isError = isError,
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        singleLine = singleLine, maxLines = maxLines, minLines = minLines, shape = shape,
        colors = if (material) colors.clearContainer() else colors,
    )
}

@Composable
fun GlassOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = uiRoundnessShape(),
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    val glass = rememberGlassSettings()
    val material = glass.usesGlassMaterial(GlassArea.SearchBars)
    val role = if (isError) colors.errorContainerColor else if (!enabled) colors.disabledContainerColor
        else colors.unfocusedContainerColor
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.inputGlass(glass, shape, role.takeUnless { it == Color.Transparent } ?: colorPalette().background1),
        enabled = enabled, readOnly = readOnly, textStyle = textStyle, label = label, placeholder = placeholder,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon, isError = isError,
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        singleLine = singleLine, maxLines = maxLines, minLines = minLines, shape = shape,
        colors = if (material) colors.clearContainer() else colors,
    )
}
