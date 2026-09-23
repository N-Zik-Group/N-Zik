package app.n_zik.android.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Centralized reactive manager for song playlist membership states.
 *
 * Instead of each SongItem querying the database individually (N queries for N items),
 * this manager provides batch loading: query once for a list of song IDs.
 *
 * Usage in Composable:
 *   val playlistStatesMap by remember(songIds) {
 *       PlaylistStateManager.getPlaylistStates(songIds)
 *   }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
 *   val inPlaylist = playlistStatesMap[songId]  // true=mapped to a playlist, false=not mapped
 */
object PlaylistStateManager {

    /**
     * Get playlist membership for a list of song IDs as a Flow,
     * using the production [SongPlaylistMapTable].
     */
    fun getPlaylistStates( songIds: List<String> ): Flow<Map<String, Boolean>> =
        getPlaylistStates( songIds, Database.songPlaylistMapTable )

    /**
     * Get playlist membership for a list of song IDs as a Flow.
     * Returns Map<songId, inPlaylist> in which EVERY requested ID is present:
     * - true  = the song belongs to at least one playlist
     * - false = the song is not mapped to any playlist
     *
     * This is MUCH more efficient than querying each song individually.
     * One SQL query for N songs instead of N SQL queries.
     *
     * @param songIds song IDs to check
     * @param dao [SongPlaylistMapTable] to query (defaults to the production database
     * via [getPlaylistStates]; overridable for tests)
     */
    fun getPlaylistStates( songIds: List<String>, dao: SongPlaylistMapTable ): Flow<Map<String, Boolean>> {
        if (songIds.isEmpty()) {
            return flowOf(emptyMap())
        }

        return dao
            .songsInPlaylists(songIds)
            .distinctUntilChanged()
            .map { mappedIds ->
                // Explicit falses: the map always covers every requested ID,
                // same contract as likeStatesMap[song.id]
                val mappedSet = mappedIds.toHashSet()
                songIds.associateWith { it in mappedSet }
            }
    }
}
