package app.it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.me.knighthat.enums.TextView
import app.n_zik.android.R

enum class LocalRecommandationsNumber(
    @field:StringRes override val textId: Int,
    val value: Int
) : TextView {
    OneQ(R.string.recommendations_quick_picks_count, 1),
    TwoQ(R.string.recommendations_quick_picks_count, 2),
    ThreeQ(R.string.recommendations_quick_picks_count, 3),
    FourQ(R.string.recommendations_quick_picks_count, 4),
    FiveQ(R.string.recommendations_quick_picks_count, 5),
    SixQ(R.string.recommendations_quick_picks_count, 6);
}



