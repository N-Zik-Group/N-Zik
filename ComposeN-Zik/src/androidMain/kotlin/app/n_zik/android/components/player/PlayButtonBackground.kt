package app.n_zik.android.components.player

import androidx.compose.ui.graphics.Color
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.enums.PlayerPlayButtonType
import app.n_zik.android.ui.styling.ColorPalette

/** Black palettes keep the central transport plate opaque over artwork. */
internal fun playButtonNativeColor(
    paletteName: ColorPaletteName,
    original: Color,
    mode: ColorPaletteMode = ColorPaletteMode.Dark,
): Color = if (mode == ColorPaletteMode.PureBlack) Color.Black else
    when (paletteName) {
        ColorPaletteName.PureBlack, ColorPaletteName.ModernBlack -> Color.Black
        else -> original
    }

/** Shaped artwork owns its fill; its surrounding container must stay transparent. */
internal fun playButtonContainerColor(
    type: PlayerPlayButtonType,
    paletteName: ColorPaletteName,
    palette: ColorPalette,
    gradient: Boolean,
    mode: ColorPaletteMode = ColorPaletteMode.Dark,
): Color = when (type) {
    PlayerPlayButtonType.CircularRibbed, PlayerPlayButtonType.Disabled -> Color.Transparent
    else -> playButtonNativeColor(
        paletteName, if (gradient) palette.background1 else palette.background2, mode,
    )
}
