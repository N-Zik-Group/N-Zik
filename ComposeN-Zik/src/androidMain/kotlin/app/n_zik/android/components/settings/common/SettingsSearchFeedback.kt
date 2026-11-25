package app.n_zik.android.components.settings.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.core.utils.Toaster

internal val LocalSettingsSearchMessage = staticCompositionLocalOf<(String) -> Unit> { { Toaster.i(it) } }

/** Wait for typing to settle; recomposition and intermediate keystrokes never enqueue more toasts. */
@Composable
fun SettingsSearchEmptyFeedback(query: String, empty: Boolean) {
    SearchEmptyFeedback(query, empty, showMessage = LocalSettingsSearchMessage.current)
}
