package app.it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class PlayerBackgroundColors(
    @field:StringRes override val textId: Int
): TextView {

    CoverColor( R.string.player_background_artwork_label ),

    ThemeColor( R.string.player_background_theme_label ),

    CoverColorGradient( R.string.player_background_artwork_gradient_label ),

    ThemeColorGradient( R.string.player_background_theme_gradient_label ),

    BlurredCoverColor( R.string.player_background_artwork_blurred_label ),

    ColorPalette( R.string.player_background_palette_label ),

    AnimatedGradient( R.string.player_background_animated_gradient_label );
}



