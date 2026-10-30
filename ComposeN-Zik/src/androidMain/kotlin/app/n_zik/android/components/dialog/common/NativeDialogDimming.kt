package app.n_zik.android.components.dialog.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** Fades the platform window's own dim amount with the retained dialog's content motion. */
@Composable
internal fun NativeDialogDimming(presentation: DialogPresentation) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    val originalDim = remember(window) { window.attributes.dimAmount }
    DisposableEffect(window, presentation) {
        window.setDimAmount(originalDim * dialogFadeAlpha(presentation.motion.value))
        // The window is discarded with its owner. Restoring dim here would flash black on exit.
        onDispose { window.setDimAmount(0f) }
    }
    LaunchedEffect(window, presentation, originalDim) {
        snapshotFlow { presentation.motion.value }.collect { progress ->
            window.setDimAmount(originalDim * dialogFadeAlpha(progress))
        }
    }
}
