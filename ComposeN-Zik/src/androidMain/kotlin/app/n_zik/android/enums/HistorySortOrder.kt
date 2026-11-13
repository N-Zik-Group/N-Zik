package app.n_zik.android.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.n_zik.android.R
import app.n_zik.android.core.enums.TextView
import app.n_zik.android.enums.Drawable

enum class HistorySortOrder(
    @field:StringRes override val textId: Int,
    @field:DrawableRes override val iconId: Int
): TextView, Drawable {
    DATE(R.string.library_sort_history_date, R.drawable.time),
    ALPHABETICAL(R.string.library_sort_history_alphabetical, R.drawable.text),
    ARTIST(R.string.artist_title, R.drawable.artist)
}
