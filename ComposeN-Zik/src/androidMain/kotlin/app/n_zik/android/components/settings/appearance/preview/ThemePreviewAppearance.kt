package app.n_zik.android.components.settings.appearance.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.glass.glassSettingsFromPreferences
import app.n_zik.android.components.theme.forPaletteMode
import app.n_zik.android.components.theme.dynamicThemeRespectBrightnessKey
import app.n_zik.android.components.player.ArtworkColorSnapshot
import app.n_zik.android.components.player.SharedArtworkColors
import app.n_zik.android.components.player.rememberScopedArtworkColorRevision
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.settings.DefaultColorPaletteMode
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.BoundedCornerSize
import app.n_zik.android.ui.styling.colorPaletteOf
import app.n_zik.android.ui.styling.customColorPalette
import app.n_zik.android.utils.customColorKey
import app.n_zik.android.ui.styling.dynamicColorPaletteOf
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.utils.applyFontPaddingKey
import app.n_zik.android.utils.artistThumbnailRoundnessDpKey
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.fontTypeKey
import app.n_zik.android.utils.thumbnailRoundnessDpKey
import app.n_zik.android.utils.uiRoundnessDpKey
import app.n_zik.android.utils.useSystemFontKey
import app.n_zik.android.utils.preferences
import com.kieronquinn.monetcompat.core.MonetCompat
import kotlinx.coroutines.CancellationException
import timber.log.Timber

internal data class ThemePreviewAppearance(
    val preferences: Map<String, Any>,
    val appearance: Appearance,
    val glass: GlassSettings,
)

private val themePreviewDefaultPreferences by lazy {
    parseThemeAppearance(defaultThemeAppearanceDocument()).values
        .flatMap { it.plain.entries }.associate { it.toPair() }
}

internal fun themePreviewPreferences(document: String?, glass: Boolean): ThemeSettings {
    val categories = document?.let { parseThemeAppearance(it, allowEmptyCategories = true) }.orEmpty()
    val preferences = categories.values.flatMap { it.plain.entries }.associate { it.toPair() }
    // Stock defaults belong to the demo. Applying an older theme still leaves omitted keys alone.
    val plain = themePreviewDefaultPreferences + preferences + (glassEnabledKey to glass)
    return ThemeSettings(plain, categories[ThemeCategory.GLASS]?.glass.orEmpty())
}

internal fun themePreviewFollowsSystem(document: String?): Boolean {
    val preferences = themePreviewPreferences(document, false).plain
    return !preferences.option(colorPaletteNameKey, ColorPaletteName.Dynamic).isAlwaysDark &&
        preferences.option(colorPaletteModeKey, DefaultColorPaletteMode) == ColorPaletteMode.System
}

@Composable
internal fun rememberThemePreviewAppearance(
    document: String?,
    glass: Boolean,
    paletteModeOverride: ColorPaletteMode? = null,
): ThemePreviewAppearance = rememberPreviewAppearance(document, glass, paletteModeOverride, false)

/** The foreground belongs to the user's app, independently of the demo document and artwork. */
@Composable
internal fun rememberThemePreviewProgressAppearance(): Appearance =
    rememberPreviewAppearance(null, false, null, true).appearance

