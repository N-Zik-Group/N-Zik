package app.n_zik.android.components.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ChipColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop

/** Replaces a custom chip's fill without changing its padding, gestures or content. */
@Composable
internal fun Modifier.glassChipBackground(
    color: Color,
    shape: Shape,
    settings: GlassSettings = rememberGlassSettings(),
): Modifier {
    if (LocalNativeQueueRows.current) return background(color, shape)
    if (!settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        return background(color, shape).glassAreaContour(settings, GlassArea.ActionButtons, shape)
    }
    val backgroundColor = colorPalette().background0
    val drawBackground: DrawScope.() -> Unit = remember(backgroundColor) { { drawRect(backgroundColor) } }
    // A container export includes its material but excludes these child controls.
    // Page captures can contain the chip itself, so retain an independent fallback.
    val backdrop = LocalGlassContainerBackdrop.current ?: rememberCanvasBackdrop(drawBackground)
    return glassMaterial(settings.forArea(GlassArea.ActionButtons), shape,
        ownBackdrop = backdrop, filmColor = color, drawShadow = false)
}

/** Keeps Material selection, colours and touch bounds while replacing only its fill. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = uiRoundnessShape(),
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    settings: GlassSettings = rememberGlassSettings(),
) {
    if (!settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        FilterChip(selected, onClick, label,
            modifier.glassAreaContour(settings, GlassArea.ActionButtons, shape),
            enabled = enabled, shape = shape, colors = colors)
        return
    }
    val film = when {
        !enabled && selected -> colors.disabledSelectedContainerColor
        !enabled -> colors.disabledContainerColor
        selected -> colors.selectedContainerColor
        else -> colors.containerColor
    }
    // Minimum touch space belongs outside the optical surface, as in Material's chip.
    Box(Modifier.minimumInteractiveComponentSize().then(modifier), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            FilterChip(selected, onClick, label,
                modifier = Modifier.glassChipBackground(film, shape, settings),
                enabled = enabled, shape = shape,
                colors = colors.copy(containerColor = Color.Transparent,
                    selectedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    disabledSelectedContainerColor = Color.Transparent),
                border = null)
        }
    }
}

/** Preserves the native keyword chip; Glass uses the same shared material as filters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ChipColors = SuggestionChipDefaults.suggestionChipColors(),
    border: BorderStroke? = SuggestionChipDefaults.suggestionChipBorder(enabled),
    settings: GlassSettings = rememberGlassSettings(),
) {
    val nativeShape = shape ?: SuggestionChipDefaults.shape
    if (!settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        SuggestionChip(onClick, label,
            modifier.glassAreaContour(settings, GlassArea.ActionButtons, nativeShape),
            enabled = enabled, shape = nativeShape, colors = colors, border = border)
        return
    }
    val glassShape = shape ?: uiRoundnessShape()
    Box(Modifier.minimumInteractiveComponentSize().then(modifier), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            SuggestionChip(onClick, label,
                modifier = Modifier.glassChipBackground(
                    if (enabled) colors.containerColor else colors.disabledContainerColor, glassShape, settings),
                enabled = enabled, shape = glassShape,
                colors = colors.copy(containerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent), border = null)
        }
    }
}
