package app.n_zik.android.components.navigation

import android.net.Uri
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.core.database.isPodcastBrowseId
import app.n_zik.android.enums.NavRoutes

internal fun onlinePlaylistDestination(browseId: String): NavRoutes =
    if (isPodcastBrowseId(browseId.removePrefix(MODIFIED_PREFIX))) NavRoutes.podcast else NavRoutes.playlist

internal fun onlinePlaylistRoute(browseId: String): String =
    "${onlinePlaylistDestination(browseId).name}/${Uri.encode(browseId)}"

internal fun onlinePlaylistBrowseIdFromLink(playlistId: String): String =
    if (isPodcastBrowseId(playlistId) || playlistId.startsWith("VL")) playlistId else "VL$playlistId"

internal const val COLLECTION_SOURCE_ONLY_ARGUMENT = "sourceOnly"

internal fun collectionMenuOnlineRoute(browseId: String): String =
    "${onlinePlaylistRoute(browseId)}?$COLLECTION_SOURCE_ONLY_ARGUMENT=true"
