package app.it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class AnimatedGradient(
    @field:StringRes override val textId: Int
): TextView {

    FluidThemeColorGradient( R.string.player_background_theme_fluid_gradient_label ),

    FluidCoverColorGradient( R.string.player_background_artwork_fluid_gradient_label ),

    M3EMorphingTheme( R.string.player_background_theme_m3e_morphing_label ),

    M3EMorphingCover( R.string.player_background_artwork_m3e_morphing_label ),

    Linear( R.string.player_background_linear_gradient ),

    Mesh( R.string.player_background_mesh_gradient ),

    MesmerizingLens( R.string.player_background_mesmerizing_lens ),

    GlossyGradients( R.string.player_background_glossy_gradient ),

    GradientFlow( R.string.player_background_gradient_flow ),

    PurpleLiquid( R.string.player_background_purple_liquid ),

    InkFlow( R.string.player_background_ink_flow ),

    OilFlow( R.string.player_background_oil_flow ),

    IceReflection( R.string.player_background_ice_reflection ),

    Stage( R.string.player_background_stage ),

    GoldenMagma( R.string.player_background_golden_magma ),

    BlackCherryCosmos( R.string.player_background_black_cherry_cosmos ),

    Random( R.string.common_option_random );
}



