package app.it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class PlayerTimelineType(
    @field:StringRes override val textId: Int
): TextView {

    Default( R.string._default ),

    Wavy( R.string.wavy_timeline ),

    PinBar( R.string.pin_bar ),

    BodiedBar( R.string.bodied_bar ),

    AudioWaves( R.string.audio_waveform ),

    ThinBar( R.string.thin_bar ),

    ColoredBar( R.string.colored_bar ),

    VisualizerBar( R.string.visualizer_bar );
}



