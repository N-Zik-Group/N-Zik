package app.n_zik.android.components.playlist

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
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
internal fun Modifier.localPlaylistHeaderSurface(settings: GlassSettings): Modifier {
    val palette = colorPalette()
    if (settings.usesGlassMaterial(GlassArea.SettingsCards)) {
        val shape = uiRoundnessShape()
        return clip(shape).glassSettingsCardBackground(
            backgroundColor = palette.background0,
            cardColor = palette.background1,
            shape = shape,
            settings = settings,
        )
    }
    val shape = thumbnailShape()
    return background(palette.background1, shape)
        .glassSettingsCardRim(settings, shape, glassTint(settings))
}
