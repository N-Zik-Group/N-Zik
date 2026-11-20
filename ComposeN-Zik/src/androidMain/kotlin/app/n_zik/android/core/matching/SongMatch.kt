package app.n_zik.android.core.matching

import java.util.UUID
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import app.n_zik.android.appContext
import app.n_zik.android.core.backup.isLibraryReady
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.models.Song
import app.n_zik.android.utils.activeProfileKey
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.profilePreferences

private const val TAG = "SongMatch"
private const val HANDOFF_TIMEOUT_MS = 10_000L

internal data class SongMatchRequest(
    val scopeKey: String,
    val songs: List<Song>,
    val playlistId: Long? = null,
    val automatic: Boolean = false,
)

internal enum class SongMatchStatus {
    Running, Cancelling, Finished, Cancelled, Failed,
}

internal data class SongMatchState(
    val requestId: String,
    val scopeKey: String,
    val status: SongMatchStatus,
    val total: Int,
    val processed: Int = 0,
    val matched: Int = 0,
    val failed: Int = 0,
    val merged: Int = 0,
    val failedIds: Set<String> = emptySet(),
    val playlistId: Long? = null,
    val unprocessed: Int = 0,
)

internal data class SongMatchRun(
    val id: String,
    val owner: ProfileStorageIdentity,
    val request: SongMatchRequest,
) {
    val initialState = SongMatchState(
        id, request.scopeKey, SongMatchStatus.Running, request.songs.size, playlistId = request.playlistId,
    )

    fun canUseLibrary(context: Context): Boolean =
        context.isLibraryReady() && ProfileWorkOwner.isBound(context, owner)

    fun failureState(processed: Int = 0): SongMatchState =
        initialState.copy(
            status = SongMatchStatus.Failed,
            processed = processed,
            unprocessed = initialState.total - processed,
            failedIds = if (processed == 0) request.songs.mapTo(linkedSetOf(), Song::id) else emptySet(),
        )
}

