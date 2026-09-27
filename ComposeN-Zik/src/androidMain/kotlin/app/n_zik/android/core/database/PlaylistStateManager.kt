package app.n_zik.android.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

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
     * Maximum number of song IDs per SQL `IN` clause. SQLite versions below
     * 3.32 (most legacy devices, e.g. Android 10) cap
     * SQLITE_MAX_VARIABLE_NUMBER at 999; 500 keeps a safe margin below it.
     *
     * Trade-off: each invalidation of the tracked table re-runs one query per
     * chunk (ceil(N/500) queries for N ids, all small and index-friendly),
     * instead of a single oversized query.
     */
    private const val CHUNK_SIZE = 500

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
     * One SQL query per chunk of [CHUNK_SIZE] songs instead of N SQL queries,
     * keeping every query below the SQLite bind-parameter limit even for very
     * large song lists.
     *
     * @param songIds song IDs to check
     * @param dao [SongPlaylistMapTable] to query (defaults to the production database
     * via [getPlaylistStates]; overridable for tests)
     */
    fun getPlaylistStates( songIds: List<String>, dao: SongPlaylistMapTable ): Flow<Map<String, Boolean>> {
        if (songIds.isEmpty()) {
            return flowOf(emptyMap())
        }

        val chunkFlows = songIds.chunked(CHUNK_SIZE).map { chunk ->
            dao.songsInPlaylists(chunk)
        }

        return combine(chunkFlows) { chunks ->
            // Explicit falses: the map always covers every requested ID,
            // same contract as likeStatesMap[song.id] — recomputed on the
            // full list after the chunk results are merged
            val mappedSet = chunks.flatMap { it }.toHashSet()
            songIds.associateWith { it in mappedSet }
        }
            .distinctUntilChanged()
    }
}
