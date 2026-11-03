package app.it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class AlbumSortBy(
    @field:StringRes override val textId: Int,
    @field:DrawableRes override val iconId: Int
): Drawable, TextView {

    Title( R.string.library_sort_name, R.drawable.text ),

    Artist( R.string.library_sort_artist, R.drawable.artist ),

    Songs( R.string.library_sort_songs_number, R.drawable.medical ),

    Duration( R.string.library_sort_duration, R.drawable.time ),

    PlayCount( R.string.library_sort_play_count, R.drawable.play ),

    ListeningTime( R.string.library_sort_listening_time, R.drawable.trending ),

    DateAdded( R.string.library_sort_date_added, R.drawable.time ),

    Year( R.string.library_sort_album_year, R.drawable.calendar ),

    Custom( R.string.library_sort_custom_order, R.drawable.position );
}




