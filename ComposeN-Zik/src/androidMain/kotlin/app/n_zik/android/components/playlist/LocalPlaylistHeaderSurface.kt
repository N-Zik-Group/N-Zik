package app.n_zik.android.components.playlist

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.compositeOver
import app.n_zik.android.components.glass.GlassRenderer
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassFilmOpacity
import app.n_zik.android.components.glass.glassNativeSurfaceTint
import app.n_zik.android.components.glass.glassOpticalShape
import app.n_zik.android.components.glass.glassRenderer
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.glassSettingsCardBackground
import app.n_zik.android.components.glass.glassSettingsCardRim
import app.n_zik.android.components.glass.glassTint
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.thumbnailShape
import app.n_zik.android.uiRoundnessShape

/** Styles the existing playlist header bounds without changing its content or spacing. */
@Composable
internal fun Modifier.localPlaylistHeaderSurface(settings: GlassSettings, exported: LayerBackdrop? = null): Modifier {
    val palette = colorPalette()
    if (settings.usesGlassMaterial(GlassArea.SettingsCards)) {
        val shape = uiRoundnessShape()
        return clip(shape).glassSettingsCardBackground(
            backgroundColor = palette.background0,
            cardColor = palette.background1,
            shape = shape,
            settings = settings,
            exportedBackdrop = exported,
        )
    }
    val shape = thumbnailShape()
    return background(palette.background1, shape)
        .glassSettingsCardRim(settings, shape, glassTint(settings))
}

/** Optical export for buttons inside the existing panel; the neighboring cover is excluded. */
@Composable
internal fun LocalPlaylistHeaderSurfaceScope(
    settings: GlassSettings,
    content: @Composable (LayerBackdrop?) -> Unit,
) {
    val palette = colorPalette()
    val material = settings.usesGlassMaterial(GlassArea.SettingsCards)
    val area = settings.forArea(GlassArea.SettingsCards)
    val fallback = glassRenderer(area) == GlassRenderer.Tint || glassOpticalShape(uiRoundnessShape()) == null
    val export = rememberLayerBackdrop()
    val film = glassNativeSurfaceTint(area, palette.background1)
    val visibleFill = if (material) film.copy(alpha = film.alpha * glassFilmOpacity(
        area.film, overlay = false, fallback = fallback, dark = palette.isDark,
    )).compositeOver(palette.background0) else palette.background1
    val uniform = rememberCanvasBackdrop { drawRect(visibleFill) }
    // The Tint renderer has no optical layer, so export its exact composite fill.
    val source = if (material && !fallback) export else uniform
    CompositionLocalProvider(LocalGlassContainerBackdrop provides source) {
        content(export.takeIf { material && !fallback })
    }
}
