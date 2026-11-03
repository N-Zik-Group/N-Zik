package app.it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class SongSortBy(
    @field:StringRes override val textId: Int,
    @field:DrawableRes override val iconId: Int
): TextView, Drawable {

    Title( R.string.library_sort_title, R.drawable.text ),

    Artist( R.string.library_sort_artist, R.drawable.artist ),

    AlbumName( R.string.library_sort_album, R.drawable.album ),

    Duration( R.string.library_sort_duration, R.drawable.time ),

    PlayCount( R.string.library_sort_play_count, R.drawable.play ),

    PlayTime( R.string.library_sort_listening_time, R.drawable.trending ),

    RelativePlayTime( R.string.library_sort_relative_listening_time, R.drawable.stats_chart ),

    DateAdded( R.string.library_sort_date_added, R.drawable.time ),

    DatePlayed( R.string.library_sort_date_played, R.drawable.calendar ),

    DateLiked( R.string.library_sort_date_liked, R.drawable.heart ),

    Downloaded( R.string.library_sort_downloaded, R.drawable.downloaded ),

    Custom( R.string.library_sort_custom_order, R.drawable.position );
}




