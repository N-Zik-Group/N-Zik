package app.n_zik.android.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.savedstate.compose.LocalSavedStateRegistryOwner

internal val LocalPageNavigationEntry = staticCompositionLocalOf<NavBackStackEntry?> { null }

/** The outgoing page must keep its identity while NavHost composes its replacement. */
@Composable
internal fun currentPageNavigationEntry(): NavBackStackEntry? =
    LocalPageNavigationEntry.current
        ?: LocalSavedStateRegistryOwner.current as? NavBackStackEntry
        ?: LocalLifecycleOwner.current as? NavBackStackEntry
        ?: LocalViewModelStoreOwner.current as? NavBackStackEntry