@Composable
private fun rememberPreviewAppearance(
    document: String?,
    glass: Boolean,
    paletteModeOverride: ColorPaletteMode?,
    useApplicationPreferences: Boolean,
): ThemePreviewAppearance {
    val context = LocalContext.current.applicationContext
    val settings = remember(document, glass, useApplicationPreferences) {
        if (useApplicationPreferences) ThemeSettings(buildMap {
            // MainActivity's app default is Dark; portable theme documents default to System.
            put(colorPaletteModeKey, ColorPaletteMode.Dark.name)
            context.preferences.all.forEach { (key, value) -> value?.let { put(key, it) } }
        }) else themePreviewPreferences(document, glass)
    }
    val preferences = remember(settings, paletteModeOverride) {
        if (paletteModeOverride == null) settings.plain
        else settings.plain + (colorPaletteModeKey to paletteModeOverride.name)
    }
    val glassPreferences = settings.glass
    val systemDark = isSystemInDarkTheme()
    val paletteName = preferences.option(colorPaletteNameKey, ColorPaletteName.Dynamic)
    val paletteMode = preferences.option(colorPaletteModeKey, DefaultColorPaletteMode)
    val dark = paletteMode != ColorPaletteMode.Light && (paletteMode != ColorPaletteMode.System || systemDark)
    val base = remember(context, paletteName, paletteMode, systemDark, preferences) {
        val fallback = colorPaletteOf(paletteName, paletteMode, systemDark)
        when (paletteName) {
            ColorPaletteName.CustomColor ->
                dynamicColorPaletteOf(Color(preferences[customColorKey] as? Int ?: Color.Green.hashCode()), dark)
            ColorPaletteName.Customized -> customColorPalette(fallback, paletteMode, systemDark) { key, default ->
                preferences[key] as? Int ?: default
            }
            ColorPaletteName.MaterialYou -> try {
                dynamicColorPaletteOf(Color(MonetCompat.setup(context).getAccentColor(context)), dark)
            } catch (error: IllegalStateException) {
                Timber.tag("ThemePreview").w(error, "Material You palette is unavailable")
                fallback
            }
            else -> fallback
        }
    }
    val artworkColors = if (!useApplicationPreferences && paletteName == ColorPaletteName.Dynamic) {
        val atlasUri = remember(context) { ThemePreviewAssets.file(context, "atlas.jpg").toURI().toString() }
        val profile = ImageCacheFactory.rememberArtworkProfile()
        val revision = ImageCacheFactory.rememberLocalArtworkRevision(atlasUri, profile)
        val generation by SharedArtworkColors.generation.collectAsStateWithLifecycle(context = NzikDispatchers.DATA)
        val sourceRevision = rememberScopedArtworkColorRevision(atlasUri, profile)
        val colors by produceState<ArtworkColorSnapshot?>(null, context, atlasUri, profile, revision, generation, sourceRevision) {
            value = try {
                SharedArtworkColors.load(context, atlasUri)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag("ThemePreview").w(error, "Demo artwork colors are unavailable")
                null
            }
        }
        colors
    } else null
    val keepDark = preferences[dynamicThemeRespectBrightnessKey] == true
    val artworkPalette = remember(artworkColors, dark, keepDark, base) {
        artworkColors?.display(dark, keepDark)?.palette ?: base
    }
    val appearance = remember(artworkPalette, preferences, paletteMode) {
        val radius = preferences[thumbnailRoundnessDpKey] as? Float ?: 12f
        val uiRadius = preferences[uiRoundnessDpKey] as? Float ?: 25f
        val artistRadius = preferences[artistThumbnailRoundnessDpKey] as? Float ?: 48f
        Appearance(
            colorPalette = artworkPalette,
            typography = typographyOf(
                artworkPalette.text,
                preferences[useSystemFontKey] == true,
                preferences[applyFontPaddingKey] == true,
                preferences.option(fontTypeKey, FontType.Rubik),
            ),
            thumbnailShape = if (radius >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(radius.dp, 0.25f)),
            uiRoundnessShape = RoundedCornerShape(BoundedCornerSize(uiRadius.dp, 0.4f)),
            artistThumbnailShape = if (artistRadius >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(artistRadius.dp, 0.25f)),
        ).forPaletteMode(paletteMode)
    }
    val glassSettings = remember(glass, glassPreferences) {
        glassSettingsFromPreferences(glassPreferences, GlassQuality.Balanced).copy(enabled = glass)
    }
    return ThemePreviewAppearance(preferences, appearance, glassSettings)
}

private inline fun <reified E : Enum<E>> Map<String, Any>.option(key: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == this[key] } ?: fallback
