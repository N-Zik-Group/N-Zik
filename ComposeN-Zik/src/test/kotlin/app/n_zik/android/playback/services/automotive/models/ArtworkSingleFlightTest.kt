package app.n_zik.android.playback.services.automotive.models

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkSingleFlightTest {
    // JUnit's synchronous test method bridges to the suspend cache API.
    @Test fun `cancelling owner lets active duplicate reload and warm the cache`() = runBlocking {
        val cache = ArtworkBytesCache()
        val started = CompletableDeferred<Unit>()
        val owner = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrLoad("owner-cancelled") {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        var duplicateLoads = 0
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrLoad("owner-cancelled") {
                duplicateLoads++
                byteArrayOf(9)
            }
        }
        owner.cancelAndJoin()
        assertArrayEquals(byteArrayOf(9), waiter.await())
        assertEquals(1, duplicateLoads)
        assertArrayEquals(byteArrayOf(9), cache.getOrLoad("owner-cancelled") { error("must hit cache") })
    }

    @Test fun `cancelling a duplicate waiter does not cancel the owner or leak its flight`() = runBlocking {
        val cache = ArtworkBytesCache()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val owner = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrLoad("same") {
                started.complete(Unit)
                release.await()
                byteArrayOf(7)
            }
        }
        started.await()
        var duplicateLoads = 0
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrLoad("same") { duplicateLoads++; byteArrayOf(8) }
        }
        waiter.cancelAndJoin()
        release.complete(Unit)
        assertArrayEquals(byteArrayOf(7), owner.await())
        assertEquals(0, duplicateLoads)
        assertArrayEquals(byteArrayOf(7), cache.getOrLoad("same") { error("must hit cache") })
    }

    @Test fun `failed owner removes flight so the next caller retries`() = runBlocking {
        val cache = ArtworkBytesCache()
        try {
            cache.getOrLoad("retry") { throw IllegalStateException("decode failed") }
            error("failure must propagate")
        } catch (_: IllegalStateException) {
            assertArrayEquals(byteArrayOf(3), cache.getOrLoad("retry") { byteArrayOf(3) })
        }
    }
}
