package app.n_zik.android.components.navigation

/** Only migrated scroll owners include the header reserve inside their content. */
internal fun pageOwnsChromeInsets(route: String?): Boolean =
    route?.substringBefore('/')?.substringBefore('?') in scrollInsetRoutes

private val scrollInsetRoutes = setOf(
    "home", "settings", "bridgeServer", "listenTogether", "profiles",
    "artistInsights", "albumInsights",
    "album", "artist", "playlist", "localPlaylist", "podcast", "mood", "moodsPage",
    "newAlbums", "artistAlbums", "artistPlaylists", "artistVideos",
    "search", "searchResults", "history", "statistics", "rewindHome", "updater",
)

internal fun pageSupportsChromeScroll(route: String?): Boolean {
    val page = route?.substringBefore('/')?.substringBefore('?') ?: return false
    return page in scrollInsetRoutes
}
