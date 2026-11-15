package app.n_zik.android.components.settings.accounts

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class GitHubInstallationAccessTest {
    private val installation = "https://github.com/settings/installations/42"

    @Test
    fun savedAllRepositoriesAccessCompletesSetupWithoutAnIndividualForkRow() {
        val state = """{"login":"creator","application":true,"fork":false,"allRepositories":true}"""

        assertEquals("Creator", confirmedGitHubInstallationAccess(installation, "Creator", state))
    }

    @Test
    fun selectedForkAccessStillCompletesSetup() {
        val state = """{"login":"creator","application":true,"fork":true,"allRepositories":false}"""

        assertEquals("creator", confirmedGitHubInstallationAccess(installation, "creator", state))
    }

    @Test
    fun unrelatedAccountAppPageOrUnsavedAccessCannotCompleteSetup() {
        val state = """{"login":"creator","application":true,"fork":false,"allRepositories":true}"""

        assertNull(confirmedGitHubInstallationAccess(installation, "another", state))
        assertNull(confirmedGitHubInstallationAccess(installation, "creator", state.replace("\"application\":true", "\"application\":false")))
        assertNull(confirmedGitHubInstallationAccess(installation, "creator", state.replace("\"allRepositories\":true", "\"allRepositories\":false")))
        assertNull(confirmedGitHubInstallationAccess("https://example.com/settings/installations/42", "creator", state))
        assertNull(confirmedGitHubInstallationAccess("https://github.com/apps/n-zik-sign-in/installations/new", "creator", state))
        assertNull(confirmedGitHubInstallationAccess(installation, "creator", "null"))
    }
}
