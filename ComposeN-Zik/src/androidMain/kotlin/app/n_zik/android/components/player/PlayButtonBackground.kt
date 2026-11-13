package app.n_zik.android.components.player

import androidx.compose.ui.graphics.Color
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.enums.PlayerPlayButtonType
import app.n_zik.android.ui.styling.ColorPalette

/** Shaped artwork owns its fill; its surrounding container must stay transparent. */
internal fun playButtonContainerColor(
    type: PlayerPlayButtonType,
    paletteName: ColorPaletteName,
    palette: ColorPalette,
    gradient: Boolean,
): Color = when (type) {
    PlayerPlayButtonType.CircularRibbed, PlayerPlayButtonType.Disabled -> Color.Transparent
    else -> when (paletteName) {
        ColorPaletteName.PureBlack, ColorPaletteName.ModernBlack -> palette.background4
        else -> if (gradient) palette.background1 else palette.background2
    }
}
