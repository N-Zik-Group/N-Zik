package app.n_zik.android.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.n_zik.android.core.enums.TextView

enum class ThumbnailCoverType(
    @field:StringRes override val textId: Int
): TextView {

    Vinyl (R.string.player_cover_style_vinyl ),
    CD( R.string.player_cover_style_cd ),
    CDwithCover( R.string.player_cover_style_cd_with_cover );
}



