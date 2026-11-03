package app.it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class PlayEventsType(
    @field:StringRes override val textId: Int,
    @field:DrawableRes override val iconId: Int
): TextView, Drawable {

    MostPlayed( R.string.home_sort_most_played_song_label, R.drawable.chevron_up ),
    LastPlayed( R.string.home_sort_last_played_song_label, R.drawable.chevron_down ),
    CasualPlayed( R.string.home_sort_random_label, R.drawable.random );
}



