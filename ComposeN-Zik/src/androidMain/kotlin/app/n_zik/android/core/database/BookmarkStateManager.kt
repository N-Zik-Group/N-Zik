package app.n_zik.android.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Centralized reactive manager for artist/album/playlist bookmark states.
 *
 * Instead of each Item composable querying the database individually (N queries for N items),
 * this manager provides batch loading: query once for a list of IDs.
 *
 * Usage in Composable:
 *   val bookmarkStatesMap by remember(ids) {
 *       BookmarkStateManager.getArtistBookmarkStates(ids)
 *   }.collectAsState(emptyMap(), Dispatchers.IO)
 *   val bookmarkState = bookmarkStatesMap[id]  // true=bookmarked, false=disliked, null=neutral
 */
object BookmarkStateManager {

    fun getArtistBookmarkStates(artistIds: List<String>): Flow<Map<String, Boolean?>> {
        if (artistIds.isEmpty()) return flowOf(emptyMap())
        return Database.artistTable
            .getBookmarkStatesForArtists(artistIds)
            .distinctUntilChanged()
            .map { list -> list.associate { it.artistId to it.bookmarkState } }
    }

    fun getAlbumBookmarkStates(albumIds: List<String>): Flow<Map<String, Boolean?>> {
        if (albumIds.isEmpty()) return flowOf(emptyMap())
        return Database.albumTable
            .getBookmarkStatesForAlbums(albumIds)
            .distinctUntilChanged()
            .map { list -> list.associate { it.albumId to it.bookmarkState } }
    }

    fun getPlaylistBookmarkStates(browseIds: List<String>): Flow<Map<String, Boolean>> {
        if (browseIds.isEmpty()) return flowOf(emptyMap())
        return Database.playlistTable
            .getBookmarkStatesForPlaylists(browseIds)
            .distinctUntilChanged()
            .map { list -> list.associate { it.browseId to it.isYoutubePlaylist } }
    }
}
