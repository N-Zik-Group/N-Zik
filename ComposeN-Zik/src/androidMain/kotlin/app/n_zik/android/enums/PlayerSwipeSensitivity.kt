package app.n_zik.android.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class PlayerSwipeSensitivity(
    @field:StringRes override val textId: Int,
    val threshold: Float
): TextView {

    Low(R.string.player_swipe_sensitivity_low_label, 150f),
    Medium(R.string.player_swipe_sensitivity_medium_label, 75f),
    High(R.string.player_swipe_sensitivity_high_label, 40f),
    VeryHigh(R.string.player_swipe_sensitivity_very_high_label, 15f);
}
