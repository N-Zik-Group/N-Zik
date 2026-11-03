package app.n_zik.android.components.player

import androidx.compose.ui.graphics.Color
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class ArtworkColorRepositoryTest {
    private val raw = ArtworkColorSnapshot(dynamicColorPaletteOf(Color.Gray, false),
        dynamicColorPaletteOf(Color.Gray, true), null, null, Color.Gray, true)
    private fun key(uri: String = "cover", profile: String = "default", revision: String = "1") =
        ArtworkColorKey(profile, uri, revision, "HIGH", 0)

    @Test fun `concurrent consumers share work and cancelling a waiter does not cancel extraction`() = runTest {
        val repository = ArtworkColorRepository(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        var loads = 0
        val first = async { repository.load(key()) { loads++; gate.await(); raw } }
        val second = async { repository.load(key()) { error("must join first work") } }
        testScheduler.runCurrent()
        assertEquals(1, loads)
        first.cancelAndJoin()
        gate.complete(Unit)
        assertSame(raw, second.await())
        assertSame(raw, repository.load(key()) { error("must use completed cache") })
    }

    @Test fun `bounded cache and source revision profile invalidation do not reuse old work`() = runTest {
        val repository = ArtworkColorRepository(backgroundScope, capacity = 2)
        var loads = 0
        suspend fun load(source: ArtworkColorKey) = repository.load(source) { loads++; raw }
        load(key())
        load(key(revision = "2"))
        load(key(profile = "other"))
        load(key())
        assertEquals(4, loads)
        repository.invalidate()
        load(key())
        assertEquals(5, loads)
    }

    @Test fun `invalidated pending result cannot evict or replace newer request`() = runTest {
        val repository = ArtworkColorRepository(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        val old = async { repository.load(key()) { gate.await(); raw } }
        testScheduler.runCurrent()
        repository.invalidate()
        val updated = raw.copy(fallbackDominant = Color.Black)
        assertSame(updated, repository.load(key()) { updated })
        gate.complete(Unit)
        old.await()
        assertSame(updated, repository.load(key()) { error("new result must remain cached") })
    }

    @Test fun `failed shared work can retry and brightness derivation never reloads`() = runTest {
        val repository = ArtworkColorRepository(backgroundScope)
        var attempts = 0
        val failed = async {
            runCatching { repository.load(key()) { attempts++; error("load failure") } }
        }
        assertTrue(failed.await().isFailure)
        val result = requireNotNull(repository.load(key()) { attempts++; raw })
        for (dark in listOf(false, true)) for (enabled in listOf(false, true, false)) result.display(dark, enabled)
        repository.load(key()) { error("deriving cannot invalidate raw extraction") }
        assertEquals(2, attempts)
    }
    @Test fun `last canceled waiter releases its abandoned source and next request can retry`() = runTest {
        val repository = ArtworkColorRepository(backgroundScope)
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val abandoned = async {
            repository.load(key()) {
                started.complete(Unit)
                try { awaitCancellation() } finally { stopped.complete(Unit) }
            }
        }
        started.await()
        abandoned.cancelAndJoin()
        stopped.await()
        assertSame(raw, repository.load(key()) { raw })
    }

    @Test fun `distinct source work is bounded and canceled admission waits never create workers`() = runTest {
        val repository = ArtworkColorRepository(backgroundScope, maxInFlight = 2)
        val gate = CompletableDeferred<Unit>()
        var active = 0
        var maximum = 0
        var started = 0
        val requests = List(12) { index ->
            async {
                repository.load(key(uri = "cover-$index")) {
                    started++
                    active++
                    maximum = maxOf(maximum, active)
                    try { gate.await(); raw } finally { active-- }
                }
            }
        }
        testScheduler.runCurrent()
        assertEquals(2, started)
        assertEquals(2, maximum)
        requests.drop(2).forEach { it.cancelAndJoin() }
        requests.take(2).forEach { it.cancelAndJoin() }
        testScheduler.runCurrent()
        assertEquals(0, active)
        assertEquals(2, started)
        assertSame(raw, repository.load(key(uri = "new-source")) { raw })
    }
}
