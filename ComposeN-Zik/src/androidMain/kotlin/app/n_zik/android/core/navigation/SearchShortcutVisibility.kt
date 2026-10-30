package app.n_zik.android.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import app.it.fast4x.rimusic.enums.NavRoutes

/** Outgoing animated pages retain their own route until they leave composition. */
@Composable
internal fun currentSearchShortcutRoute(navController: NavController): String? {
    val owner = currentPageNavigationEntry()
    if (owner != null) return owner.destination.route
    val currentEntry by navController.currentBackStackEntryAsState()
    return currentEntry?.destination?.route
}

/** Search destinations already expose their own query controls. */
internal fun shouldShowSearchShortcut(route: String?): Boolean =
    when (route?.substringBefore('?')?.substringBefore('/')) {
        NavRoutes.search.name, NavRoutes.searchResults.name -> false
        else -> true
    }
