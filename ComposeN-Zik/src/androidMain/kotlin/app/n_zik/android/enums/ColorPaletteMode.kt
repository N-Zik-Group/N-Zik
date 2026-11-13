package app.n_zik.android.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.n_zik.android.core.enums.TextView

enum class ColorPaletteMode(
    @field:StringRes override val textId: Int
): TextView {
    Light( R.string.settings_theme_light_mode_label ),
    Dark( R.string.settings_theme_dark_mode_label ),
    System( R.string.settings_theme_mode_label ),
    PitchBlack( R.string.theme_mode_pitch_black );
}




