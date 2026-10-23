package app.n_zik.android.extensions.audiobar

import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import utils.VisualizerHelper
import utils.VisualizerSnapshot

/**
 * Owns the single capture loop per active `audioSessionId` (issue #606, ARCHITECTURE-SPINE AD-2).
 *
 * `nextvisualizer` cannot depend on coroutines (module dependency direction, AD-5), so this
 * object lives in `app.n_zik.android` and is wired into that module through
 * `VisualizerHelper.snapshotProvider` (see [NextVisualizer]). Every `Visualizer.getFft()`/
 * `getWaveForm()` call for a given sessionId now happens exactly once per frame, from exactly
 * one place -- [VisualizerHelper.captureNow] -- running on [NzikDispatchers.VISUALIZER].
 *
 * Process-lifetime singleton, same rationale as `NzikDispatchers`: the backing scope is never
 * explicitly cancelled from an Activity/Service/ViewModel lifecycle. Per-session loops start
 * and stop on their own via [SharingStarted.WhileSubscribed] as consumers come and go.
 */
object VisualizerCaptureCoordinator {

    /** Capture cadence. 60fps to match `VisualizerView`'s draw cadence (AD-2 design notes). */
    internal var captureIntervalMillis: Long = 16L

    /** How long a session's loop keeps running with zero collectors before it stops. */
    internal var subscriptionTimeoutMillis: Long = 2000L

    private val scope: CoroutineScope by lazy {
        NzikDispatchers.fireAndForget(NzikDispatchers.VISUALIZER)
    }

    private val snapshotFlows = ConcurrentHashMap<Int, StateFlow<VisualizerSnapshot?>>()

    /**
     * A [StateFlow] of the latest [VisualizerSnapshot] for [sessionId], backed by a single
     * capture loop shared by every collector of this exact sessionId — collecting it from two
     * places (e.g. `SeekBarVisualizer` and `VisualizerView`, through
     * [VisualizerHelper.snapshotProvider]) never doubles the underlying native capture.
     *
     * The loop starts lazily on the first collector and stops [subscriptionTimeoutMillis] after
     * the last one goes away ([SharingStarted.WhileSubscribed]) — no collector means no polling.
     *
     * Note: when the only "collector" is [currentSnapshot]'s poll-keep-alive proxy, that proxy
     * itself waits up to [subscriptionTimeoutMillis] of poll inactivity before it lets go, so
     * the worst-case stop delay is roughly *twice* the documented value, not exactly it.
     */
    fun snapshotFlow(sessionId: Int): StateFlow<VisualizerSnapshot?> =
        snapshotFlows.computeIfAbsent(sessionId) {
            lateinit var selfFlow: StateFlow<VisualizerSnapshot?>
            selfFlow = flow {
                while (currentCoroutineContext().isActive) {
                    try {
                        emit(VisualizerHelper.captureNow(sessionId))
                    } catch (e: Exception) {
                        Timber.tag("VisualizerCaptureCoordinator").e(
                            e,
                            "captureNow() failed for sessionId=$sessionId; continuing capture loop",
                        )
                    }
                    delay(captureIntervalMillis)
                }
            }.onCompletion {
                snapshotFlows.remove(sessionId, selfFlow)
                lastPolledAtMs.remove(sessionId)
            }.stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(subscriptionTimeoutMillis),
                initialValue = null,
            )
            selfFlow
        }

    private val lastPolledAtMs = ConcurrentHashMap<Int, AtomicLong>()
    private val pollKeepAliveJobs = ConcurrentHashMap<Int, Job>()

    /**
     * Synchronous read of the latest published snapshot for [sessionId] — the reference wired
     * to [VisualizerHelper.snapshotProvider] (its `(Int) -> VisualizerSnapshot?` shape means
     * `nextvisualizer` never needs to know a `StateFlow` is involved on the other side of the
     * seam).
     *
     * Reading [StateFlow.value] alone is not a real collector ([SharingStarted.WhileSubscribed]
     * would never start the loop for a caller that only peeks `.value` — e.g. `VisualizerView`,
     * since `View.onDraw()` cannot suspend to `collect{}`), so every call records a poll
     * timestamp and lazily starts a proxy collector keeping the flow's real subscriber count
     * above zero while polling continues, cancelling itself [subscriptionTimeoutMillis] after
     * the last poll.
     */
    fun currentSnapshot(sessionId: Int): VisualizerSnapshot? {
        lastPolledAtMs.computeIfAbsent(sessionId) { AtomicLong(0L) }.set(System.currentTimeMillis())
        ensurePollKeepAlive(sessionId)
        return snapshotFlow(sessionId).value
    }

    private fun ensurePollKeepAlive(sessionId: Int) {
        pollKeepAliveJobs.compute(sessionId) { _, existing ->
            if (existing != null && existing.isActive) return@compute existing
            scope.launch {
                snapshotFlow(sessionId).collect {
                    val idleFor = System.currentTimeMillis() - (lastPolledAtMs[sessionId]?.get() ?: 0L)
                    if (idleFor > subscriptionTimeoutMillis) {
                        pollKeepAliveJobs.remove(sessionId)
                        currentCoroutineContext()[Job]?.cancel()
                    }
                }
            }
        }
    }

    /**
     * Test-only: cancels every capture-loop / keep-alive job currently running under [scope] and
     * clears all per-sessionId state, so each test starts from a clean slate instead of racing a
     * previous test's still-running coroutine against that earlier test's already-torn-down mocks
     * (the actual cause of stray exceptions observed in this singleton's test class). Must never
     * be called from production code.
     */
    internal fun resetForTests() {
        scope.coroutineContext[Job]?.cancelChildren()
        pollKeepAliveJobs.clear()
        snapshotFlows.clear()
        lastPolledAtMs.clear()
    }
}
