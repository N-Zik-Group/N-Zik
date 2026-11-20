package app.n_zik.innertube.requests

import app.n_zik.innertube.Innertube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExplorePageCacheTest {
    private val clock = TestTimeSource()
    private val cache = ExplorePageCache(clock)
    private val key = ExploreCacheKey(transportGeneration = 1, sessionGeneration = 2)
    private val original = Innertube.DiscoverPage(emptyList(), emptyList())
    private val refreshed = Innertube.DiscoverPage(emptyList(), emptyList())

    @Test
    fun `warm callers reuse the parsed page and refresh bypasses it`() = runTest {
        assertSame(original, cache.load(key, false, {}) { original })
        assertSame(original, cache.load(key, false, {}) { error("Unexpected duplicate browse") })
        assertSame(refreshed, cache.load(key, true, {}) { refreshed })

        clock += 29.seconds
        assertSame(refreshed, cache.load(key, false, {}) { error("Unexpected duplicate browse") })
        clock += 1.seconds
        assertSame(original, cache.load(key, false, {}) { original })
    }

    @Test
    fun `overlapping callers share a fetch after the first caller cancels`() = runTest {
        val response = CompletableDeferred<Innertube.DiscoverPage>()
        var requests = 0
        val first = async {
            cache.load(key, false, {}) {
                requests++
                response.await()
            }
        }
        val second = async {
            cache.load(key, true, {}) {
                requests++
                response.await()
            }
        }
        runCurrent()

        first.cancelAndJoin()
        response.complete(original)

        assertSame(original, second.await())
        assertEquals(1, requests)
    }

    @Test
    fun `the last cancellation stops the request and the next call can retry`() = runTest {
        val stopped = CompletableDeferred<Unit>()
        val waiter = async {
            cache.load(key, false, {}) {
                try {
                    awaitCancellation()
                } finally {
                    stopped.complete(Unit)
                }
            }
        }
        runCurrent()
        waiter.cancelAndJoin()
        runCurrent()

        assertTrue(stopped.isCompleted)
        assertSame(refreshed, cache.load(key, false, {}) { refreshed })
    }

    @Test
    fun `failed requests do not enter the cache`() = runTest {
        val failure = IllegalStateException("browse failed")
        val outcome = runCatching { cache.load(key, false, {}) { throw failure } }

        assertTrue(outcome.exceptionOrNull() is IllegalStateException)
        assertEquals(failure.message, outcome.exceptionOrNull()?.message)
        assertSame(refreshed, cache.load(key, false, {}) { refreshed })
    }

    @Test
    fun `changing session or transport skips old pages and rejects stale in flight results`() = runTest {
        var current = key
        val deferredResponse = CompletableDeferred<Innertube.DiscoverPage>()
        val oldRequest = async {
            cache.load(key, false, {
                if (current != key) throw CancellationException("session changed")
            }) { deferredResponse.await() }
        }
        runCurrent()

        current = key.copy(sessionGeneration = key.sessionGeneration + 1)
        assertSame(refreshed, cache.load(current, false, {}) { refreshed })
        deferredResponse.complete(original)
        assertTrue(runCatching { oldRequest.await() }.exceptionOrNull() is CancellationException)
        assertSame(refreshed, cache.load(current, false, {}) { error("Old request published") })

        current = current.copy(transportGeneration = current.transportGeneration + 1)
        assertSame(original, cache.load(current, false, {}) { original })
    }
}
