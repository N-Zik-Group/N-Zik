package app.n_zik.android.core.database

import app.n_zik.android.core.database.Database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
 *   }.collectAsState(emptyMap(), Dispatchers.IO)
 *   val likeState = likeStatesMap[songId]  // true=liked, false=disliked, null=neutral
 */
object LikeStateManager {

    /**
     * Get like states for a list of song IDs as a Flow.
     * Returns Map<songId, likeState?> where:
     * - true = liked
     * - false = disliked
     * - null = neutral
     *
     * This is MUCH more efficient than querying each song individually.
     * One SQL query for N songs instead of N SQL queries.
     */
    fun getLikeStates(songIds: List<String>): Flow<Map<String, Boolean?>> {
        if (songIds.isEmpty()) {
            return kotlinx.coroutines.flow.flowOf(emptyMap())
        }

        return Database.songTable
            .getLikeStatesForSongs(songIds)
            .distinctUntilChanged()
            .map { list ->
                list.associate { it.songId to it.likeState }
            }
    }
}
