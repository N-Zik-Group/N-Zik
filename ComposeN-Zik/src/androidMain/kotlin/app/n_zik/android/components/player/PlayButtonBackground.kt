package app.n_zik.android.components.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.enums.PlayerPlayButtonType
import app.n_zik.android.ui.styling.ColorPalette

/** Black palettes keep the central transport plate opaque over artwork. */
internal fun playButtonNativeColor(
    paletteName: ColorPaletteName,
    original: Color,
    mode: ColorPaletteMode = ColorPaletteMode.Dark,
    blackCoverage: Float? = null,
): Color {
    val coverage = blackCoverage ?: if (mode == ColorPaletteMode.PureBlack ||
        paletteName == ColorPaletteName.PureBlack || paletteName == ColorPaletteName.ModernBlack) 1f else 0f
    return when (coverage) {
        0f -> original
        1f -> Color.Black
        else -> lerp(original, Color.Black, coverage)
    }
}

/** Shaped artwork owns its fill; its surrounding container must stay transparent. */
internal fun playButtonContainerColor(
    type: PlayerPlayButtonType,
    paletteName: ColorPaletteName,
    palette: ColorPalette,
    gradient: Boolean,
    mode: ColorPaletteMode = ColorPaletteMode.Dark,
    blackCoverage: Float? = null,
): Color = when (type) {
    PlayerPlayButtonType.CircularRibbed, PlayerPlayButtonType.Disabled -> Color.Transparent
    else -> playButtonNativeColor(
        paletteName, if (gradient) palette.background1 else palette.background2, mode, blackCoverage,
    )
}
