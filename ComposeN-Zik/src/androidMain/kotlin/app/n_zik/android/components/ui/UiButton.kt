package app.n_zik.android.components.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import app.n_zik.android.components.glass.PageButton
import app.n_zik.android.components.glass.PageTextButton
import app.n_zik.android.uiRoundnessShape

/** Shared page policy for Material-compatible actions, including contextual Glass choices. */
@Composable
fun UiButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = uiRoundnessShape(),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    PageButton(onClick = onClick, modifier = modifier,
        enabled = enabled, shape = shape, colors = colors, elevation = elevation,
        border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content)
}

/** Material text button with the same shared feedback owner as icon and filled actions. */
@Composable
fun UiTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = uiRoundnessShape(),
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    PageTextButton(onClick = onClick, modifier = modifier,
        enabled = enabled, shape = shape, colors = colors, elevation = elevation,
        border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content)
}
