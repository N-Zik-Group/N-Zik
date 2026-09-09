package app.it.fast4x.rimusic.enums

import androidx.compose.runtime.Composable
import app.kreate.android.me.knighthat.enums.TextView
import app.n_zik.android.R
import androidx.compose.ui.res.stringResource

enum class DislikeMode : TextView {
    Enabled,
    Disabled;

    override val text: String
        @Composable
        get() = when (this) {
            Enabled -> stringResource(R.string.on)
            Disabled -> stringResource(R.string.off)
        }

    val isEnabled: Boolean get() = this == Enabled
}
