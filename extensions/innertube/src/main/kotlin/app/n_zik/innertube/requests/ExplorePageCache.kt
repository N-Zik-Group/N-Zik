package app.n_zik.innertube.requests

import app.n_zik.innertube.Innertube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal data class ExploreCacheKey(val transportGeneration: Long, val sessionGeneration: Long)

/** Cache only the latest session's parsed page, and cancel work once its last waiter leaves. */
internal class ExplorePageCache(private val clock: TimeSource = TimeSource.Monotonic) {
    private class Flight {
        lateinit var task: Deferred<Innertube.DiscoverPage>
        var waiters = 0
    }

    private data class CachedPage(val page: Innertube.DiscoverPage, val expiresAt: TimeMark)

    private class Entry(val key: ExploreCacheKey) {
        var cached: CachedPage? = null
        var flight: Flight? = null
    }

    private val lock = Any()
    private var current: Entry? = null

    suspend fun load(
        key: ExploreCacheKey,
        forceRefresh: Boolean,
        requireCurrent: () -> Unit,
        fetch: suspend () -> Innertube.DiscoverPage,
    ): Innertube.DiscoverPage {
        val callerContext = currentCoroutineContext()
        callerContext.ensureActive()
        val (entry, flight) = synchronized(lock) {
            requireCurrent()
            val entry = current?.takeIf { it.key == key } ?: Entry(key).also { current = it }
            entry.cached?.takeIf { !forceRefresh && it.expiresAt.hasNotPassedNow() }?.let { return it.page }
            val flight = entry.flight?.takeUnless { it.task.isCompleted } ?: Flight().also { created ->
                // Keep the caller's dispatcher. The waiters own the task's lifetime jointly.
                created.task = CoroutineScope(callerContext.minusKey(Job)).async(start = CoroutineStart.LAZY) {
                    val page = fetch()
                    ensureActive()
                    synchronized(lock) {
                        requireCurrent()
                        if (current === entry && entry.flight === created) {
                            entry.cached = CachedPage(page, clock.markNow() + 30.seconds)
                        }
                    }
                    page
                }
                entry.flight = created
            }
            flight.waiters++
            entry to flight
        }

        try {
            return flight.task.await()
        } finally {
            val abandon = synchronized(lock) {
                flight.waiters--
                (flight.waiters == 0).also { if (it && entry.flight === flight) entry.flight = null }
            }
            if (abandon) flight.task.cancel()
        }
    }
}
