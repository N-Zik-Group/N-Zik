package app.n_zik.android.core.matching

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.canEditStoredPlaylistMembership
import app.n_zik.android.models.Song
import app.n_zik.android.utils.coroutines.NzikDispatchers

private const val RETRY_QUERY_BATCH_SIZE = 500

internal suspend fun loadSongMatchFailures(
    state: SongMatchState,
    isCurrent: suspend () -> Boolean = { SongMatch.results.value[state.scopeKey]?.requestId == state.requestId },
): List<Song> =
    withContext(NzikDispatchers.DATA) {
        if (!isCurrent()) return@withContext emptyList()
        try {
            val playlistId = state.playlistId
            if (playlistId != null && !canEditStoredPlaylistMembership(playlistId)) {
                return@withContext emptyList()
            }
            val songsById = state.failedIds.chunked(RETRY_QUERY_BATCH_SIZE)
                .flatMap { ids ->
                    if (playlistId == null) Database.songTable.findByIdsDirect(ids)
                    else Database.songPlaylistMapTable.findSongsByIds(playlistId, ids)
                }
                .associateBy(Song::id)
            if (!isCurrent()) return@withContext emptyList()
            state.failedIds.mapNotNull(songsById::get)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.tag("SongMatchFailures").e(error, "Could not load song matching failures")
            emptyList()
        }
    }
