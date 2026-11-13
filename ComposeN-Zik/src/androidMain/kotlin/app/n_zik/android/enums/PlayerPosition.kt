package app.n_zik.android.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.n_zik.android.core.enums.TextView

enum class PlayerPosition(
    @field:StringRes override val textId: Int
): TextView {

    Top( R.string.player_position_top_label ),
    Bottom( R.string.player_position_bottom_label );
}



