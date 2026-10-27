package app.n_zik.android.core.navigation

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import app.it.fast4x.rimusic.enums.NavRoutes

/** Uses the active Android back handler, including nested hosts and the back-to-home preference. */
@Composable
fun rememberAppBackAction(navController: NavController): () -> Unit {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val settingsNavigation = LocalSettingsBackNavigation.current
    return remember(navController, dispatcher, settingsNavigation) {
        {
            val route = navController.currentDestination?.route
            // The outgoing arrow remains drawn during its exit animation. Do not let a second
            // tap reach Home's exit handler after the first tap has already returned there.
            val isHome = route?.startsWith(NavRoutes.home.name) == true || route == NavRoutes.queue.name
            if (!isHome && (navController.previousBackStackEntry != null || settingsNavigation?.hasNestedBackStack == true)) {
                dispatcher?.onBackPressed()
            }
        }
    }
}
