package app.it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class ArtistsType(
    @field:StringRes override val textId: Int
): TextView {

    Favorites( R.string.library_favorites_title ),
    Library( R.string.library_title ),
    Disliked( R.string.library_disliked_title );
}



