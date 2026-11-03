package app.it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.it.fast4x.rimusic.ui.styling.px
import app.kreate.android.me.knighthat.enums.TextView

enum class HomeItemSize (
    @field:StringRes override val textId: Int,
    val size: Int
): TextView, Drawable {

    SMALL( R.string.common_size_small, 100 ),
    MEDIUM( R.string.common_size_medium,130 ),
    BIG( R.string.common_size_big, 160 );

    @field:DrawableRes
    override val iconId = R.drawable.arrow_forward

    val dp: Dp = this.size.dp
    val px: Int
        @Composable
        get() = this.dp.px
}



