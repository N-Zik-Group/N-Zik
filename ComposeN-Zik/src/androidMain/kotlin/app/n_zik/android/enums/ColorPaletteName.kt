package app.n_zik.android.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.n_zik.android.core.enums.TextView

enum class ColorPaletteName(
    @field:StringRes override val textId: Int
): TextView {

    Default( R.string.common_option_default ),

    Dynamic( R.string.theme_palette_dynamic ),

    PureBlack( R.string.theme_palette_pure_black ),

    ModernBlack( R.string.theme_palette_modern_black ),

    MaterialYou( R.string.theme_palette_material_you ),

    Customized( R.string.theme_palette_custom ),

    CustomColor( R.string.settings_theme_custom_colors_label);
}




