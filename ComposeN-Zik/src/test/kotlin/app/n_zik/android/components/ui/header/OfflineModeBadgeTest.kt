package app.n_zik.android.components.ui.header

import android.app.Application
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI test for [OfflineModeBadge] (spec-full-offline-mode wave 2, rework): the
 * airplane badge is composed only while the offline mode flag is on, glides in from
 * under the logo on enable, and stays composed — fading out to the right — until the
 * exit animation finishes before leaving the tree on disable.
 *
 * The flag is driven as a composable parameter: the production caller
 * (CollapsingAppTitle) feeds it from OfflineMode.rememberEnabled(), whose preference
 * wiring is already covered by the wave-1 suites.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine
 * on the JUnit 5 platform (the `createComposeRule()` rule only works with JUnit 4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = OfflineModeBadgeTestApplication::class)
class OfflineModeBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    private val badgeDescription = "Full offline mode"

    /**
     * Advances the frozen frame clock past every badge animation (400ms entry) and settles.
     * A state write from the test body is recomposed during the idle pass, not during the
     * clock advance — so the advance and the idle must alternate: the idle pass processes
     * the recomposition, the next advance gives the animation the frames it needs.
     */
    private fun settle() {
        repeat(3) {
            composeRule.mainClock.advanceTimeBy(200)
            composeRule.waitForIdle()
        }
    }

    private val badgeNode get() = composeRule.onNodeWithContentDescription(badgeDescription)

    @Test
    fun `badge is absent while the mode is off`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                OfflineModeBadge(offlineMode = false, enterTravelPx = 200)
            }
        }
        settle()
        badgeNode.assertDoesNotExist()
    }

    @Test
    fun `badge glides in from under the logo when the mode is enabled`() {
        var modeOn by mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                OfflineModeBadge(offlineMode = modeOn, enterTravelPx = 200)
            }
        }
        settle()
        badgeNode.assertDoesNotExist()

        modeOn = true
        settle()
        badgeNode.assertIsDisplayed()
    }

    @Test
    fun `badge stays composed while fading out and leaves the tree when the mode is disabled`() {
        var modeOn by mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                OfflineModeBadge(offlineMode = modeOn, enterTravelPx = 200)
            }
        }
        settle()
        badgeNode.assertIsDisplayed()

        modeOn = false
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.waitForIdle()
        // mid-exit: the fade is progressive, the badge is still in the tree
        badgeNode.assertExists()

        composeRule.mainClock.advanceTimeBy(600)
        composeRule.waitForIdle()
        badgeNode.assertDoesNotExist()
    }
}

/** Test-only application: the app's MainApplication.onCreate() is not available on the Robolectric JVM. */
class OfflineModeBadgeTestApplication : Application()
