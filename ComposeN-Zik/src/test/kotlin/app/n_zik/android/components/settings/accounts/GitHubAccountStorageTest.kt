package app.n_zik.android.components.settings.accounts

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class GitHubAccountStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var profile = "first"
    private var encryptedAvailable = true
    private fun identities(profile: String) = context.getSharedPreferences("github-test-identity-$profile", 0)
    private fun secrets(profile: String) = context.getSharedPreferences("github-test-secrets-$profile", 0)
    private val legacy = context.getSharedPreferences("github-test-legacy", 0)
    private fun storage() = GitHubAccountStorage(
        { profile }, ::identities,
        { owner -> check(encryptedAvailable); secrets(owner) }, legacy, { 1_000L },
    )

    @Test
    fun anUnprovenGlobalIdentityIsNotInheritedByAnotherProfile() {
        legacy.edit().putString("identity", """{"id":123,"login":"creator"}""").commit()
        assertNull(storage().userId())
        assertFalse(identities(profile).contains("identity"))
    }

    @Test
    fun migrationRequiresTheProfilesBackupOrItsMatchingCredential() {
        legacy.edit().putString("identity", """{"id":123,"login":"creator"}""").commit()
        secrets("first").edit().putString("githubPublicationSession", """{"id":123,"token":"first-token","expires":9000}""").commit()
        secrets("second").edit().putString("githubBackupIdentity", """{"id":456,"login":"another"}""").commit()
        val accounts = storage()
        assertEquals(123L, accounts.userId())
        assertEquals("first-token", accounts.tokenFor(123))
        profile = "second"
        assertEquals(456L, accounts.userId())
        assertNull(accounts.tokenFor(123))
    }

    @Test
    fun logoutStaysEffectiveAfterSwitchingAwayAndRestartingTheStore() {
        val accounts = storage()
        accounts.save(123, "creator", "first-token", 9000)
        profile = "second"
        assertNull(accounts.userId())
        accounts.save(456, "another", "second-token", 9000)
        accounts.disconnect()
        profile = "first"
        assertEquals(123L, accounts.userId())
        assertEquals("first-token", accounts.tokenFor(123))
        profile = "second"
        assertNull(storage().userId())
        assertNull(storage().tokenFor(456))
    }

    @Test
    fun cachedTokensNeverCrossProfilesEvenForTheSameGitHubAccount() {
        val accounts = storage()
        accounts.save(123, "creator", "first-token", 9000)
        profile = "second"
        accounts.save(123, "creator", "second-token", 9000)
        profile = "first"
        assertEquals("first-token", accounts.tokenFor(123))
        profile = "second"
        assertEquals("second-token", accounts.tokenFor(123))
    }

    @Test
    fun logoutCannotRestoreStaleSecretsWhenTheKeystoreRecovers() {
        val accounts = storage()
        accounts.save(123, "creator", "old-token", 9000)
        encryptedAvailable = false
        accounts.disconnect()
        encryptedAvailable = true
        assertNull(storage().userId())
        assertNull(storage().tokenFor(123))
        accounts.restoreIdentity("""{"id":123,"login":"creator"}""")
        assertEquals(123L, accounts.userId())
        assertNull(accounts.tokenFor(123))
    }

    @Test
    fun authorizationFromThePreviousProfileCannotSaveIntoTheNewProfile() {
        val accounts = storage()
        val authorizationProfile = profile
        profile = "second"
        assertFalse(accounts.save(123, "creator", "test-token", 9000, authorizationProfile))
        assertNull(accounts.userId())
        assertFalse(secrets("first").contains("githubPublicationSession"))
        assertFalse(secrets("second").contains("githubPublicationSession"))
    }

    @Test
    fun additionalWorkflowPermissionBelongsToTheProfileThatRequestedIt() {
        val accounts = storage()
        accounts.save(123, "creator", "first-token", 9000)
        accounts.requireWorkflow()
        assertTrue(accounts.needsWorkflow(123))
        assertNull(accounts.tokenFor(123))
        profile = "second"
        accounts.save(123, "creator", "second-token", 9000)
        assertFalse(accounts.needsWorkflow(123))
        assertEquals("second-token", accounts.tokenFor(123))
    }

    @Test
    fun restoringAnInactiveAccountInvalidatesItsCachedTokenAndSurvivesStoreRestart() {
        val accounts = storage()
        accounts.save(123, "creator", "first-token", 9000)
        profile = "second"
        accounts.save(456, "active", "second-token", 9000)
        profile = "first"
        accounts.save(123, "creator", "cached-first-token", 9000)
        profile = "second"

        accounts.restoreIdentity("""{"id":123,"login":"restored"}""", "first")

        assertEquals(456L, accounts.userId())
        assertEquals("second-token", accounts.tokenFor(456))
        profile = "first"
        assertEquals("restored", accounts.login())
        assertNull(accounts.tokenFor(123))
        assertNull(storage().tokenFor(123))
        assertFalse(secrets("first").contains("githubPublicationSession"))
    }
}
