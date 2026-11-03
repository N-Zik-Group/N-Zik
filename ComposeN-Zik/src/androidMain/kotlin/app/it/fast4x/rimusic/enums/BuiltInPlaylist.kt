package app.it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class BuiltInPlaylist(
    @field:DrawableRes override val iconId: Int,
    @field:StringRes override val textId: Int
): Drawable, TextView {

    All( R.drawable.musical_notes, R.string.common_option_all ),

    Favorites( R.drawable.heart, R.string.library_favorites_title ),

    Offline( R.drawable.sync, R.string.library_cached_title ),

    Downloaded( R.drawable.downloaded, R.string.library_downloaded_title ),

    Top( R.drawable.trending, R.string.playlist_top ),

    OnDevice( R.drawable.musical_notes, R.string.library_on_device_title ),

    Disliked( R.drawable.heart_dislike, R.string.library_disliked_title )
}




