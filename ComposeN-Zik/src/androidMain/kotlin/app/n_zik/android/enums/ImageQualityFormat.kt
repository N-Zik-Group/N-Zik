package app.n_zik.android.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.n_zik.android.core.enums.TextView

enum class ImageQualityFormat(
    @field:StringRes override val textId: Int
): TextView {

    Auto( R.string.audio_quality_automatic ),

    High( R.string.audio_quality_format_high ),

    Medium( R.string.audio_quality_format_medium ),

    Low( R.string.audio_quality_format_low );
}




