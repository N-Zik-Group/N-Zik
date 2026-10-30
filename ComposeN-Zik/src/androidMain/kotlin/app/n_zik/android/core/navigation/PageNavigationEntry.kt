package app.n_zik.android.core.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.savedstate.compose.LocalSavedStateRegistryOwner

/** The outgoing page must keep its identity while NavHost composes its replacement. */
@Composable
internal fun currentPageNavigationEntry(): NavBackStackEntry? =
    LocalSavedStateRegistryOwner.current as? NavBackStackEntry
        ?: LocalLifecycleOwner.current as? NavBackStackEntry
        // View-model scopes may already belong to the incoming entry during a transition.
        ?: LocalViewModelStoreOwner.current as? NavBackStackEntry
