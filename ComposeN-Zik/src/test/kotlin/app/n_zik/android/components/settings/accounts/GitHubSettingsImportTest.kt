package app.n_zik.android.components.settings.accounts

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.security.crypto.MasterKey
import app.n_zik.android.components.import.ImportSettings
import app.n_zik.android.components.ui.screens.profiles.profileSecurePrefs
import app.n_zik.android.core.rescue.RescueFiles
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.clearEncryptedPreferencesCache
import app.n_zik.android.utils.encryptedPreferences
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.utils.writeProfileEntries
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.File
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GitHubSettingsImportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val restoredIdentity = """{"id":789,"login":"restored"}"""

    private fun identities(profile: String) = context.getSharedPreferences("github_account_profile_$profile", 0)
    private fun secrets(profile: String) = context.getSharedPreferences("github-import-secrets-$profile", 0)

    @Before
    fun setup() {
        assertTrue(context.writeProfileEntries(listOf("work" to "Work")))
        setActiveProfile("default", context)
        mockkStatic("app.n_zik.android.components.ui.screens.profiles.ProfileSecurePrefsKt")
        every { profileSecurePrefs(any(), any()) } answers { secrets(secondArg()) }
        seedAccount("default", 456, "active")
        seedAccount("work", 123, "previous")
        GitHubAccount.read(context)
        RescueFiles.initialize(context)
        RescueFiles.setRescueProfile("work")
    }

    @After
    fun teardown() {
        GitHubAccount.cancel()
        clearEncryptedPreferencesCache()
        unmockkAll()
    }

    @Test
    fun secondaryProfileKeystoreFailurePreservesTheDefaultPreferencesFile() {
        val base = context.getSharedPreferences("secure_preferences", Context.MODE_PRIVATE)
        assertTrue(base.edit().putString("sentinel", "kept").commit())
        setActiveProfile("work", context)
        clearEncryptedPreferencesCache()
        mockkConstructor(MasterKey.Builder::class)
        every { anyConstructed<MasterKey.Builder>().build() } throws IllegalStateException("Unavailable test key")

        assertTrue(runCatching { context.encryptedPreferences }.isFailure)
        assertEquals("kept", context.getSharedPreferences("secure_preferences", Context.MODE_PRIVATE).getString("sentinel", null))
    }

    @Test
    fun normalImportReplacesAnActiveAccountAndInvalidatesItsToken() {
        setActiveProfile("work", context)
        GitHubAccount.read(context)

        importIdentity(rescue = false)

        assertRestoredAccount()
        assertEquals("restored", GitHubAccount.login.value)
    }

    @Test
    fun normalImportReplacesAnInactiveAccountWithoutCancellingTheActiveAuthorization() {
        assertInactiveImport(rescue = false, signedOut = false)
    }

    @Test
    fun normalImportRestoresAnInactiveSignedOutAccount() {
        assertInactiveImport(rescue = false, signedOut = true)
    }

    @Test
    fun rescueImportReplacesAnInactiveAccountWithoutCancellingTheActiveAuthorization() {
        assertInactiveImport(rescue = true, signedOut = false)
    }

    @Test
    fun rescueImportRestoresAnInactiveSignedOutAccount() {
        assertInactiveImport(rescue = true, signedOut = true)
    }

    @Test
    fun rescueImportKeepsTheCapturedProfileAfterTheSelectorChanges() {
        val captured = RescueFiles.rescueTargetProfile()
        val credentials = secrets(captured)
        RescueFiles.setRescueProfile("default")

        RescueFiles.importSettings(context, Uri.fromFile(identityCsv()), Result.success(credentials), captured).getOrThrow()

        assertEquals(456L, GitHubAccount.userId(context))
        assertEquals("default-token", GitHubAccount.tokenFor(context, 456))
        setActiveProfile("work", context)
        assertRestoredAccount()
    }

    @Test
    fun rescueExportKeepsTheCapturedProfileAfterTheSelectorChanges() {
        val captured = RescueFiles.rescueTargetProfile()
        val credentials = secrets(captured)
        RescueFiles.setRescueProfile("default")
        val target = File(context.cacheDir, "github-settings-export.csv")

        RescueFiles.exportSettings(
            context, Uri.fromFile(target), Result.success(credentials), includeGitHub = true, profile = captured,
        ).getOrThrow()

        val restored = target.inputStream().use(RescueFiles::readSettingsCsv)
            .single { it.second == "githubBackupIdentity" }
        assertEquals(123L, JSONObject(restored.third).getLong("id"))
    }

    @Test
    fun rescueImportWithoutEncryptedPreferencesLeavesTheExistingAccountAndToken() {
        val result = RescueFiles.importSettings(context, Uri.fromFile(identityCsv())).getOrThrow()

        assertTrue(result.encryptedSkipped)
        setActiveProfile("work", context)
        assertEquals(123L, GitHubAccount.userId(context))
        assertEquals("work-token", GitHubAccount.tokenFor(context, 123))
    }

    @Test
    fun normalImportWithoutGitHubIdentityPreservesTheExistingAccountAndToken() {
        "Type,Key,Value\nString,unrelatedSetting,restored\n".byteInputStream().use {
            ImportSettings.onImport(context, it, "work")
        }

        setActiveProfile("work", context)
        assertEquals(123L, GitHubAccount.userId(context))
        assertEquals("work-token", GitHubAccount.tokenFor(context, 123))
    }

    private fun assertInactiveImport(rescue: Boolean, signedOut: Boolean) {
        if (signedOut) identities("work").edit().clear().putBoolean("signedOut", true).commit()
        val authorization = GitHubAccount.publicationAuthorization.begin("default")

        importIdentity(rescue)

        assertEquals("default", getActiveProfile(context))
        assertEquals(456L, GitHubAccount.userId(context))
        assertEquals("active", GitHubAccount.login.value)
        assertEquals("default-token", GitHubAccount.tokenFor(context, 456))
        assertEquals(authorization, GitHubAccount.publicationAuthorization.state.value?.key)
        setActiveProfile("work", context)
        assertRestoredAccount()
    }

    private fun assertRestoredAccount() {
        assertEquals(789L, GitHubAccount.userId(context))
        assertEquals("restored", GitHubAccount.accountLogin(context))
        assertNull(GitHubAccount.tokenFor(context, 789))
        assertNull(GitHubAccount.tokenFor(context, 123))
        assertFalse(identities("work").getBoolean("signedOut", false))
        assertTrue(identities("work").getBoolean("publicationTokenInvalidated", false))
        assertFalse(secrets("work").contains("githubPublicationSession"))
        assertEquals(789L, JSONObject(checkNotNull(secrets("work").getString("githubBackupIdentity", null))).getLong("id"))
    }

    private fun importIdentity(rescue: Boolean) {
        val file = identityCsv()
        if (rescue) {
            RescueFiles.importSettings(context, Uri.fromFile(file), Result.success(secrets("work"))).getOrThrow()
        } else {
            file.inputStream().use { ImportSettings.onImport(context, it, "work") }
        }
    }

    private fun identityCsv(): File = File(context.cacheDir, "github-settings-import.csv").apply {
        outputStream().use {
            RescueFiles.writeSettingsCsv(it, listOf(Triple("String", "githubBackupIdentity", restoredIdentity)))
        }
    }

    private fun seedAccount(profile: String, id: Long, login: String) {
        val identity = JSONObject().put("id", id).put("login", login).toString()
        identities(profile).edit().clear().putString("identity", identity).commit()
        val session = JSONObject().put("id", id).put("token", "$profile-token").put("expires", Long.MAX_VALUE)
        secrets(profile).edit().clear().putString("githubBackupIdentity", identity)
            .putString("githubPublicationSession", session.toString()).commit()
    }
}
