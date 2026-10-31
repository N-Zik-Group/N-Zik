package app.n_zik.android.components.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import app.n_zik.android.components.glass.GlassNativeSurfaceBounds
import app.n_zik.android.components.glass.NativeButtonSurfaceProbe
import app.n_zik.android.components.glass.NativePageButtonFeedback
import app.n_zik.android.uiRoundnessShape

/** Native Material icon geometry and actions with the application's shared UI shape. */
@Composable
fun UiIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = uiRoundnessShape(),
    content: @Composable () -> Unit,
) {
    val nativeBounds = remember { GlassNativeSurfaceBounds() }
    NativePageButtonFeedback(false, enabled, shape, interactionSource, nativeBounds) { feedback, source ->
        androidx.compose.material3.IconButton(onClick = onClick, modifier = modifier.then(feedback), enabled = enabled,
            colors = colors, interactionSource = source, shape = shape) {
            content()
            NativeButtonSurfaceProbe(nativeBounds)
        }
    }
}
