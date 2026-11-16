package app.n_zik.android.utils.coroutines

import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import timber.log.Timber
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Single source of truth for every named thread/dispatcher in the app (issue #606).
 * Process-lifetime singleton: threads are daemon and never explicitly closed here or by
 * any caller — closing a process-lifetime executor from an Activity/Service scope kills it
 * for the rest of the process (see the Cubic-Music `Threads.close()`-in-`onDestroy` bug this
 * design avoids).
 */
object NzikDispatchers {

    /** Thread 1 — UI & gestures. Always Dispatchers.Main. */
    val UI: MainCoroutineDispatcher = Dispatchers.Main

    /** Thread 2 — Audio & playback. Single thread: guarantees ordering. Absorbs the former
     *  `PlaybackDispatchers.STREAM_RESOLVER`. ExoPlayer `player.*` access itself stays on UI.
     *  Lazy: a Kotlin `object`'s properties all initialize together on first touch of any one
     *  of them, so eagerly declaring five independent thread pools here would spin up every
     *  pool (~12 threads) the moment any single one is first used. */
    val PLAYBACK: CoroutineDispatcher by lazy {
        Executors.newSingleThreadExecutor(namedThreadFactory("nzik-playback")).asCoroutineDispatcher()
    }

    /** Thread 3a — Visualizer FFT capture. Single thread: serializes access to the native
     *  `Visualizer` shared per sessionId (thread-safe by construction, not by locking). */
    val VISUALIZER: CoroutineDispatcher by lazy {
        Executors.newSingleThreadExecutor(namedThreadFactory("nzik-visualizer")).asCoroutineDispatcher()
    }

    /** Thread 3b — CPU-bound media work: queue conversion at playback start
     *  (`forcePlayAtIndex` in `utils/Player.kt`), palette extraction, LRC/TTML parsing, bitmap circling
     *  (`Icon.getCircledBitmap` in `NextVisualizer.kt`), and botguard challenge/integrity-token
     *  JSON parsing (`PoTokenWebView.kt`). Isolated from VISUALIZER's continuous loop and from
     *  DATA. */
    val MEDIA: CoroutineDispatcher by lazy {
        Executors.newFixedThreadPool(2, indexedThreadFactory("nzik-media")).asCoroutineDispatcher()
    }

    /** Thread 5 — Network & disk IO: album art, song info, DB changes, downloads. */
    val DATA: CoroutineDispatcher = Dispatchers.IO

    /** Named, bounded Room query executor — same off-main behavior as Room's internal
     *  default, just visible by name in ANR/profiler traces. Wrapped in a plain `Executor`
     *  SAM (not exposed as the underlying `ExecutorService`) so a caller cannot cast it to
     *  call `.shutdown()` — this object is process-lifetime and must never be closed. */
    val ROOM_QUERY_EXECUTOR: Executor by lazy {
        val pool = Executors.newFixedThreadPool(4, indexedThreadFactory("nzik-room-query"))
        Executor { runnable -> pool.execute(runnable) }
    }

    /** Named, bounded Room transaction executor — see [ROOM_QUERY_EXECUTOR]. */
    val ROOM_TX_EXECUTOR: Executor by lazy {
        val pool = Executors.newFixedThreadPool(4, indexedThreadFactory("nzik-room-tx"))
        Executor { runnable -> pool.execute(runnable) }
    }

    /**
     * Creates a hardened fire-and-forget scope (issue #606, Goal G5).
     *
     * Every bare `CoroutineScope(dispatcher)` left uncancelled by design gets exactly two
     * additions here: a [SupervisorJob] (an exception in one coroutine can no longer cancel
     * its siblings) and a [CoroutineExceptionHandler] that turns an otherwise unhandled
     * exception (which on Android crashes the process or silently kills sibling coroutines)
     * into a Timber log. The scope itself is still never cancelled by this helper — callers
     * that used to cancel their scope keep doing so on the returned [CoroutineScope].
     *
     * @param dispatcher the exact dispatcher this scope must run on (never changed by this helper)
     * @return a `CoroutineScope` on [dispatcher] with `SupervisorJob()` + Timber exception handler
     */
    fun fireAndForget(dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(dispatcher + SupervisorJob() + fireAndForgetExceptionHandler)

    /**
     * Same as [fireAndForget] for scopes whose context carries extra elements beside the
     * dispatcher (e.g. a parent [kotlinx.coroutines.Job] that must stay the cancellation
     * root, or a [kotlinx.coroutines.CoroutineName] for traceability).
     *
     * A [Job] already present in [context] is kept as the cancellation root; a
     * [SupervisorJob] is only added when [context] carries none. A dispatcher-less context
     * (e.g. a [SupervisorJob] alone) is allowed: launches then need an explicit dispatcher,
     * otherwise they run on the caller's thread.
     */
    fun fireAndForget(context: CoroutineContext): CoroutineScope =
        CoroutineScope(context + (context[Job] ?: SupervisorJob()) + fireAndForgetExceptionHandler)

    /** Isolates child failures while inheriting the parent's dispatcher and cancellation. */
    internal fun supervisedChildScope(parent: CoroutineScope): CoroutineScope {
        val context = parent.coroutineContext
        return CoroutineScope(context + SupervisorJob(context[Job]))
    }

    private val fireAndForgetExceptionHandler = CoroutineExceptionHandler { context, e ->
        // Name + dispatcher identify the failing site for this shared handler.
        Timber.tag("FireAndForget").e(
            e,
            "Unhandled exception in fire-and-forget scope (name=%s, dispatcher=%s)",
            context[CoroutineName]?.name,
            context[ContinuationInterceptor],
        )
    }

    private fun namedThreadFactory(name: String) = ThreadFactory { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }

    private fun indexedThreadFactory(prefix: String): ThreadFactory {
        val counter = AtomicInteger(1)
        return ThreadFactory { runnable ->
            Thread(runnable, "$prefix-${counter.getAndIncrement()}").apply { isDaemon = true }
        }
    }
}
