package app.n_zik.android.components.dialog.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial

/** Fades the platform window's own dim amount with the retained dialog's content motion. */
@Composable
internal fun NativeDialogDimming(
    presentation: DialogPresentation?,
    area: GlassArea = GlassArea.Dialogs,
    scrimColor: Color = Color.Unspecified,
    scrimVisible: Boolean = true,
) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    val originalDim = remember(window) { window.attributes.dimAmount }
    val settings = rememberGlassSettings()
    val dim = if (!scrimVisible) 0f else if (scrimColor.isSpecified) scrimColor.alpha
        else nativeGlassDialogDimAmount(settings, area, originalDim)
    DisposableEffect(window, presentation, dim) {
        window.setDimAmount(dim * (presentation?.motion?.value?.let(::dialogFadeAlpha) ?: 1f))
        // The window is discarded with its owner. Restoring dim here would flash black on exit.
        onDispose { window.setDimAmount(0f) }
    }
    LaunchedEffect(window, presentation, dim) {
        snapshotFlow { presentation?.motion?.value ?: 1f }.collect { progress ->
            window.setDimAmount(dim * dialogFadeAlpha(progress))
        }
    }
}

/** The master and area material mode select Glass dimming independently of renderer support. */
internal fun nativeGlassDialogDimAmount(settings: GlassSettings, area: GlassArea, native: Float): Float =
    if (!settings.usesGlassMaterial(area)) native
    else if (area == GlassArea.Menus) settings.menuDimming else settings.dialogDimming