/** Holds the process-local handoff until the foreground service claims the request. */
internal object SongMatch {
    private val sessions = SongMatchSessions { owner ->
        try {
            ProfileWorkOwner.isBound(appContext(), owner)
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Could not verify matching profile")
            false
        }
    }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val runnerScope by lazy { NzikDispatchers.fireAndForget(NzikDispatchers.UI) }
    private var handoffTimeout: Runnable? = null
    private var observingProfile = false
    private val profileListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == activeProfileKey) sessions.invalidateUnboundState()
    }

    val state: StateFlow<SongMatchState?> = sessions.state
    val results: StateFlow<Map<String, SongMatchState>> = sessions.results

    @MainThread
    fun start(context: Context, request: SongMatchRequest): Boolean {
        if (sessions.hasActive || request.songs.isEmpty()) return false
        return try {
            val application = context.applicationContext
            if (!application.isLibraryReady() || OfflineMode.enabled(application)) return false
            val owner = ProfileWorkOwner.capture(application) ?: return false
            val candidate = SongMatchRun(
                id = UUID.randomUUID().toString(),
                owner = owner,
                request = request.copy(songs = request.songs.distinctBy(Song::id)),
            )
            if (!observingProfile) {
                application.profilePreferences.registerOnSharedPreferenceChangeListener(profileListener)
                observingProfile = true
            }
            val handler = mainHandler
            val run = sessions.reserve(candidate) ?: return false
            try {
                val intent = Intent(application, SongMatchService::class.java)
                    .setAction(SongMatchService.ACTION_START)
                    .putExtra(SongMatchService.EXTRA_REQUEST_ID, run.id)
                ContextCompat.startForegroundService(application, intent)
            } catch (error: RuntimeException) {
                sessions.abandon(run)
                Timber.tag(TAG).w(error, "Could not start foreground matching")
                return false
            }

            sessions.accept(run)
            val timeout = Runnable {
                handoffTimeout = null
                Timber.tag(TAG).w("Foreground matching service did not claim the request")
                sessions.rejectPromotion(run)
            }
            handoffTimeout = timeout
            handler.postDelayed(timeout, HANDOFF_TIMEOUT_MS)
            true
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Could not prepare foreground matching")
            false
        }
    }

    @MainThread
    fun cancel(requestId: String) {
        sessions.cancel(requestId)
    }

    @MainThread
    fun dismiss(requestId: String) {
        sessions.dismiss(requestId)
    }

    @MainThread
    fun claim(requestId: String?): SongMatchRun? {
        val run = sessions.claim(requestId) ?: return null
        handoffTimeout?.let(mainHandler::removeCallbacks)
        handoffTimeout = null
        return run
    }

    @MainThread
    fun hasPendingStart(): Boolean = sessions.hasPending

    @MainThread
    fun interruption(requestId: String, failed: Boolean) {
        sessions.cancel(requestId, failed)
    }

    @MainThread
    fun failToPromote(run: SongMatchRun) {
        sessions.rejectPromotion(run)
    }

    @MainThread
    fun promoted(run: SongMatchRun) {
        sessions.promoted(run)
    }

    @MainThread
    fun retryResult(context: Context, requestId: String, owner: ProfileStorageIdentity): SongMatchState? {
        if (!context.isLibraryReady() || OfflineMode.enabled(context)) return null
        return sessions.retryResult(requestId, owner)
    }

    @MainThread
    fun reserveRetry(
        context: Context,
        result: SongMatchState,
        owner: ProfileStorageIdentity,
        songs: List<Song>,
    ): SongMatchRun? {
        val current = retryResult(context, result.requestId, owner) ?: return null
        return sessions.reserveRetry(current, owner, songs)
    }

    @MainThread
    fun execute(context: Context, run: SongMatchRun, onFinished: (SongMatchState?) -> Unit) {
        runnerScope.launch {
            var result = run.failureState()
            try {
                result = runSongMatch(context, run)
            } catch (error: Exception) {
                Timber.tag(TAG).e(error, "Matching could not finish")
                result = run.failureState(state.value?.takeIf { it.requestId == run.id }?.processed ?: 0)
            } finally {
                sessions.finish(run, result, onFinished)
            }
        }
    }

    fun report(run: SongMatchRun, processed: Int, merged: Int) {
        sessions.report(run.id, processed, merged)
    }
}

internal class SongMatchSessions(private val owns: (ProfileStorageIdentity) -> Boolean) {
    private val mutableState = MutableStateFlow<SongMatchState?>(null)
    val state = mutableState.asStateFlow()
    private val mutableResults = MutableStateFlow<Map<String, SongMatchState>>(emptyMap())
    val results = mutableResults.asStateFlow()
    private val attempts = mutableMapOf<Pair<ProfileStorageIdentity, String>, MutableSet<String>>()
    private var active: SongMatchRun? = null
    private var stateOwner: ProfileStorageIdentity? = null
    private var notificationResult: SongMatchState? = null
    private var claimed = false
    private var interrupted = false

    val hasActive: Boolean get() = active != null
    val hasPending: Boolean get() = active != null && !claimed

    fun reserve(candidate: SongMatchRun): SongMatchRun? {
        if (hasActive || !owns(candidate.owner)) return null
        val request = candidate.request
        val attempted = attempts[candidate.owner to request.scopeKey]
        val run = if (request.automatic && attempted != null) {
            val songs = request.songs.filterNot { it.id in attempted }
            if (songs.isEmpty()) return null
            candidate.copy(request = request.copy(songs = songs))
        } else {
            candidate
        }
        active = run
        return run
    }

    fun abandon(run: SongMatchRun) {
        if (active !== run) return
        // A rejected foreground start can be retried manually without a repeated automatic launch.
        if (run.request.automatic) rememberAttempt(run)
        release()
    }

    fun accept(run: SongMatchRun) {
        rememberAttempt(run)
        if (stateOwner != run.owner) {
            notificationResult = null
            mutableResults.value = emptyMap()
        }
        stateOwner = run.owner
        mutableState.update { run.initialState }
    }

