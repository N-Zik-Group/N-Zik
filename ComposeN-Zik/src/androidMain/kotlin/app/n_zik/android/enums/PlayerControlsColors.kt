package app.n_zik.android.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.n_zik.android.core.enums.TextView

enum class PlayerControlsColors(
    @field:StringRes override val textId: Int
): TextView {
    Theme(R.string.common_color_source_theme),
    Cover(R.string.common_color_source_artwork),
    Monochrome(R.string.player_controls_monochrome_label);
}
