package app.n_zik.android.components.settings.accounts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubAccountTest {
    @Test
    fun installationConfirmationRequiresTheGitHubSuccessNotice() {
        val personal = "https://github.com/settings/installations/42"
        val organization = "https://github.com/organizations/N-Zik-Group/settings/installations/42"
        val notice = "Okay, N-Zik Sign In was installed on the @creator account."
        assertEquals("creator", confirmedGitHubInstallationAccount(personal, notice))
        assertEquals("N-Zik-Group", confirmedGitHubInstallationAccount(organization, notice.replace("@creator", "@N-Zik-Group")))
        assertNull(confirmedGitHubInstallationAccount(personal, "Installed now"))
        assertNull(confirmedGitHubInstallationAccount("https://github.com/apps/n-zik-sign-in/installations/new", notice))
        assertNull(confirmedGitHubInstallationAccount("https://example.com/settings/installations/42", notice))
    }

    @Test
    fun onlyRetryableResponsesContinuePolling() {
        assertEquals(5, nextGitHubPoll(5, "authorization_pending"))
        assertEquals(10, nextGitHubPoll(5, "slow_down"))
        assertEquals(15, nextGitHubPoll(10, "slow_down"))
        assertNull(nextGitHubPoll(5, "expired_token"))
        assertNull(nextGitHubPoll(5, "access_denied"))
        assertNull(nextGitHubPoll(5, "device_flow_disabled"))
    }
}

class GitHubContributionPolicyTest {
    @Test
    fun customModerationMessageOnlyAppliesToListedAccount() {
        val policy = """{"schemaVersion":1,"accounts":[{"githubId":123,"message":"Contact moderation."}]}"""
        assertEquals("Contact moderation.", contributionBlockMessage(policy, 123))
        assertNull(contributionBlockMessage(policy, 456))
    }
}
