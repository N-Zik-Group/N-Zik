package app.it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class IconLikeType(
    @field:StringRes override val textId: Int
): TextView {

    Apple( R.string.player_like_icon_apple ),

    Breaked( R.string.player_like_icon_broken ),

    Brilliant( R.string.player_like_icon_brilliant ),

    Essential( R.string.player_controls_style_essential ),

    Gift( R.string.player_like_icon_gift ),

    Shape( R.string.player_like_icon_cute ),

    Striped( R.string.player_like_icon_striped );
}



