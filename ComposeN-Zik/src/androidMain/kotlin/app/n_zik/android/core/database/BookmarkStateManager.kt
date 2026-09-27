package app.n_zik.android.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * Centralized reactive manager for artist/album/playlist bookmark states.
 *
 * Instead of each Item composable querying the database individually (N queries for N items),
 * this manager provides batch loading: query once for a list of IDs.
 *
 * Usage in Composable:
 *   val bookmarkStatesMap by remember(ids) {
 *       BookmarkStateManager.getArtistBookmarkStates(ids)
 *   }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
 *   val bookmarkState = bookmarkStatesMap[id]  // true=bookmarked, false=disliked, null=neutral
 */
object BookmarkStateManager {

    /**
     * Maximum number of IDs per SQL `IN` clause. SQLite versions below
     * 3.32 (most legacy devices, e.g. Android 10) cap
     * SQLITE_MAX_VARIABLE_NUMBER at 999; 500 keeps a safe margin below it.
     *
     * Trade-off: each invalidation of the tracked table re-runs one query per
     * chunk (ceil(N/500) queries for N ids, all small and index-friendly),
     * instead of a single oversized query.
     */
    private const val CHUNK_SIZE = 500

    private fun <T> chunkedFlows(
        ids: List<String>,
        query: (chunk: List<String>) -> Flow<List<T>>
    ): List<Flow<List<T>>> = ids.chunked(CHUNK_SIZE).map { chunk -> query(chunk) }

    fun getArtistBookmarkStates(artistIds: List<String>): Flow<Map<String, Boolean?>> {
        if (artistIds.isEmpty()) return flowOf(emptyMap())
        return combine(chunkedFlows(artistIds) { Database.artistTable.getBookmarkStatesForArtists(it) }) { chunks ->
            chunks.flatMap { it }.associate { it.artistId to it.bookmarkState }
        }
            .distinctUntilChanged()
    }

    fun getAlbumBookmarkStates(albumIds: List<String>): Flow<Map<String, Boolean?>> {
        if (albumIds.isEmpty()) return flowOf(emptyMap())
        return combine(chunkedFlows(albumIds) { Database.albumTable.getBookmarkStatesForAlbums(it) }) { chunks ->
            chunks.flatMap { it }.associate { it.albumId to it.bookmarkState }
        }
            .distinctUntilChanged()
    }

    fun getPlaylistBookmarkStates(browseIds: List<String>): Flow<Map<String, Boolean>> {
        if (browseIds.isEmpty()) return flowOf(emptyMap())
        return combine(chunkedFlows(browseIds) { Database.playlistTable.getBookmarkStatesForPlaylists(it) }) { chunks ->
            chunks.flatMap { it }.associate { it.browseId to it.isYoutubePlaylist }
        }
            .distinctUntilChanged()
    }
}
