package app.n_zik.android.components.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalView
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.surfaceColor

private fun GlassSettings.usesGlassDialogButtonMaterial(): Boolean =
    usesGlassMaterial(GlassArea.DialogButtons)

/** The Glass rim replaces a native outline while the dialog button material is active. */
@Composable
internal fun glassDialogButtonBorder(
    originalBorder: BorderStroke,
    settings: GlassSettings = rememberGlassSettings(),
): BorderStroke? = if (settings.usesGlassDialogButtonMaterial() || settings.usesGlassContour(GlassArea.DialogButtons)) null else originalBorder

/** Dialog action material; preserves the role fill and leaves content and input unchanged. */
@PublishedApi
@Composable
internal fun Modifier.glassDialogButtonBackground(
    backgroundColor: Color,
    shape: Shape,
    filmColor: Color = backgroundColor,
    settings: GlassSettings = rememberGlassSettings(),
): Modifier {
    if (!settings.usesGlassDialogButtonMaterial()) return background(backgroundColor).glassAreaContour(settings, GlassArea.DialogButtons, shape)

    // The shared modal capture excludes hosted dialogs. Native dialog windows must not sample
    // a window/page capture that could contain their own action.
    val dialogBackground = surfaceColor(colorPalette().background1)
    val drawBackground: DrawScope.() -> Unit = remember(dialogBackground) {
        { drawRect(dialogBackground) }
    }
    val backdrop = rememberCanvasBackdrop(drawBackground)
    val host = LocalGlassMenuHost.current
    val sharedBackdrop = if (host?.dialogs?.windowRoot === LocalView.current.rootView) host?.modalBackdrop else null
    return glassMaterial(
        settings = settings.forArea(GlassArea.DialogButtons),
        shape = shape,
        ownBackdrop = LocalGlassContainerBackdrop.current ?: sharedBackdrop ?: backdrop,
        menu = true,
        filmColor = filmColor,
    )
}
