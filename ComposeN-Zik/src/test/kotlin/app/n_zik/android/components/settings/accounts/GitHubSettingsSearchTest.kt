package app.n_zik.android.components.settings.accounts

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.LocalSettingsTargets
import app.n_zik.android.components.settings.common.SettingsTargetHighlighted
import app.n_zik.android.components.settings.common.SettingsTargetRegistry
import app.n_zik.android.components.settings.common.parentCard
import app.n_zik.android.components.settings.common.searchResultTitle
import app.n_zik.android.components.settings.common.searchSettingsEntries
import app.n_zik.android.core.network.fullOfflineModeKey
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.preferences
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GitHubSettingsSearchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `GitHub search resolves the visible account actions without activating them`() {
        val signIn = GITHUB_SETTINGS_SIGN_IN_TARGET
        val signOut = GITHUB_SETTINGS_SIGN_OUT_TARGET
        val manageApp = GITHUB_SETTINGS_APP_INSTALLATION_TARGET
        val actions = mapOf(
            signIn to R.string.github_account_login,
            signOut to R.string.github_account_logout,
            manageApp to R.string.github_app_installation,
        )
        actions.forEach { (id, label) ->
            val target = searchSettingsEntries(context, context.getString(label)).single { it.id == id }
            assertEquals(label, target.labelRes)
            assertEquals(R.string.common_brand_github, target.parentCard().titleRes)
            assertNull(target.nestedDestination)
        }

        val appInstallationLabel = context.getString(R.string.github_app_installation)
        val service = context.getString(R.string.common_brand_github)
        val appInstallation = searchSettingsEntries(context, appInstallationLabel).single { it.id == manageApp }
        listOf("$service $appInstallationLabel", "$appInstallationLabel $service", appInstallation.searchResultTitle(context))
            .forEach { query -> assertTrue(searchSettingsEntries(context, query).any { it.id == manageApp }) }

        val french = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.FRENCH) },
        )
        assertTrue(searchSettingsEntries(french, "installation GitHub").any { it.id == manageApp })

        val identity = context.getSharedPreferences("github_account_profile_${getActiveProfile(context)}", Context.MODE_PRIVATE)
        identity.edit().putBoolean("signedOut", true).apply()
        context.preferences.edit().putBoolean(fullOfflineModeKey, true).apply()
        val registry = SettingsTargetRegistry()
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance, LocalSettingsTargets provides registry) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GitHubAccountCard(showAppInstallation = true)
                }
            }
        }

        composeRule.onNodeWithTag("settings.target.$signIn").assertExists()
        composeRule.onNodeWithTag("settings.target.$signOut").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.target.$manageApp").assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue(signIn in registry.targets)
            assertFalse(signOut in registry.targets)
            assertFalse(manageApp in registry.targets)
            assertNull(githubSettingsSearchFallback(signIn, registry.targets::containsKey))
            assertEquals(signIn, githubSettingsSearchFallback(signOut, registry.targets::containsKey))
            registry.highlighted = githubSettingsSearchFallback(manageApp, registry.targets::containsKey)
        }
        composeRule.onNodeWithTag("settings.target.$signIn")
            .assert(SemanticsMatcher.expectValue(SettingsTargetHighlighted, true))
        composeRule.runOnIdle {
            assertFalse(GitHubAccount.awaiting.value)
            assertNull(GitHubAccount.accountLogin(context))
            identity.edit().putString("identity", """{"id":123,"login":"creator"}""").apply()
            GitHubAccount.read(context)
        }

        composeRule.onNodeWithTag("settings.target.$signIn").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.target.$signOut").assertExists()
        composeRule.onNodeWithTag("settings.target.$manageApp").assertExists()
        composeRule.runOnIdle {
            assertFalse(signIn in registry.targets)
            assertTrue(signOut in registry.targets)
            assertTrue(manageApp in registry.targets)
            assertNull(githubSettingsSearchFallback(signOut, registry.targets::containsKey))
            assertNull(githubSettingsSearchFallback(manageApp, registry.targets::containsKey))
            registry.highlighted = signOut
        }
        composeRule.onNodeWithTag("settings.target.$signOut")
            .assert(SemanticsMatcher.expectValue(SettingsTargetHighlighted, true))
        composeRule.runOnIdle {
            registry.highlighted = manageApp
        }
        composeRule.onNodeWithTag("settings.target.$manageApp")
            .assert(SemanticsMatcher.expectValue(SettingsTargetHighlighted, true))
        composeRule.runOnIdle {
            assertEquals("creator", GitHubAccount.accountLogin(context))
            assertFalse(GitHubAccount.awaiting.value)
        }
    }
}
