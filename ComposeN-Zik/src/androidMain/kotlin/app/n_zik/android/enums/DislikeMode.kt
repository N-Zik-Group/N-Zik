package app.n_zik.android.enums

import androidx.compose.runtime.Composable
import app.n_zik.android.core.enums.TextView
import app.n_zik.android.R
import androidx.compose.ui.res.stringResource

enum class DislikeMode : TextView {
    Enabled,
    Disabled;

    override val text: String
        @Composable
        get() = when (this) {
            Enabled -> stringResource(R.string.common_state_on)
            Disabled -> stringResource(R.string.common_state_off)
        }

    val isEnabled: Boolean get() = this == Enabled
}
