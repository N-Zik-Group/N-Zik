package app.n_zik.android.components.settings.common

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.usesGlassContour
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassControlOverflowSurface
import app.n_zik.android.components.glass.GlassMaterialContainerScope
import androidx.compose.ui.graphics.compositeOver
import app.n_zik.android.components.glass.glassSettingsCardBackground
import app.n_zik.android.components.glass.glassSettingsCardRim
import app.n_zik.android.components.glass.glassTint
import app.n_zik.android.components.glass.rememberGlassSettings

/** Uses one Glass surface, retaining the original native card when its material is disabled. */
@Composable
internal fun SettingsCardSurface(
    modifier: Modifier,
    shape: Shape,
    containerColor: Color,
    backgroundColor: Color,
    shadowColor: Color,
    shadowElevation: Dp = 4.dp,
    settings: GlassSettings = rememberGlassSettings(),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        val glassCard = settings.usesGlassMaterial(GlassArea.SettingsCards)
        GlassMaterialContainerScope(
            modifier = Modifier,
            sectionFilm = if (glassCard) null else containerColor.compositeOver(backgroundColor),
            material = if (glassCard) { exported ->
                Modifier.clip(shape).glassSettingsCardBackground(
                    backgroundColor, containerColor, shape, settings, exported)
            } else null,
        ) { material ->
            GlassControlOverflowSurface(modifier, shape, surface = {
                if (settings.usesGlassMaterial(GlassArea.SettingsCards)) {
                    Box(Modifier.matchParentSize().then(material))
                } else {
                    val tint = if (settings.usesGlassContour(GlassArea.SettingsCards)) glassTint(settings) else Color.Unspecified
                    Card(Modifier.matchParentSize()
                        .shadow(elevation = shadowElevation, shape = shape, spotColor = shadowColor)
                        .glassSettingsCardRim(settings, shape, tint),
                        shape = shape,
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) { }
                }
            }, content = content)
        }
        return
    }
    if (settings.usesGlassMaterial(GlassArea.SettingsCards)) {
        Column(
            modifier = modifier
                .clip(shape)
                .glassSettingsCardBackground(backgroundColor, containerColor, shape, settings),
            content = content,
        )
    } else {
        val tint = if (settings.usesGlassContour(GlassArea.SettingsCards)) glassTint(settings) else Color.Unspecified
        Card(
            modifier = modifier
                .shadow(elevation = shadowElevation, shape = shape, spotColor = shadowColor)
                .glassSettingsCardRim(settings, shape, tint),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content,
        )
    }
}
