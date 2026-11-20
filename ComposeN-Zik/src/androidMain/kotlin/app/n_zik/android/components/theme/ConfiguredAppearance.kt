package app.n_zik.android.components.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.BoundedCornerSize
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.colorPaletteOf
import app.n_zik.android.ui.styling.customColorPalette
import app.n_zik.android.ui.styling.dynamicColorPaletteOf
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.utils.applyFontPaddingKey
import app.n_zik.android.utils.artistThumbnailRoundnessDpKey
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.customColorKey
import app.n_zik.android.utils.fontTypeKey
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.thumbnailRoundnessDpKey
import app.n_zik.android.utils.useSystemFontKey

internal fun configuredApplicationAppearance(
    context: Context,
    isSystemInDarkTheme: Boolean,
    materialYouAccent: () -> Color,
): Appearance = with(context.preferences) {
    val colorPaletteMode = getEnum(colorPaletteModeKey, ColorPaletteMode.Dark)
    val thumbnailRoundnessDp = getFloat(thumbnailRoundnessDpKey, 12f)
    val artistThumbnailRoundnessDp = getFloat(artistThumbnailRoundnessDpKey, 48f)
    val uiRoundnessDp = getFloat("uiRoundnessDpKey", 25f)
    val useSystemFont = getBoolean(useSystemFontKey, false)
    val applyFontPadding = getBoolean(applyFontPaddingKey, false)

    val palettePreferences = context.preferences
    val colorPalette = configuredApplicationPalette(
        context,
        palettePreferences,
        colorPaletteName = palettePreferences.getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic),
        colorPaletteMode = palettePreferences.getEnum(colorPaletteModeKey, ColorPaletteMode.Dark),
        isSystemInDarkTheme = isSystemInDarkTheme,
        materialYouAccent = materialYouAccent,
    )
    val fontType = getEnum(fontTypeKey, FontType.Rubik)
    Appearance(
        colorPalette = colorPalette,
        typography = typographyOf(
            colorPalette.text,
            useSystemFont,
            applyFontPadding,
            fontType
        ),
        thumbnailShape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f)),
        uiRoundnessShape = RoundedCornerShape(BoundedCornerSize(uiRoundnessDp.dp, 0.4f)),
        artistThumbnailShape = if (artistThumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(artistThumbnailRoundnessDp.dp, 0.25f))
    ).forPaletteMode(colorPaletteMode)
}

internal fun configuredApplicationPalette(
    context: Context,
    preferences: SharedPreferences,
    colorPaletteName: ColorPaletteName,
    colorPaletteMode: ColorPaletteMode,
    isSystemInDarkTheme: Boolean,
    materialYouAccent: () -> Color,
): ColorPalette {
    val paletteIsDark = colorPaletteMode == ColorPaletteMode.Dark ||
        colorPaletteMode == ColorPaletteMode.PitchBlack ||
        colorPaletteMode == ColorPaletteMode.PureBlack ||
        (colorPaletteMode == ColorPaletteMode.System && isSystemInDarkTheme)

    return when (colorPaletteName) {
        ColorPaletteName.MaterialYou -> dynamicColorPaletteOf(materialYouAccent(), paletteIsDark)
        ColorPaletteName.CustomColor -> dynamicColorPaletteOf(
            Color(preferences.getInt(customColorKey, Color.Green.hashCode())),
            paletteIsDark,
        )
        ColorPaletteName.Customized -> customColorPalette(
            colorPaletteOf(colorPaletteName, colorPaletteMode, isSystemInDarkTheme),
            context,
            isSystemInDarkTheme,
        )
        else -> colorPaletteOf(colorPaletteName, colorPaletteMode, isSystemInDarkTheme)
    }
}
