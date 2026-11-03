package app.it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class CarouselSize(
    val size: Int,
    @field:StringRes override val textId: Int
): TextView {

    Small( 90, R.string.common_size_small ),

    Medium( 55, R.string.common_size_medium ),

    Big( 30, R.string.common_size_big ),

    Biggest( 20, R.string.common_size_biggest ),

    Expanded( 0, R.string.common_size_expanded );
}




