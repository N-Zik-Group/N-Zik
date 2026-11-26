package app.n_zik.android.intents

import java.util.UUID

import android.net.Uri
import app.n_zik.android.components.navigation.onlinePlaylistBrowseIdFromLink
import app.n_zik.android.components.navigation.onlinePlaylistRoute
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.listentogether.parseListenTogetherInvite

internal sealed interface LibraryLinkDestination {
    data class Theme(val route: String?) : LibraryLinkDestination
    data class Invite(val roomCode: String) : LibraryLinkDestination
    data class Route(val route: String) : LibraryLinkDestination
    data class RemoteAlbum(val playlistBrowseId: String) : LibraryLinkDestination
    data class Video(val id: String) : LibraryLinkDestination
    data object Favorites : LibraryLinkDestination
    data object Unsupported : LibraryLinkDestination
}

internal fun libraryLinkDestination(uri: Uri): LibraryLinkDestination {
    if (uri.scheme == "nzik" && uri.host == "theme") {
        val segments = uri.pathSegments
        val id = segments.firstOrNull()
        val revision = segments.getOrNull(1)?.toIntOrNull()
        val route = if (segments.size == 2 && id != null && revision != null && revision > 0 &&
            runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
        ) {
            "${NavRoutes.themeStoreDetail.name}/$id/$revision"
        } else {
            null
        }
        return LibraryLinkDestination.Theme(route)
    }

    parseListenTogetherInvite(uri)?.let { return LibraryLinkDestination.Invite(it) }

    return when (val path = uri.pathSegments.firstOrNull()) {
        "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
            val browseId = onlinePlaylistBrowseIdFromLink(playlistId)
            if (playlistId.startsWith("OLAK5uy_")) LibraryLinkDestination.RemoteAlbum(browseId)
            else LibraryLinkDestination.Route(onlinePlaylistRoute(browseId))
        }
        "channel", "c" -> uri.lastPathSegment?.let { LibraryLinkDestination.Route("${NavRoutes.artist.name}/$it") }
        "search" -> uri.getQueryParameter("q")?.let { LibraryLinkDestination.Route("${NavRoutes.searchResults.name}/$it") }
        "localPlaylist" -> uri.lastPathSegment?.let { LibraryLinkDestination.Route("${NavRoutes.localPlaylist.name}/$it") }
        "album" -> uri.lastPathSegment?.let { LibraryLinkDestination.Route("${NavRoutes.album.name}/$it") }
        "playFavorites" -> LibraryLinkDestination.Favorites
        else -> when {
            path == "watch" -> uri.getQueryParameter("v")
            uri.host == "youtu.be" -> path
            else -> null
        }?.let { LibraryLinkDestination.Video(it) }
    } ?: LibraryLinkDestination.Unsupported
}
