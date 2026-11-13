package app.n_zik.android.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.n_zik.android.core.enums.TextView

enum class MiniPlayerType(
    @field:StringRes override val textId: Int
): TextView {

    Essential( R.string.player_controls_style_essential ),
    Modern( R.string.player_controls_style_modern );
}



