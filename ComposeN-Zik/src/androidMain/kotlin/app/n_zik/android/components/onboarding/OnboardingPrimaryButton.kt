package app.n_zik.android.components.onboarding

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.PageButton
import app.n_zik.android.components.glass.glassNativeSurfaceTint
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.uiRoundnessShape

/** Shared primary action: the accent owns its foreground in every onboarding step. */
@Composable
internal fun OnboardingPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val palette = colorPalette()
    val settings = rememberGlassSettings()
    val shape = uiRoundnessShape()
    val material = settings.usesGlassMaterial(GlassArea.ActionButtons)
    // Inline onboarding cards do not export a container capture: PageButton
    // samples its independent page canvas, which also gives a stable contrast reference.
    val film = glassNativeSurfaceTint(settings, palette.accent)
    val surface = film.copy(alpha = film.alpha * settings.film).compositeOver(palette.background0)
    val foreground = if (material) onboardingGlassForeground(surface, palette.onAccent, palette.text)
        else palette.onAccent
    PageButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.accent,
            contentColor = foreground,
        ),
        shape = shape,
        settings = settings,
        content = content,
    )
}

/** Chooses a readable foreground for the translucent accent without changing its fill. */
internal fun onboardingGlassForeground(surface: Color, onAccent: Color, text: Color): Color {
    fun contrast(color: Color): Float =
        (maxOf(color.luminance(), surface.luminance()) + .05f) /
            (minOf(color.luminance(), surface.luminance()) + .05f)
    return listOf(onAccent, text, Color.Black, Color.White).maxBy(::contrast)
}
