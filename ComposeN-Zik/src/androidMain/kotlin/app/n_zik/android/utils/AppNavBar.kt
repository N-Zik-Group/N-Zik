package app.n_zik.android.utils

import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.core.ondevice.isDeviceArtistId

/**
 * Whether [route] renders an app navigation bar whose space the mini-player must reserve.
 * Keep this aligned with each screen's `navBarContent`; Settings has no app navigation bar.
 *
 * @param route Destination route pattern, or null before the first destination.
 * @param destinationId Identifier from the destination arguments, when available.
 * @param activeHomeTabCount Home draws a bar only with at least two active tabs.
 */
fun appNavBarPresentForRoute(
    route: String?,
    activeHomeTabCount: Int,
    destinationId: String? = null,
): Boolean = when {
    route == null -> false
    route == NavRoutes.home.name -> activeHomeTabCount >= 2
    route == NavRoutes.statistics.name -> true
    route.startsWith("${NavRoutes.artist.name}/") ->
        !isDeviceArtistId(destinationId ?: route.substringAfter('/'))
    route.startsWith("${NavRoutes.search.name}?") -> true
    route.startsWith(NavRoutes.searchResults.name) -> true
    else -> false
}
