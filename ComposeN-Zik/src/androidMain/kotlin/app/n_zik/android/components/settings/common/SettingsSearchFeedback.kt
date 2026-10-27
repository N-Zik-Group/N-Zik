package app.n_zik.android.components.settings.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import kotlinx.coroutines.delay

internal val LocalSettingsSearchMessage = staticCompositionLocalOf<(String) -> Unit> { { Toaster.i(it) } }

/** Wait for typing to settle; recomposition and intermediate keystrokes never enqueue more toasts. */
@Composable
fun SettingsSearchEmptyFeedback(query: String, empty: Boolean) {
    val message = stringResource(R.string.no_results_found)
    val showMessage = rememberUpdatedState(LocalSettingsSearchMessage.current)
    LaunchedEffect(query, empty) {
        if (query.isNotBlank() && empty) {
            delay(500)
            showMessage.value(message)
        }
    }
}
