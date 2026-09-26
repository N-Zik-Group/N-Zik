package app.n_zik.android.utils

import app.it.fast4x.rimusic.enums.NavRoutes

/**
 * Whether the screen for [route] renders the app's bottom navigation bar.
 *
 * The global mini-player reserves room for that bar only on these routes; on
 * every other route it sits just above the system navigation bar
 * (issue https://github.com/N-Zik-Group/N-Zik/issues/832).
 *
 * Matching is against the destination route pattern (e.g. `artist/{id}`,
 * `search?text={text}`); parameterized routes keep a boundary guard
 * (`artist/`, `settings?`, `search?`) so lookalike routes are not swallowed.
 *
 * Known exception: Home filters its tabs by preference and can fall back to a
 * single tab, in which case the bar is not drawn yet this still reports it as
 * present — a phantom gap only (never an overlap).
 *
 * Keep this list in sync with each screen's `navBarContent`: add a route when
 * its screen gains a bar with two or more buttons, remove it when the bar goes.
 *
 * @param route The current navigation route (destination route pattern, e.g.
 * `artist/{id}` or `search?text={text}`), or null before the first destination.
 * @return true when the route's screen shows the app's bottom navigation bar.
 */
fun appNavBarPresentForRoute(route: String?): Boolean = when {
    route == null -> false
    route == NavRoutes.home.name -> true
    route == NavRoutes.statistics.name -> true
    route.startsWith("${NavRoutes.artist.name}/") -> true
    route.startsWith("${NavRoutes.settings.name}?") -> true
    route.startsWith("${NavRoutes.search.name}?") -> true
    route.startsWith(NavRoutes.searchResults.name) -> true
    else -> false
}
