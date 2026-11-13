package app.n_zik.android.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.n_zik.android.core.enums.TextView

enum class ExoPlayerCacheLocation(
    @field:StringRes override val textId: Int
): TextView {

    System( R.string.cache_location_system ),
    Private( R.string.cache_location_private );
}



