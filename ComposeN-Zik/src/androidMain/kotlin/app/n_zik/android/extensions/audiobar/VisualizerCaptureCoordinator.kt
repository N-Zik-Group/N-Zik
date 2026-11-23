package app.n_zik.android.extensions.audiobar

import app.n_zik.android.privacy.PermissionUsageJournal
import app.n_zik.android.privacy.PermissionFeature
import app.n_zik.android.privacy.JournalPermission
import app.n_zik.android.privacy.PermissionEventKind

import app.n_zik.android.utils.coroutines.NzikDispatchers
import android.media.audiofx.Visualizer
import kotlinx.coroutines.CancellationException
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

/** Shares one native capture loop per audio session while it has collectors. */
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
                var lastJournalAt = 0L
                while (currentCoroutineContext().isActive) {
                    try {
                        emit(VisualizerHelper.captureNow(sessionId))
                        val now = System.currentTimeMillis()
                        if (now - lastJournalAt >= 30_000L && VisualizerHelper.getSharedVisualizer(sessionId)?.enabled == true) {
                            lastJournalAt = now
                            PermissionUsageJournal.record(app.n_zik.android.appContext(), PermissionFeature.VISUALIZER, JournalPermission.MICROPHONE, PermissionEventKind.USED)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
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

    private val silentSnapshot by lazy {
        val size = Visualizer.getCaptureSizeRange().last()
        VisualizerSnapshot(fft = ByteArray(size), waveform = ByteArray(size) { 0x80.toByte() }, samplingRate = 44_100_000)
    }

    private val lastPolledAtMs = ConcurrentHashMap<Int, AtomicLong>()
    private val pollKeepAliveJobs = ConcurrentHashMap<Int, Job>()

    /** Starts capture for a polling view and supplies silence until the first frame arrives. */
    fun currentSnapshot(sessionId: Int): VisualizerSnapshot {
        lastPolledAtMs.computeIfAbsent(sessionId) { AtomicLong(0L) }.set(System.currentTimeMillis())
        ensurePollKeepAlive(sessionId)
        return snapshotFlow(sessionId).value ?: silentSnapshot
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
