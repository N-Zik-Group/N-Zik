package app.n_zik.android.components.ui.screens.home.quickpicks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun QuickPicksInitialLoadEffect(
    stateKey: Any,
    resolveLoggedIn: suspend () -> Boolean,
    onLoggedInResolved: (Boolean) -> Unit,
    load: () -> Unit,
) {
    LaunchedEffect(stateKey) {
        // Resolving an existing account must not queue an additional forced load.
        onLoggedInResolved(resolveLoggedIn())
        load()
    }
}
