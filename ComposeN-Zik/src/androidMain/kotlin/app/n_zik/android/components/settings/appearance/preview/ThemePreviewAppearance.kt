package app.n_zik.android.components.settings.appearance.preview

import android.graphics.BitmapFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withContext
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassTintSource
import app.n_zik.android.components.glass.glassTintSourceKey
import app.n_zik.android.components.theme.forPaletteMode
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.BoundedCornerSize
import app.n_zik.android.ui.styling.colorPaletteOf
import app.n_zik.android.ui.styling.dynamicColorPaletteOf
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.utils.applyFontPaddingKey
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.fontTypeKey
import app.n_zik.android.utils.thumbnailRoundnessDpKey
import app.n_zik.android.utils.useSystemFontKey

internal data class ThemePreviewAppearance(
    val preferences: Map<String, Any>,
    val appearance: Appearance,
    val glass: GlassSettings,
)

@Composable
internal fun rememberThemePreviewAppearance(document: String?, glass: Boolean): ThemePreviewAppearance {
    val context = LocalContext.current.applicationContext
    val categories = remember(document) { document?.let(::parseThemeV1).orEmpty() }
    val preferences = remember(categories) { categories.values.flatMap { it.plain.entries }.associate { it.toPair() } }
    val glassPreferences = categories[ThemeCategory.GLASS]?.glass.orEmpty()
    val systemDark = isSystemInDarkTheme()
    val paletteName = preferences.option(colorPaletteNameKey, ColorPaletteName.Dynamic)
    val paletteMode = preferences.option(colorPaletteModeKey, ColorPaletteMode.Dark)
    val dark = paletteMode != ColorPaletteMode.Light && (paletteMode != ColorPaletteMode.System || systemDark)
    val base = colorPaletteOf(paletteName, paletteMode, systemDark)
    val artworkPalette by produceState(base, context, paletteName, dark, base) {
        if (paletteName == ColorPaletteName.Dynamic) value = withContext(NzikDispatchers.DATA) {
            val bitmap = context.assets.open("theme-preview/atlas.jpg").use(BitmapFactory::decodeStream)
            try { bitmap?.let { dynamicColorPaletteOf(it, dark) } ?: base } finally { bitmap?.recycle() }
        } else value = base
    }
    val radius = preferences[thumbnailRoundnessDpKey] as? Float ?: 12f
    val appearance = remember(artworkPalette, preferences, paletteMode, radius) {
        Appearance(
            colorPalette = artworkPalette,
            typography = typographyOf(
                artworkPalette.text,
                preferences[useSystemFontKey] == true,
                preferences[applyFontPaddingKey] == true,
                preferences.option(fontTypeKey, FontType.Rubik),
            ),
            thumbnailShape = if (radius >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(radius.dp, 0.25f)),
            uiRoundnessShape = RoundedCornerShape(BoundedCornerSize(25.dp, 0.4f)),
            artistThumbnailShape = CircleShape,
        ).forPaletteMode(paletteMode)
    }
    val glassSettings = remember(glass, glassPreferences) {
        GlassSettings(
            enabled = glass,
            source = glassPreferences.option(glassTintSourceKey, GlassTintSource.None),
            areaModes = GlassArea.entries.mapNotNull { area ->
                val mode = glassPreferences[area.preferenceKey] as? String
                GlassAreaMode.entries.firstOrNull { it.name == mode }?.let { area to it }
            }.toMap(),
        )
    }
    return ThemePreviewAppearance(preferences, appearance, glassSettings)
}

private inline fun <reified E : Enum<E>> Map<String, Any>.option(key: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == this[key] } ?: fallback
