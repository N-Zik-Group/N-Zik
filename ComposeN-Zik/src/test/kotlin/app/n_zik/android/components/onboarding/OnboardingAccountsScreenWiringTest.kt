package app.n_zik.android.components.onboarding

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.ytAccountName
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI test for the accounts onboarding step ([OnboardingAccountsScreen]) —
 * the exit contract: leaving the step always advances the flow ([onComplete]), and
 * the restart prompt ([RestartAppDialog]) is requested only when a Discord token is
 * set (a fresh token is picked up only on a new launch; the onboarding-complete
 * flag is left unwritten — the restart lands on the profile step).
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine
 * on the JUnit 5 platform (the `createComposeRule()` rule only works with JUnit 4),
 * with the plain [Application] so the app's heavy init (DI, Room, player) is skipped.
 * Stubs: the app-level keystore-backed [encryptedPreferences] (a JVM has no
 * keystore) and the two appContext-backed one-shot state readers — the plain test
 * Application has no initialized `Dependencies`, and the account login sheets stay
 * closed, so only the card state is composed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OnboardingAccountsScreenWiringTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    private var completed = false

    @Before
    fun setUp() {
        completed = false
        RestartAppDialog.isActive = false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Stubs the app-level keystore-backed prefs: [token] under the Discord key,
     * everything else absent (YouTube cookie, Last.fm session, …).
     */
    private fun mockEncryptedPrefs(token: String) {
        val secure = mockk<SharedPreferences>()
        every { secure.getString(any(), any()) } answers {
            if (firstArg<String>() == discordPersonalAccessTokenKey && token.isNotEmpty()) token else null
        }
        mockkStatic("app.it.fast4x.rimusic.utils.EncryptedPreferencesKt")
        every { any<Context>().encryptedPreferences } returns secure
        // The one-shot state readers are appContext-backed (Dependencies is not
        // initialized on the plain test Application)
        mockkStatic("app.it.fast4x.rimusic.ui.screens.settings.AccountsSettingsKt")
        every { isYouTubeLoggedIn() } returns false
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { ytAccountName() } returns ""
    }

    private fun showScreen() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                OnboardingAccountsScreen(onComplete = { completed = true })
            }
        }
    }

    @Test
    fun skippingWithADiscordTokenAdvancesAndRequestsTheRestart() {
        mockEncryptedPrefs("token123")
        showScreen()
        composeRule.waitForIdle()

        // At least one account connected: the button reads "I'm done"
        composeRule
            .onNodeWithText(context.getString(R.string.onboard_accounts_done))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertTrue(completed)
        assertTrue(RestartAppDialog.isActive)
    }

    @Test
    fun skippingWithoutAnyAccountAdvancesWithoutTheRestartPrompt() {
        mockEncryptedPrefs("")
        showScreen()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(context.getString(R.string.onboard_accounts_skip))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertTrue(completed)
        assertFalse(RestartAppDialog.isActive)
    }
}