    fun promoted(run: SongMatchRun) {
        if (active !== run) return
        if (state.value?.requestId != run.id && owns(run.owner)) accept(run)
        notificationResult = null
        mutableResults.update { it - run.request.scopeKey }
    }

    fun retryResult(requestId: String, owner: ProfileStorageIdentity): SongMatchState? {
        if (hasActive || stateOwner != owner || !owns(owner)) return null
        return notificationResult?.takeIf { it.requestId == requestId && it.failedIds.isNotEmpty() }
    }

    fun reserveRetry(result: SongMatchState, owner: ProfileStorageIdentity, songs: List<Song>): SongMatchRun? {
        val current = retryResult(result.requestId, owner) ?: return null
        val eligible = songs.filter { it.id in current.failedIds }.distinctBy(Song::id)
        if (eligible.isEmpty()) return null
        val run = reserve(
            SongMatchRun(UUID.randomUUID().toString(), owner, SongMatchRequest(current.scopeKey, eligible, current.playlistId)),
        ) ?: return null
        return claim(run.id)
    }

    fun rejectPromotion(run: SongMatchRun) {
        if (active !== run) return
        // The notification retry has not published a replacement state at this point.
        if (state.value?.requestId != run.id) {
            abandon(run)
            return
        }
        val previous = results.value[run.request.scopeKey]
        if (previous == null) {
            finish(run, run.failureState())
        } else {
            release()
            mutableState.update { previous }
        }
    }

    private fun rememberAttempt(run: SongMatchRun) {
        val attempted = attempts.getOrPut(run.owner to run.request.scopeKey) { mutableSetOf() }
        run.request.songs.mapTo(attempted, Song::id)
    }

    fun claim(requestId: String?): SongMatchRun? {
        val run = active ?: return null
        if (claimed || run.id != requestId) return null
        claimed = true
        return run
    }

    fun cancel(requestId: String, failed: Boolean = false) {
        val run = active ?: return
        if (run.id != requestId || !owns(run.owner)) return
        interrupted = interrupted || failed
        mutableState.update {
            it?.takeIf { state -> state.requestId == requestId }?.copy(status = SongMatchStatus.Cancelling) ?: it
        }
    }

    fun dismiss(requestId: String) {
        if (stateOwner?.let(owns) != true) return
        val result = results.value.values.firstOrNull { it.requestId == requestId } ?: return
        mutableResults.update { it - result.scopeKey }
        mutableState.update { it?.takeUnless { current -> current.requestId == requestId } }
    }

    fun invalidateUnboundState() {
        if (stateOwner?.let(owns) != false) return
        stateOwner = null
        notificationResult = null
        mutableResults.value = emptyMap()
        mutableState.value = null
    }

    fun report(requestId: String, processed: Int, merged: Int) {
        mutableState.update {
            it?.takeIf { state -> state.requestId == requestId }?.copy(
                processed = maxOf(it.processed, processed),
                merged = maxOf(it.merged, merged),
            ) ?: it
        }
    }

    fun finish(run: SongMatchRun, result: SongMatchState, onFinished: ((SongMatchState?) -> Unit)? = null) {
        if (active !== run) return
        val status = when {
            interrupted -> SongMatchStatus.Failed
            result.status == SongMatchStatus.Finished && state.value?.status == SongMatchStatus.Cancelling ->
                SongMatchStatus.Cancelled
            else -> result.status
        }
        if (stateOwner != run.owner || !owns(run.owner)) {
            try {
                onFinished?.invoke(null)
            } finally {
                release()
                invalidateUnboundState()
            }
            return
        }
        val current = state.value
        val completed = result.copy(
            status = status,
            processed = maxOf(result.processed, current?.processed ?: 0),
            merged = maxOf(result.merged, current?.merged ?: 0),
        )
        try {
            if (onFinished != null) {
                notificationResult = completed
                onFinished(completed)
            }
        } finally {
            release()
            mutableResults.update { it + (completed.scopeKey to completed) }
            mutableState.value = completed
        }
    }

    private fun release() {
        active = null
        claimed = false
        interrupted = false
    }
}
