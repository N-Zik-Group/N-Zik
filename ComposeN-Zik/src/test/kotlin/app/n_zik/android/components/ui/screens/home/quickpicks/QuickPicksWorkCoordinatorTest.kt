package app.n_zik.android.components.ui.screens.home.quickpicks

import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.requests.HomePage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickPicksWorkCoordinatorTest {
    @Test
    fun `authenticated startup shares one fetch and manual refresh and account switch still fetch`() = runTest {
        val coordinator = QuickPicksLoadCoordinator(backgroundScope)
        val firstAccount = quickPicksAccountIdentity("SAPISID=test-account-a; VISITOR_INFO1_LIVE=first", true)
        val firstFinished = CompletableDeferred<Unit>()
        var cached = false
        var fetches = 0
        val starts = mutableListOf<Boolean>()
        suspend fun fetch(force: Boolean) {
            if (force || !cached) {
                fetches++
                firstFinished.await()
                cached = true
            }
        }
        coordinator.request(firstAccount, onStart = { starts += it }) { fetch(it) }
        runCurrent()
        repeat(3) {
            // Visitor metadata and repeated authenticated emissions do not change the account.
            val sameAccount = quickPicksAccountIdentity("VISITOR_INFO1_LIVE=$it; SAPISID=test-account-a", true)
            coordinator.request(sameAccount) { fetch(it) }
        }
        firstFinished.complete(Unit)
        runCurrent()
        coordinator.request(firstAccount) { fetch(it) }
        runCurrent()
        assertEquals(1, fetches)
        assertEquals(listOf(false), starts)

        coordinator.request(firstAccount, refresh = true) { fetch(it) }
        runCurrent()
        assertEquals(2, fetches)
        val secondAccount = quickPicksAccountIdentity("SAPISID=test-account-b", true)
        coordinator.request(secondAccount) { force -> assertTrue(force); fetch(force) }
        runCurrent()
        assertEquals(3, fetches)
    }

    @Test
    fun `empty null and blank key sections do not prematurely end home attempts`() = runTest {
        val blank = Innertube.SongItem(null, null, null, null, null)
        val usable = blank.copy(info = Innertube.Info(
            name = "Usable", endpoint = NavigationEndpoint.Endpoint.Watch(videoId = "song"),
        ))
        val sections = List(16) { index ->
            HomePage.Section("Section $index", null, null, null, listOf(null, blank))
        } + HomePage.Section("Usable", null, null, null, listOf(usable))
        var attempts = 0
        loadQuickPicksHomeAttempts { attempts++; countUsableQuickPicksSections(sections) }
        assertEquals(1, countUsableQuickPicksSections(sections))
        assertEquals(3, attempts)
    }

    @Test
    fun `home attempts stop after the first rich response`() = runTest {
        var requests = 0
        loadQuickPicksHomeAttempts { requests++; 16 }
        assertEquals(1, requests)
    }

    @Test
    fun `home attempts retain three tries for failures and the fifteen section boundary`() = runTest {
        val sizes = listOf(0, 5, 15)
        var requests = 0
        loadQuickPicksHomeAttempts { sizes[requests++] }
        assertEquals(3, requests)
    }

    @Test
    fun `home attempts stop when a later merge crosses the threshold`() = runTest {
        var requests = 0
        loadQuickPicksHomeAttempts { requests++; if (requests == 2) 16 else 10 }
        assertEquals(2, requests)
    }

    @Test
    fun `ordinary reentry never queues another load`() = runTest {
        val coordinator = QuickPicksLoadCoordinator(backgroundScope)
        val finish = CompletableDeferred<Unit>()
        var loads = 0
        coordinator.request { loads++; finish.await() }
        runCurrent()
        repeat(10) { coordinator.request { loads++ } }
        finish.complete(Unit)
        runCurrent()
        assertEquals(1, loads)
        coordinator.request { loads++ }
        runCurrent()
        assertEquals(2, loads)
    }

    @Test
    fun `explicit refreshes coalesce to one deferred refresh with the latest owner`() = runTest {
        val coordinator = QuickPicksLoadCoordinator(backgroundScope)
        val finish = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        var resets = 0
        coordinator.request { calls += "initial"; finish.await() }
        runCurrent()
        repeat(3) { index ->
            coordinator.request(refresh = true, onStart = { resets++ }) { deferred ->
                assertTrue(deferred)
                calls += "refresh $index"
            }
        }
        assertEquals(0, resets)
        finish.complete(Unit)
        runCurrent()
        assertEquals(listOf("initial", "refresh 2"), calls)
        assertEquals(1, resets)
    }

    @Test
    fun `same identity replacement keeps a queued refresh with the current observer owner`() = runTest {
        val coordinator = QuickPicksLoadCoordinator(backgroundScope)
        val observations = QuickPicksObservationOwner(backgroundScope)
        val finish = CompletableDeferred<Unit>()
        val callbacks = mutableListOf<String>()
        var localResults = emptyList<String>()
        val firstOwner = observations.claim()
        coordinator.request(identity = "FR") { finish.await() }
        runCurrent()
        coordinator.request(identity = "FR", refresh = true, onStart = {
            callbacks += "old reset"
            localResults = emptyList()
            observations.start(firstOwner, restart = true) { publication ->
                observations.publish(publication) { localResults = listOf("old") }
                awaitCancellation()
            }
        }) { callbacks += "old load" }

        val currentOwner = observations.claim()
        observations.start(currentOwner) { publication ->
            observations.publish(publication) { localResults = listOf("current") }
            awaitCancellation()
        }
        runCurrent()
        coordinator.request(identity = "FR", onStart = { force ->
            assertTrue(force)
            callbacks += "current reset"
            localResults = emptyList()
            observations.start(currentOwner, restart = true) { publication ->
                observations.publish(publication) { localResults = listOf("current") }
                awaitCancellation()
            }
        }) { force ->
            assertTrue(force)
            callbacks += "current load"
        }

        finish.complete(Unit)
        runCurrent()
        assertEquals(listOf("current reset", "current load"), callbacks)
        assertEquals(listOf("current"), localResults)
    }

    @Test
    fun `idle explicit refresh resets once and forces the load`() = runTest {
        val coordinator = QuickPicksLoadCoordinator(backgroundScope)
        var resets = 0
        coordinator.request(refresh = true, onStart = { resets++ }) { deferred ->
            assertEquals(1, resets)
            assertTrue(deferred)
        }
        runCurrent()
        assertEquals(1, resets)
    }

    @Test
    fun `three replacements preserve the cleanup barrier and fence stale publications`() = runTest {
        val observations = QuickPicksObservationOwner(backgroundScope)
        val old = observations.claim()
        val finishCleanup = CompletableDeferred<Unit>()
        val published = mutableListOf<String>()
        var subscriptions = 0
        var maximumSubscriptions = 0
        observations.start(old) { publication ->
            subscriptions++
            maximumSubscriptions = maxOf(maximumSubscriptions, subscriptions)
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    finishCleanup.await()
                    observations.publish(publication) { published += "stale" }
                    subscriptions--
                }
            }
        }
        runCurrent()
        val intermediate = observations.claim()
        observations.start(intermediate) { published += "intermediate started" }
        runCurrent()
        val replacement = observations.claim()
        observations.start(replacement) { publication ->
            subscriptions++
            maximumSubscriptions = maxOf(maximumSubscriptions, subscriptions)
            observations.publish(publication) { published += "latest parameters" }
            try { awaitCancellation() } finally { subscriptions-- }
        }
        observations.start(old) { published += "old restarted" }
        runCurrent()
        assertTrue(published.isEmpty())
        finishCleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf("latest parameters"), published)
        assertEquals(1, maximumSubscriptions)
        assertEquals(1, subscriptions)
    }

    @Test
    fun `changed request identity queues only the latest parameters after active work`() = runTest {
        val coordinator = QuickPicksLoadCoordinator(backgroundScope)
        val finish = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        val starts = mutableListOf<Boolean>()
        coordinator.request(identity = "FR", onStart = { starts += it }) {
            calls += "FR"
            finish.await()
        }
        runCurrent()
        coordinator.request(identity = "US", onStart = { starts += it }) { calls += "US" }
        coordinator.request(identity = "NZ", onStart = { starts += it }) { force ->
            assertTrue(force)
            calls += "NZ"
        }
        assertEquals(listOf("FR"), calls)
        finish.complete(Unit)
        runCurrent()
        assertEquals(listOf("FR", "NZ"), calls)
        assertEquals(listOf(false, true), starts)
    }

    @Test
    fun `same identity invalidation is replayed once after the active load`() = runTest {
        val coordinator = QuickPicksLoadCoordinator(backgroundScope)
        val finish = CompletableDeferred<Unit>()
        var cachePresent = false
        coordinator.request {
            finish.await()
            cachePresent = true
        }
        runCurrent()
        coordinator.request(invalidate = true, onStart = { cachePresent = false }) { force ->
            assertTrue(force)
            assertFalse(cachePresent)
        }
        finish.complete(Unit)
        runCurrent()
        assertFalse(cachePresent)
    }

    @Test
    fun `ordinary observation start is idempotent and explicit restart replaces it`() = runTest {
        val observations = QuickPicksObservationOwner(backgroundScope)
        val owner = observations.claim()
        var starts = 0
        var stops = 0
        suspend fun observe() {
            starts++
            try { awaitCancellation() } finally { stops++ }
        }
        observations.start(owner) { observe() }
        runCurrent()
        repeat(3) { observations.start(owner) { observe() } }
        runCurrent()
        assertEquals(1, starts)
        observations.start(owner, restart = true) { observe() }
        runCurrent()
        assertEquals(2, starts)
        assertEquals(1, stops)
    }
}
