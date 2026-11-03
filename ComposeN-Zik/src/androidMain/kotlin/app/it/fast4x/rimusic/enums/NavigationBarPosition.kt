package app.it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import app.n_zik.android.R
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.kreate.android.me.knighthat.enums.TextView

enum class NavigationBarPosition(
    @field:StringRes override val textId: Int
): TextView {

    Left( R.string.lyrics_alignment_left_label ),
    Right( R.string.lyrics_alignment_right_label ),
    Top( R.string.navigation_position_top_label ),
    Bottom( R.string.navigation_position_bottom_label ),
    BottomFloating( R.string.navigation_position_bottom_floating_label );


    companion object {

        @Composable
        fun current() = rememberPreference( navigationBarPositionKey, NavigationBarPosition.BottomFloating ).value

    }

    @Composable
    fun isCurrent(): Boolean = current() == this

    val isHorizontal: Boolean
        get() = this == Top || this == Bottom || this == BottomFloating

    val isBottomType: Boolean
        get() = this == Bottom || this == BottomFloating

    val isFloating: Boolean
        get() = this == BottomFloating
}




