package app.n_zik.android.components.navigation

import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.NavRoutes

/** Configured side navigation always owns the header, including routes without tabs. */
internal fun sideNavigationOwnsHeader(
    position: NavigationBarPosition,
): Boolean = position == NavigationBarPosition.Left || position == NavigationBarPosition.Right

/** A page's own tab rail already contains the header controls. */
internal fun needsStandaloneSideHeader(
    hasNavBar: Boolean,
    position: NavigationBarPosition,
): Boolean = sideNavigationOwnsHeader(position) && !hasNavBar

/** Skeleton places its clearance around content, leaving its side overlay at screen coordinates. */
internal fun sidePageOwnsTopInset(route: String?): Boolean = route?.substringBefore('/')?.substringBefore('?') in setOf(
    NavRoutes.home.name, NavRoutes.settings.name, NavRoutes.artist.name, NavRoutes.statistics.name,
    NavRoutes.search.name, NavRoutes.searchResults.name, NavRoutes.playlist.name, NavRoutes.podcast.name,
    NavRoutes.history.name, NavRoutes.localPlaylist.name, NavRoutes.mood.name, NavRoutes.moodsPage.name,
    NavRoutes.newAlbums.name, NavRoutes.bridgeServer.name, NavRoutes.listenTogether.name, NavRoutes.profiles.name,
)

/** Destination transitions keep old pages composed, but only the target owns navigation UI. */
internal fun pageOwnsNavigationOverlay(pageKey: String?, currentPageKey: String?): Boolean =
    pageKey != null && pageKey == currentPageKey
