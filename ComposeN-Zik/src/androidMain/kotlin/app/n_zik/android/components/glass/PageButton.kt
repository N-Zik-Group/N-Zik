package app.n_zik.android.components.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop

/** Page action retaining Material's dimensions, role colours, content, and input behaviour. */
@Composable
fun PageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    settings: GlassSettings = rememberGlassSettings(),
    content: @Composable RowScope.() -> Unit,
) {
    val material = settings.usesGlassMaterial(GlassArea.ActionButtons)
    PageButtonFeedback(material, enabled, shape, interactionSource) { feedback, source ->
        Button(
            onClick = onClick,
            modifier = modifier.pageButtonMaterial(enabled, shape, colors, settings).then(feedback),
            enabled = enabled,
            shape = shape,
            colors = if (material) colors.copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent) else colors,
            elevation = elevation,
            border = if (material || settings.usesGlassContour(GlassArea.ActionButtons)) null else border,
            contentPadding = contentPadding,
            interactionSource = source,
            content = content,
        )
    }
}

/** Text page action with the exact native text-button defaults when Glass is disabled. */
@Composable
fun PageTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    settings: GlassSettings = rememberGlassSettings(),
    content: @Composable RowScope.() -> Unit,
) {
    val material = settings.usesGlassMaterial(GlassArea.ActionButtons)
    PageButtonFeedback(material, enabled, shape, interactionSource) { feedback, source ->
        TextButton(
            onClick = onClick,
            modifier = modifier.pageButtonMaterial(enabled, shape, colors, settings).then(feedback),
            enabled = enabled,
            shape = shape,
            colors = if (material) colors.copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent) else colors,
            elevation = elevation,
            border = if (material || settings.usesGlassContour(GlassArea.ActionButtons)) null else border,
            contentPadding = contentPadding,
            interactionSource = source,
            content = content,
        )
    }
}

/** Native buttons retain their own ripple; Glass uses the shared light beneath sharp content. */
@Composable
private fun PageButtonFeedback(
    material: Boolean,
    enabled: Boolean,
    shape: Shape,
    interactionSource: MutableInteractionSource?,
    content: @Composable (Modifier, MutableInteractionSource?) -> Unit,
) {
    if (!material) {
        content(Modifier, interactionSource)
        return
    }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val active = enabled && isGlassMotionAllowed()
    if (!active) {
        content(Modifier, source)
        return
    }
    val tracker = remember { GlassHighlightPointerTracker() }
    val indication = remember(tracker) { GlassHighlightIndication(tracker, drawBeforeContent = true) }
    val feedback = Modifier.clip(shape).trackGlassHighlightPointers(tracker).indication(source, indication)
    CompositionLocalProvider(LocalRippleConfiguration provides null) { content(feedback, source) }
}

@Composable
private fun Modifier.pageButtonMaterial(
    enabled: Boolean,
    shape: Shape,
    colors: ButtonColors,
    settings: GlassSettings,
): Modifier {
    if (!settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        return glassAreaContour(settings, GlassArea.ActionButtons, shape)
    }
    // Use the rendered parent material when it exports a child-free source.
    val background = colorPalette().background0
    val drawBackground: DrawScope.() -> Unit = remember(background) { { drawRect(background) } }
    val backdrop = LocalGlassContainerBackdrop.current ?: rememberCanvasBackdrop(drawBackground)
    val roleFill = if (enabled) colors.containerColor else colors.disabledContainerColor
    return glassMaterial(
        settings = settings.forArea(GlassArea.ActionButtons),
        shape = shape,
        ownBackdrop = backdrop,
        filmColor = roleFill,
        drawShadow = false,
    )
}
