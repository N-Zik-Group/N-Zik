package app.n_zik.android.updater.models

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R

/**
 * The state of the "check for updates" setting: silent auto-check at
 * startup, ask the user once per update, or disabled.
 *
 * Migrated from the legacy `app.it.fast4x.rimusic.enums.CheckUpdateState` —
 * the composable [text] getter is kept self-contained instead of relying on
 * the legacy `TextView` interface.
 */
enum class CheckUpdateState(
    @field:StringRes val textId: Int
) {
    Enabled(R.string.enabled),
    Disabled(R.string.vt_disabled),
    Ask(R.string.ask);

    /** The localized display text of this state. */
    val text: String
        @Composable
        get() = stringResource(textId)
}
