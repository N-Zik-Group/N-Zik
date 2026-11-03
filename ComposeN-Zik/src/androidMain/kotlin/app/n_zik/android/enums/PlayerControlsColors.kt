package app.n_zik.android.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView

enum class PlayerControlsColors(
    @field:StringRes override val textId: Int
): TextView {
    Theme(R.string.theme),
    Cover(R.string.artwork),
    Monochrome(R.string.monochrome);
}
