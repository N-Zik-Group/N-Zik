package app.n_zik.android.components.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import app.n_zik.android.components.glass.PageButtonFeedback
import app.n_zik.android.uiRoundnessShape

/** Keeps the native small FAB layout while sharing the application's feedback owner. */
@Composable
fun UiSmallFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = uiRoundnessShape(),
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = androidx.compose.material3.contentColorFor(containerColor),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    PageButtonFeedback(false, true, shape, interactionSource) { feedback, source ->
        androidx.compose.material3.SmallFloatingActionButton(
            onClick = onClick,
            modifier = modifier.then(feedback),
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            interactionSource = source,
            content = content,
        )
    }
}
