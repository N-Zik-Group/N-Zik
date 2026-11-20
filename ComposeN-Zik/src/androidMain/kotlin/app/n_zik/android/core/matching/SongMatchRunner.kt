package app.n_zik.android.core.matching

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.utils.SongMatchOutcome
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getAlbumVersionFromVideo
import app.n_zik.android.utils.getAlbumVersionFromVideoGlobal

private const val TAG = "SongMatchRunner"
private const val REQUEST_STAGGER_MS = 800L
private const val GLOBAL_RECONCILIATION_DELAY_MS = 1_500L
private const val PLAYLIST_RECONCILIATION_DELAY_MS = 500L

internal suspend fun runSongMatch(context: Context, run: SongMatchRun): SongMatchState {
    val request = run.request
    val outcomes = ConcurrentHashMap<String, SongMatchOutcome>()
    val merged = AtomicInteger()
    val canUseLibrary = { run.canUseLibrary(context) }
    val canMatch = { canUseLibrary() && !OfflineMode.enabled(context) }
    val cancelled = coroutineScope {
        val work = launch(NzikDispatchers.DATA, start = CoroutineStart.LAZY) {
            request.songs.forEach { song ->
                ensureActive()
                if (!canMatch()) {
                    throw CancellationException("Matching is no longer available")
                }
                launch matchSong@{
                    if (!canMatch()) return@matchSong
                    val outcome = try {
                        val playlistId = request.playlistId
                        if (playlistId == null) {
                            getAlbumVersionFromVideoGlobal(
                                song = song,
                                profileId = run.owner.id,
                                canUseLibrary = canMatch,
                            )
                        } else {
                            getAlbumVersionFromVideo(
                                song = song,
                                playlistId = playlistId,
                                profileId = run.owner.id,
                                canUseLibrary = canMatch,
                            )
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Timber.tag(TAG).e(error, "Could not match a song")
                        SongMatchOutcome.Unmatched(song.id)
                    }
                    outcomes[song.id] = outcome
                    if (outcome == SongMatchOutcome.Merged) merged.incrementAndGet()
                    SongMatch.report(run, outcomes.size, merged.get())
                }
                delay(REQUEST_STAGGER_MS)
            }
        }
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
            SongMatch.state.first {
                it?.requestId != run.id || it.status == SongMatchStatus.Cancelling
            }
            work.cancel()
        }
        val offline = launch {
            OfflineMode.enabledFlow(context).flowOn(NzikDispatchers.DATA).first { it }
            SongMatch.cancel(run.id)
        }
        work.join()
        cancellation.cancel()
        offline.cancel()
        work.isCancelled
    }

    val result = songMatchResult(run, outcomes, cancelled)
    return reconcileSongMatch(result) {
        withContext(NzikDispatchers.DATA) {
            delay(if (request.playlistId == null) GLOBAL_RECONCILIATION_DELAY_MS else PLAYLIST_RECONCILIATION_DELAY_MS)
            if (canUseLibrary()) Database.importSongTable.reconcileMatches(request.playlistId)
        }
    }
}

internal suspend fun reconcileSongMatch(
    result: SongMatchState,
    reconcile: suspend () -> Unit,
): SongMatchState = try {
    reconcile()
    result
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Timber.tag(TAG).e(error, "Could not reconcile song matching results")
    result.copy(status = SongMatchStatus.Failed)
}

internal fun songMatchResult(
    run: SongMatchRun,
    outcomes: Map<String, SongMatchOutcome>,
    cancelled: Boolean,
): SongMatchState {
    val retryIds = run.request.songs.mapNotNullTo(mutableSetOf()) { song ->
        when (val outcome = outcomes[song.id]) {
            SongMatchOutcome.Matched, SongMatchOutcome.Merged -> null
            is SongMatchOutcome.Unmatched -> outcome.songId
            SongMatchOutcome.Skipped, null -> song.id
        }
    }
    val merged = outcomes.values.count { it == SongMatchOutcome.Merged }
    val matched = merged + outcomes.values.count { it == SongMatchOutcome.Matched }
    val failed = outcomes.values.count { it is SongMatchOutcome.Unmatched }
    return run.initialState.copy(
        status = if (cancelled) SongMatchStatus.Cancelled else SongMatchStatus.Finished,
        processed = matched + failed,
        matched = matched,
        failed = failed,
        merged = merged,
        unprocessed = run.initialState.total - matched - failed,
        failedIds = retryIds,
    )
}
