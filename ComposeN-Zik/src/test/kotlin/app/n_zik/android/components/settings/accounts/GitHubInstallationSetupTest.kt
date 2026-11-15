package app.n_zik.android.components.settings.accounts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GitHubInstallationSetupTest {
    @Test
    fun replacingTheScreenObserverRetainsForkPreparationAndVerifiedSession() = runTest {
        val forkReady = CompletableDeferred<Unit>()
        val setup = GitHubInstallationSetup(backgroundScope, { forkReady.await() }, { throw AssertionError(it) })
        val session = session(123)
        setup.prepare(session)
        val oldScreen = backgroundScope.launch { setup.state.collect {} }
        runCurrent()

        oldScreen.cancelAndJoin()
        assertTrue(setup.state.first().preparing)
        assertNull(setup.state.value.session)
        forkReady.complete(Unit)
        runCurrent()

        val restoredScreen = setup.state.first()
        assertFalse(restoredScreen.preparing)
        assertSame(session, restoredScreen.session)
        assertTrue(setup.consume(session))
        assertFalse(setup.consume(session))
    }

    @Test
    fun dismissingSetupRejectsACompletedNetworkRequestAndStaleConfirmation() = runTest {
        val forkReady = CompletableDeferred<Unit>()
        val setup = GitHubInstallationSetup(backgroundScope, {
            withContext(NonCancellable) { forkReady.await() }
        }, { throw AssertionError(it) })
        val session = session(123)
        setup.prepare(session)
        runCurrent()

        setup.cancel()
        forkReady.complete(Unit)
        runCurrent()

        assertFalse(setup.state.value.preparing)
        assertNull(setup.state.value.session)
        assertFalse(setup.consume(session))
    }

    @Test
    fun anOldPreparationCannotClearOrConfirmANewerSetup() = runTest {
        val firstReady = CompletableDeferred<Unit>()
        val nextReady = CompletableDeferred<Unit>()
        val first = session(123)
        val next = session(456)
        val setup = GitHubInstallationSetup(backgroundScope, { current ->
            withContext(NonCancellable) { if (current === first) firstReady.await() else nextReady.await() }
        }, { throw AssertionError(it) })
        setup.prepare(first)
        runCurrent()
        setup.cancel()
        setup.prepare(next)
        runCurrent()

        firstReady.complete(Unit)
        runCurrent()
        assertTrue(setup.state.value.preparing)
        assertFalse(setup.consume(first))
        nextReady.complete(Unit)
        runCurrent()

        assertSame(next, setup.state.value.session)
        assertFalse(setup.consume(first))
        assertTrue(setup.consume(next))
    }

    @Test
    fun forkFailureEndsPreparationAndAllowsRetryWithoutAnAuthorizedSession() = runTest {
        val failure = IllegalStateException("Fork unavailable")
        val failures = mutableListOf<Exception>()
        var fails = true
        val setup = GitHubInstallationSetup(backgroundScope, { if (fails) throw failure }, { failures += it })
        val session = session(123)
        setup.prepare(session)
        runCurrent()

        assertEquals(listOf(failure), failures)
        assertFalse(setup.state.value.preparing)
        assertNull(setup.state.value.session)
        assertFalse(setup.consume(session))
        fails = false
        setup.prepare(session)
        runCurrent()

        assertSame(session, setup.state.value.session)
        setup.cancel()
        assertFalse(setup.consume(session))
    }

    private fun session(id: Long) = GitHubAccount.SetupSession(id, "creator$id", "test-token", Long.MAX_VALUE)
}
