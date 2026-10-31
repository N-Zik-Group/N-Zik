package app.n_zik.android.components.ui.screens.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassControlOverflowSurface
import app.n_zik.android.components.glass.GlassMaterialContainerScope
import app.n_zik.android.components.glass.glassSettingsCardBackground
import app.n_zik.android.components.glass.glassSettingsCardRim
import app.n_zik.android.components.glass.glassTint
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.surfaceColor
import app.n_zik.android.uiRoundnessShape

/** Keeps the profile cards' native shadow before their existing click and clip modifiers. */
@Composable
internal fun ProfileCardSurface(
    modifier: Modifier = Modifier,
    interactionModifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = colorPalette()
    val shape = uiRoundnessShape()
    val fill = surfaceColor(palette.background1)
    val material = settings.usesGlassMaterial(GlassArea.SettingsCards)
    val interaction = if (onClick != null) Modifier.clip(shape).clickable(enabled = enabled, onClick = onClick)
        else interactionModifier
    // Opaque legacy modifiers retain their original card semantics/mask. Real profile
    // owners use the explicit callback so the skin can relinquish accessibility safely.
    if (settings.usesGlassMaterial(GlassArea.ActionButtons) && (onClick != null || interactionModifier == Modifier)) {
        val accessible = if (onClick != null) Modifier.semantics(mergeDescendants = true) {
            if (!enabled) disabled()
            semanticsOnClick {
                if (enabled) onClick()
                enabled
            }
        } else Modifier
        GlassMaterialContainerScope(
            modifier = Modifier,
            sectionFilm = if (material) null else fill.compositeOver(palette.background0),
            material = if (material) { exported ->
                Modifier.clip(shape).glassSettingsCardBackground(palette.background0, fill, shape, settings, exported)
            } else null,
        ) { exportedMaterial ->
            val surfaceModifier = if (material) exportedMaterial else
                Modifier.shadow(4.dp, shape, spotColor = palette.accent.copy(alpha = 0.2f))
                    .glassSettingsCardRim(settings, shape, glassTint(settings))
            GlassControlOverflowSurface(modifier.then(accessible), shape, surface = {
                Card(
                    modifier = Modifier.matchParentSize().then(surfaceModifier)
                        .then(if (onClick != null) Modifier.clearAndSetSemantics { } else Modifier)
                        .then(interaction),
                    shape = shape,
                    colors = CardDefaults.cardColors(containerColor = if (material) Color.Transparent else fill),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) { }
            }, content = content)
        }
        return
    }
    val surfaceModifier = if (material) {
        Modifier.clip(shape).glassSettingsCardBackground(palette.background0, fill, shape, settings)
    } else {
        Modifier.shadow(4.dp, shape, spotColor = palette.accent.copy(alpha = 0.2f))
            .glassSettingsCardRim(settings, shape, glassTint(settings))
    }
    Card(
        modifier = modifier.then(surfaceModifier).then(interaction),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (material) Color.Transparent else fill),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}
