package app.n_zik.android.components.ui.screens.profiles

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSettings
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = colorPalette()
    val shape = uiRoundnessShape()
    val fill = surfaceColor(palette.background1)
    val material = settings.usesGlassMaterial(GlassArea.SettingsCards)
    val surfaceModifier = if (material) {
        Modifier.clip(shape).glassSettingsCardBackground(palette.background0, fill, shape, settings)
    } else {
        Modifier.shadow(4.dp, shape, spotColor = palette.accent.copy(alpha = 0.2f))
            .glassSettingsCardRim(settings, shape, glassTint(settings))
    }
    Card(
        modifier = modifier.then(surfaceModifier).then(interactionModifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (material) Color.Transparent else fill),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}
