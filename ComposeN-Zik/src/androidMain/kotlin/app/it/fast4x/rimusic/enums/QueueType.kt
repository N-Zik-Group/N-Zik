package app.it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class QueueType(
    @field:StringRes override val textId: Int
): TextView {

    Essential( R.string.player_controls_style_essential ),
    Modern( R.string.player_controls_style_modern );
}



