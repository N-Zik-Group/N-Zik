package app.n_zik.android.core.database

import app.n_zik.android.core.database.Database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * Data class for like state query results from Room.
 */
data class SongLikeState(
    val songId: String,
    val likeState: Boolean?
)

/**
 * Centralized reactive manager for song like states.
 *
 * Instead of each SongItem querying the database individually (N queries for N items),
 * this manager provides batch loading: query once for a list of song IDs.
 *
 * Usage in Composable:
 *   val likeStatesMap by remember(songIds) {
 *       LikeStateManager.getLikeStates(songIds)
 *   }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
 *   val likeState = likeStatesMap[songId]  // true=liked, false=disliked, null=neutral
 */
object LikeStateManager {

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
     * Get like states for a list of song IDs as a Flow.
     * Returns Map<songId, likeState?> where:
     * - true = liked
     * - false = disliked
     * - null = neutral
     *
     * The requested IDs are split into chunks of [CHUNK_SIZE] so no single
     * query exceeds the SQLite bind-parameter limit, then composed back into
     * a single reactive Flow. This is MUCH more efficient than querying each
     * song individually.
     */
    fun getLikeStates(songIds: List<String>): Flow<Map<String, Boolean?>> {
        if (songIds.isEmpty()) {
            return flowOf(emptyMap())
        }

        val chunkFlows = songIds.chunked(CHUNK_SIZE).map { chunk ->
            Database.songTable.getLikeStatesForSongs(chunk)
        }

        return combine(chunkFlows) { chunks ->
            chunks.flatMap { it }.associate { it.songId to it.likeState }
        }
            .distinctUntilChanged()
    }
}
