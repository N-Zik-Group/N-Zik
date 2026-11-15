package app.n_zik.android.components.settings.accounts

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GitHubPublicationAuthorizationTest {
    @Test
    fun recreatedScreenResumesAnAuthorizationCompletedWithoutAnObserverExactlyOnce() = runTest {
        val authorization = GitHubPublicationAuthorization()
        val savedRequestKey = authorization.begin("first")
        val oldScreen = backgroundScope.launch { authorization.state.collect {} }
        runCurrent()
        oldScreen.cancelAndJoin()
        authorization.authorize(savedRequestKey, "first")
        authorization.finish(savedRequestKey)
        assertTrue(authorization.consume(savedRequestKey, "first"))
        assertFalse(authorization.consume(savedRequestKey, "first"))
        assertNull(authorization.state.value)
    }

    @Test
    fun cancelledOrReplacedAuthorizationCannotResumeANewSubmission() {
        val authorization = GitHubPublicationAuthorization()
        val abandoned = authorization.begin("first")
        authorization.cancel()
        val current = authorization.begin("first")
        authorization.authorize(abandoned, "first")
        authorization.finish(abandoned)
        assertFalse(authorization.consume(abandoned, "first"))
        assertFalse(authorization.consume(current, "first"))
        authorization.authorize(current, "first")
        assertTrue(authorization.consume(current, "first"))
    }

    @Test
    fun switchingProfilesCannotCompleteOrConsumeThePreviousRequest() {
        val authorization = GitHubPublicationAuthorization()
        val request = authorization.begin("first")
        authorization.authorize(request, "second")
        assertFalse(authorization.consume(request, "first"))
        authorization.authorize(request, "first")
        assertFalse(authorization.consume(request, "second"))
        assertTrue(authorization.consume(request, "first"))
    }

    @Test
    fun failedAuthorizationClearsItsTicket() {
        val authorization = GitHubPublicationAuthorization()
        val request = authorization.begin("first")
        authorization.finish(request)
        authorization.authorize(request, "first")
        assertNull(authorization.state.value)
        assertFalse(authorization.consume(request, "first"))
    }
}
