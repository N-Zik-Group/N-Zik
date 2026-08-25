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

    Title( R.string.sort_title, R.drawable.text ),

    Artist( R.string.sort_artist, R.drawable.artist ),

    Album( R.string.sort_album, R.drawable.album ),

    ArtistAndAlbum( -1, R.drawable.artist ),

    Duration( R.string.sort_duration, R.drawable.time ),

    PlayCount( R.string.sort_play_count, R.drawable.play ),

    PlayTime( R.string.sort_listening_time, R.drawable.trending ),

    RelativePlayTime( R.string.relative_listening_time, R.drawable.stats_chart ),

    DateAdded( R.string.sort_date_added, R.drawable.time ),

    DatePlayed( R.string.sort_date_played, R.drawable.up_right_arrow ),

    DateLiked( R.string.sort_date_liked, R.drawable.heart ),

    AlbumYear( R.string.sort_album_year, R.drawable.calendar ),

    Downloaded( R.string.sort_downloaded, R.drawable.downloaded ),

    Custom( R.string.sort_custom_order, R.drawable.position );

    override val text: String
        @Composable
        get() = when( this ) {
            ArtistAndAlbum -> "${Artist.text}, ${Album.text}"
            else -> super.text
        }
}




