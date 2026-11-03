package app.n_zik.android.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.applyPitchBlack
import app.it.fast4x.rimusic.ui.styling.colorPaletteOf
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.components.player.ArtworkColorSnapshot
import app.n_zik.android.components.player.SharedArtworkColors
import app.n_zik.android.components.theme.DYNAMIC_THEME_RESPECT_BRIGHTNESS_DEFAULT
import app.n_zik.android.components.theme.dynamicThemeRespectBrightnessKey

/** Resolves complete cover roles for the active profile; never reads the old partial widget cache. */
internal suspend fun loadWidgetArtworkColors(context: Context, uri: String?, bitmap: Bitmap?): ArtworkColorSnapshot? =
    // BitmapProvider can still expose the preceding cover while a new URI is loading.
    // Only URI-less callers can prove that their bitmap is the authoritative source.
    SharedArtworkColors.load(context, uri, bitmap = bitmap.takeIf { uri.isNullOrBlank() })

internal fun widgetArtworkPalette(context: Context, snapshot: ArtworkColorSnapshot?): ColorPalette {
    val preferences = context.preferences
    val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val mode = preferences.getEnum(colorPaletteModeKey, ColorPaletteMode.Dark)
    val dark = mode == ColorPaletteMode.Dark || mode == ColorPaletteMode.PitchBlack || mode == ColorPaletteMode.System && systemDark
    val keepDark = preferences.getBoolean(dynamicThemeRespectBrightnessKey, DYNAMIC_THEME_RESPECT_BRIGHTNESS_DEFAULT)
    val palette = snapshot?.display(dark, keepDark)?.palette
        ?: colorPaletteOf(ColorPaletteName.Dynamic, mode, systemDark)
    return if (mode == ColorPaletteMode.PitchBlack) palette.applyPitchBlack else palette
}
