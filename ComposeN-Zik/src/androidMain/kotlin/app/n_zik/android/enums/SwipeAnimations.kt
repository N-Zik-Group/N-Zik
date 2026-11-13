package app.n_zik.android.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.n_zik.android.core.enums.TextView

enum class SwipeAnimationNoThumbnail(
    @StringRes override val textId: Int
): TextView {

    Sliding( R.string.settings_transition_slide_vertical ),

    Fade( R.string.settings_transition_fade ),

    Scale( R.string.settings_transition_scale ),

    Carousel( R.string.settings_player_artwork_carousel_label ),

    Circle( R.string.player_artwork_transition_circular )
}



