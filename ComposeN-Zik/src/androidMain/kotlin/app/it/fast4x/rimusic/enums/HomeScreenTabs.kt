package app.it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class HomeScreenTabs(
    val index: Int,
    @field:StringRes override val textId: Int
): TextView {

    Default( 0, R.string.common_option_default ),

    QuickPics( 0, R.string.recommendations_title ),

    Songs( 1, R.string.library_songs_title ),

    Artists( 2, R.string.library_artists_title ),

    Albums( 3, R.string.library_albums_title ),

    Playlists( 4, R.string.library_playlists_title ),

    Search( 5, R.string.search_title );
}



