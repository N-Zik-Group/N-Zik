package app.it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class PlayerPlayButtonType(
    val height: Int,
    val width: Int,
    @field:StringRes override val textId: Int
): TextView {

    Disabled( 60, 60, R.string.common_option_disabled ),

    Default( 60, 60, R.string.common_option_default ),

    Rectangular( 70, 110, R.string.player_play_button_rectangular_label ),

    CircularRibbed( 100, 100, R.string.player_play_button_wavy_circle_label ),

    Square( 80, 80, R.string.player_play_button_square_label );
}



