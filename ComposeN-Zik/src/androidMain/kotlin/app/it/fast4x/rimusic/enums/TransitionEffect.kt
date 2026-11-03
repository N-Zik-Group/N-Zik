package app.it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class TransitionEffect(
    @field:StringRes override val textId: Int
): TextView {

    SlideVertical( R.string.settings_transition_slide_vertical ),

    SlideHorizontal( R.string.settings_transition_slide_horizontal ),

    Scale( R.string.settings_transition_scale ),

    Fade( R.string.settings_transition_fade ),

    Expand( R.string.settings_transition_expand ),

    None( R.string.common_option_none );
}



