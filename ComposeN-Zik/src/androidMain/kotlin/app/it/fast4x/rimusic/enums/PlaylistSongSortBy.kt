package app.it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class PlaylistSongSortBy(
    @field:StringRes override val textId: Int,
    @field:DrawableRes override val iconId: Int
): TextView, Drawable {

    Title( R.string.library_sort_title, R.drawable.text ),

    Artist( R.string.library_sort_artist, R.drawable.artist ),

    Album( R.string.library_sort_album, R.drawable.album ),

    ArtistAndAlbum( -1, R.drawable.artist ),

    Duration( R.string.library_sort_duration, R.drawable.time ),

    PlayCount( R.string.library_sort_play_count, R.drawable.play ),

    PlayTime( R.string.library_sort_listening_time, R.drawable.trending ),

    RelativePlayTime( R.string.library_sort_relative_listening_time, R.drawable.stats_chart ),

    DateAdded( R.string.library_sort_date_added, R.drawable.time ),

    DatePlayed( R.string.library_sort_date_played, R.drawable.up_right_arrow ),

    DateLiked( R.string.library_sort_date_liked, R.drawable.heart ),

    AlbumYear( R.string.library_sort_album_year, R.drawable.calendar ),

    Downloaded( R.string.library_sort_downloaded, R.drawable.downloaded ),

    Custom( R.string.library_sort_custom_order, R.drawable.position ),

    // Spec 2: only shown for generated `rewind-*` playlists (filtered in PlaylistSongsSort);
    // sorts by the top order written at generation time (a live sort never rewrites it)
    RewindTop( R.string.rewind_top_sort, R.drawable.position );

    override val text: String
        @Composable
        get() = when( this ) {
            ArtistAndAlbum -> "${Artist.text}, ${Album.text}"
            else -> super.text
        }
}




