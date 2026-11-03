package app.it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class AlbumSwipeAction(
    @field:DrawableRes override val iconId: Int,
    @field:StringRes override val textId: Int,
): Drawable, TextView {

    NoAction( R.drawable.close, R.string.common_option_none ),

    PlayNext( R.drawable.play_skip_forward, R.string.player_action_play_next ),

    Bookmark( R.drawable.bookmark_outline, R.string.library_action_bookmark ),

    Enqueue( R.drawable.enqueue, R.string.player_action_enqueue );

    fun getStateIcon(bookmarkedState: Long?): Int? {
        return when (this) {
            Bookmark -> when(bookmarkedState) {
                null -> R.drawable.bookmark_outline
                else -> R.drawable.bookmark
            }
            NoAction -> null
            else -> iconId
        }
    }
}




