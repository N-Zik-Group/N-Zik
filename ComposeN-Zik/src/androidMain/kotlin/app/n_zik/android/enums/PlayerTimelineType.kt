package app.n_zik.android.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.n_zik.android.core.enums.TextView

enum class PlayerTimelineType(
    @field:StringRes override val textId: Int
): TextView {

    Default( R.string.common_option_default ),

    Wavy( R.string.player_timeline_wavy_label ),

    PinBar( R.string.player_timeline_pin_bar_label ),

    BodiedBar( R.string.player_timeline_fat_bar_label ),

    AudioWaves( R.string.audio_waveform ),

    ThinBar( R.string.player_timeline_thin_bar_label ),

    ColoredBar( R.string.player_timeline_colored_bar_label ),

    VisualizerBar( R.string.visualizer_bar );
}



