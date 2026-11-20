package app.n_zik.android.components.player

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import app.n_zik.android.ui.styling.dynamicColorPaletteOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
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
        ArtworkColorKey(profile, uri, revision, 0)

    private fun ready(bitmap: Bitmap, revision: String = "") = DisplayedArtworkResult.Ready(DisplayedArtwork(bitmap, revision))

    @Test fun `ready artwork for two tracks sharing a revision analyzes once without canonical decode`() = runTest {
        val source = ArtworkSource("default", "album", 0, 0)
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val displayed = DisplayedArtworkRegistry()
        val repository = ArtworkColorRepository(backgroundScope)
        val close = displayed.showPlayer(source.profile)
        val remove = displayed.register(source, MutableStateFlow(ready(bitmap)))
        var analyses = 0
        suspend fun loadTrackArtwork() = repository.load(key(uri = source.uri)) {
            val image = displayed.await(source, "") ?: error("must not decode a ready cover")
            assertSame(bitmap, image)
            analyses++
            raw
        }
        assertSame(raw, loadTrackArtwork())
        remove()
        close()
        assertSame(raw, loadTrackArtwork())
        assertEquals(1, analyses)
    }

    @Test fun `visible ownership and revisions reject outgoing and replaced pixels`() = runTest {
        val source = ArtworkSource("default", "file:///cover.jpg", 1, 0)
        val updated = source.copy(localRevision = 2)
        val oldBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val newBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val displayed = DisplayedArtworkRegistry()
        val close = displayed.showPlayer(source.profile)
        val old = MutableStateFlow<DisplayedArtworkResult?>(null)
        val oldPage = displayed.register(source, old.filterNotNull())
        val oldWaiter = async { displayed.await(source, "file-1") }
        testScheduler.runCurrent()
        oldPage()
        val current = MutableStateFlow(ready(newBitmap, "file-2"))
        val remove = displayed.register(updated, current)
        old.value = ready(oldBitmap, "file-1")
        assertNull(oldWaiter.await())
        assertNull(displayed.await(updated, "file-1"))
        assertSame(newBitmap, displayed.await(updated, "file-2"))
        remove()
        close()
    }

    @Test fun `expected pager can join after composition and missing artwork falls back within the bound`() = runTest {
        val source = ArtworkSource("default", "album", 0, 0)
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val displayed = DisplayedArtworkRegistry(waitMillis = 100)
        assertNull(displayed.await(source, ""))
        val expected = displayed.showPlayer(source.profile)
        val waiter = async { displayed.await(source, "") }
        testScheduler.advanceTimeBy(70)
        val remove = displayed.register(source, MutableStateFlow(ready(bitmap)))
        assertSame(bitmap, waiter.await())
        remove()
        val started = testScheduler.currentTime
        assertNull(displayed.await(source, ""))
        assertEquals(100L, testScheduler.currentTime - started)
        val failed = displayed.register(source, MutableStateFlow(DisplayedArtworkResult.Unavailable))
        val failedAt = testScheduler.currentTime
        assertNull(displayed.await(source, ""))
        assertEquals(failedAt, testScheduler.currentTime)
        failed()
        expected()
    }

    @Test fun `targeted invalidation removes only matching displayed artwork in the requested profile`() = runTest {
        val displayed = DisplayedArtworkRegistry()
        val target = ArtworkSource("default", "cover-alias", 0, 0)
        val unrelated = target.copy(uri = "unrelated")
        val otherProfile = target.copy(profile = "other")
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val closeDefault = displayed.showPlayer(target.profile)
        val closeOther = displayed.showPlayer(otherProfile.profile)
        val removals = listOf(target, unrelated, otherProfile).map {
            displayed.register(it, MutableStateFlow(ready(bitmap)))
        }

        displayed.invalidateSources(target.profile) { it == target.uri }

        assertNull(displayed.await(target, ""))
        assertSame(bitmap, displayed.await(unrelated, ""))
        assertSame(bitmap, displayed.await(otherProfile, ""))
        removals.forEach { it() }
        closeDefault()
        closeOther()
    }

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
        load(key().copy(generation = repository.generation.value))
        assertEquals(5, loads)
    }

    @Test fun `invalidated pending result cannot evict or replace newer request`() = runTest {
        val repository = ArtworkColorRepository(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        val old = async { repository.load(key()) { gate.await(); raw } }
        testScheduler.runCurrent()
        repository.invalidate()
        val updated = raw.copy(fallbackDominant = Color.Black)
        val newer = key().copy(generation = repository.generation.value)
        assertSame(updated, repository.load(newer) { updated })
        gate.complete(Unit)
        old.await()
        assertSame(updated, repository.load(newer) { error("new result must remain cached") })
    }

    @Test fun `targeted invalidation evicts palette aliases and cancels only their work in the requested profile`() = runTest {
        val repository = ArtworkColorRepository(backgroundScope)
        val original = key(uri = "original").copy(sourceIdentity = "cover-family")
        val alias = original.copy(uri = "alias")
        val otherProfile = alias.copy(profile = "other")
        val unrelated = key(uri = "unrelated")
        val targetRevision = repository.observe(ArtworkColorIdentity(original.profile, original.sourceIdentity))
        val unrelatedRevision = repository.observe(ArtworkColorIdentity(unrelated.profile, unrelated.sourceIdentity))
        val otherProfileRevision = repository.observe(ArtworkColorIdentity(otherProfile.profile, otherProfile.sourceIdentity))
        repository.load(original) { raw }
        repository.load(otherProfile) { raw }
        val gate = CompletableDeferred<Unit>()
        val oldAlias = async { runCatching { repository.load(alias) { awaitCancellation() } } }
        val unaffected = async { repository.load(unrelated) { gate.await(); raw } }
        testScheduler.runCurrent()

        repository.invalidateSources(original.profile, setOf(original.sourceIdentity))

        assertTrue(oldAlias.await().exceptionOrNull() is CancellationException)
        assertEquals(1L, targetRevision.revision.value)
        assertEquals(0L, unrelatedRevision.revision.value)
        assertEquals(0L, otherProfileRevision.revision.value)
        assertEquals(0L, repository.generation.value)
        val refreshed = raw.copy(fallbackDominant = Color.Black)
        assertSame(refreshed, repository.load(original) { refreshed })
        assertSame(refreshed, repository.load(alias) { refreshed })
        assertSame(raw, repository.load(otherProfile) { error("other profile must remain cached") })
        val joining = async { repository.load(unrelated) { error("unrelated work must remain shareable") } }
        testScheduler.runCurrent()
        gate.complete(Unit)
        assertSame(raw, unaffected.await())
        assertSame(raw, joining.await())
    }

    @Test fun `targeted invalidation rejects a stale admission wait and preserves unrelated work`() = runTest {
        val repository = ArtworkColorRepository(backgroundScope, maxInFlight = 1)
        val gate = CompletableDeferred<Unit>()
        val running = async { repository.load(key(uri = "running")) { gate.await(); raw } }
        val waiting = async {
            runCatching { repository.load(key(uri = "waiting")) { error("stale work must not start") } }
        }
        testScheduler.runCurrent()
        repository.invalidateSources("default", setOf("waiting"))
        gate.complete(Unit)
        assertSame(raw, running.await())
        assertTrue(waiting.await().exceptionOrNull() is CancellationException)
        assertSame(raw, repository.load(key(uri = "waiting")) { raw })
    }

    @Test fun `old request waiting for admission cannot refill a cleared cache`() = runTest {
        val repository = ArtworkColorRepository(backgroundScope, maxInFlight = 1)
        val gate = CompletableDeferred<Unit>()
        val first = async { repository.load(key()) { gate.await(); raw } }
        val waiting = async {
            runCatching { repository.load(key(uri = "waiting")) { error("stale work must not start") } }
        }
        testScheduler.runCurrent()
        repository.invalidate()
        gate.complete(Unit)
        first.await()
        assertTrue(waiting.await().exceptionOrNull() is kotlinx.coroutines.CancellationException)
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
