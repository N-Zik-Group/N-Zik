package app.n_zik.android.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class ShakeSensitivityTheme(
    @field:StringRes override val textId: Int,
    val thresholdG: Float
): TextView {

    Disabled(R.string.shake_sensitivity_theme_disabled, 0f),
    VeryHigh(R.string.shake_sensitivity_theme_very_high, 1.0f),
    High(R.string.shake_sensitivity_theme_high, 2.2f),
    Medium(R.string.shake_sensitivity_theme_medium, 3.5f),
    Low(R.string.shake_sensitivity_theme_low, 5.0f);
}
