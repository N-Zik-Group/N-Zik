package app.n_zik.android.components.settings.accounts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubAccountTest {
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
