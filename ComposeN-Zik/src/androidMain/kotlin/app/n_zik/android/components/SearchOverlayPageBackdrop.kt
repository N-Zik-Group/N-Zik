package app.n_zik.android.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import app.it.fast4x.rimusic.enums.NavRoutes
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.core.navigation.currentPageNavigationEntry

// Sibling recognition overlays cannot inherit Skeleton's CompositionLocals.
private class SearchOverlayPageBackdropState : ViewModel() {
    var backdrop: Backdrop? by mutableStateOf(null)

    override fun onCleared() {
        backdrop = null
    }
}

@Composable
private fun searchOverlayPageState(entry: NavBackStackEntry?): SearchOverlayPageBackdropState? {
    if (entry?.destination?.route?.substringBefore('?')?.substringBefore('/') != NavRoutes.search.name) return null
    return viewModel(viewModelStoreOwner = entry) { SearchOverlayPageBackdropState() }
}

/** Exports only the search page capture, never the scene containing its sibling overlays. */
@Composable
internal fun RegisterSearchOverlayPageBackdrop(entry: NavBackStackEntry?, backdrop: Backdrop?) {
    val state = searchOverlayPageState(entry) ?: return
    DisposableEffect(state, backdrop) {
        state.backdrop = backdrop
        onDispose {
            if (state.backdrop === backdrop) state.backdrop = null
        }
    }
}

@Composable
internal fun rememberSearchOverlayPageBackdrop(): Backdrop? =
    searchOverlayPageState(currentPageNavigationEntry())?.backdrop
