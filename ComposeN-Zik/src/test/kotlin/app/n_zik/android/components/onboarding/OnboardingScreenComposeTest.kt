package app.n_zik.android.components.onboarding

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.components.glass.glassEnabledKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.enums.OnboardingStep
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Layout test for the onboarding permissions step ([OnboardingScreen]) - the
 * "onboarding content under the system nav bar" fix: the step composes with its CTA
 * displayed, and when the permission cards overflow the viewport the whole step
 * scrolls and the CTA stays reachable (it is the last child, so it carries the nav
 * inset on device and is never clipped or covered by the system nav buttons).
 *
 * JUnit 4 + [RobolectricTestRunner] through the project's junit-vintage-engine on the JUnit 5
 * platform (`createComposeRule()` only works with JUnit 4), with the plain [Application] so
 * the app's heavy init (DI, Room, player) is skipped. The [LocalLifecycleOwner] the step
 * observes (ON_RESUME refresh of the card states) is provided from a [LifecycleRegistry]
 * kept at RESUMED.
 *
 * Harness limitation: Robolectric reports zero window insets, so the nav-bar bleed itself
 * (content under the transparent system nav bar) cannot be asserted here - the
 * `navigationBarsPadding` on the last child is a no-op in this environment. The inset
 * behavior is verified on-device (fresh install, run all four steps).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class OnboardingScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app: Context = ApplicationProvider.getApplicationContext()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    // One owner per test instance: stable across recompositions (a fresh registry on
    // every composition would re-fire the step's ON_RESUME refresh on each re-compose)
    private val lifecycleOwner = resumedLifecycleOwner()

    @After
    fun tearDown() {
        app.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .remove(DataStoreUtils.KEY_ONBOARDING_PERMISSIONS_REQUESTED)
            .commit()
    }

    /** A [LifecycleOwner] parked at RESUMED, for the step's ON_RESUME card refresh. */
    private fun resumedLifecycleOwner(): LifecycleOwner {
        lateinit var registry: LifecycleRegistry
        val owner = object : LifecycleOwner {
            override val lifecycle: Lifecycle get() = registry
        }
        registry = LifecycleRegistry(owner)
        registry.currentState = Lifecycle.State.RESUMED
        return owner
    }

    private fun content(block: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalLifecycleOwner provides lifecycleOwner
            ) {
                block()
            }
        }
    }

    @Test
    fun `post-import Glass screen offers persisted choice and advances to accounts`() {
        app.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        val step = mutableStateOf(OnboardingStep.IMPORT.advance())
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_ONBOARDING_COMPLETE, false)
        content {
            if (step.value == OnboardingStep.GLASS) {
                OnboardingGlassScreen(onComplete = {
                    val next = requireNotNull(step.value?.advance())
                    DataStoreUtils.saveString(app, DataStoreUtils.KEY_ONBOARDING_STEP, next.name)
                    step.value = next
                })
            } else {
                androidx.compose.material3.Text("Accounts step")
            }
        }
        composeRule.onNodeWithTag("settings.glass.preview").assertIsDisplayed()
        val choice = composeRule.onNodeWithTag("settings.glass.master")
        choice.assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertFalse(app.preferences.getBoolean(glassEnabledKey, true))
        choice.performClick()
        composeRule.onNodeWithText(app.getString(R.string.glass_enable_warning_title)).assertIsDisplayed()
        assertFalse("requesting enable must wait for confirmation", app.preferences.getBoolean(glassEnabledKey, true))
        composeRule.onNodeWithText(app.getString(R.string.glass_enable_confirm)).performClick()
        composeRule.waitForIdle()
        assertTrue(app.preferences.getBoolean(glassEnabledKey, false))
        composeRule.onNodeWithTag("onboarding.glass.continue").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Accounts step").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding.glass").assertDoesNotExist()
        assertEquals("ACCOUNTS", DataStoreUtils.getString(app, DataStoreUtils.KEY_ONBOARDING_STEP, ""))
        assertFalse(DataStoreUtils.getBoolean(app, DataStoreUtils.KEY_ONBOARDING_COMPLETE, false))
    }

    @Test
    fun thePermissionsStepComposesAndTheCtaIsDisplayed() {
        content { OnboardingScreen(onComplete = {}) }

        // The step header renders right away
        composeRule.onNodeWithText(app.getString(R.string.onboard_greeting)).assertIsDisplayed()

        // The CTA sits below the permission cards: scroll it into view (the step
        // scrolls instead of clipping) and assert it displays
        composeRule
            .onNodeWithText(app.getString(R.string.onboard_start))
            .performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(app.getString(R.string.onboard_start)).assertIsDisplayed()
    }
}
