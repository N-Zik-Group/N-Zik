package app.n_zik.android.legacyoffmain.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.screens.settings.About
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Wiring test for the cleaned About tab (spec-settings-global-page, row
 * ABOUT_CLEAN): the update elements (version display, last-check date,
 * check-for-update button) are gone from the tab, while the app info
 * (author line), the troubleshooting card and the contributors card stay
 * unchanged.
 *
 * JUnit 4 + [RobolectricTestRunner] through the project's junit-vintage-engine
 * on the JUnit 5 platform (`createComposeRule()` only works with JUnit 4),
 * with the plain [Application] so the app's heavy init is skipped. The tab's
 * cards use finite entry animations, so the test settles with `waitForIdle()`.
 */
@OptIn(ExperimentalAnimationApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class AboutCleanWiringTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val navController = mockk<NavController>()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @Before
    fun setUp() {
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns context
        Dependencies.init(mainApplication)
    }

    private fun noNodeWithTextExactly(text: String): Boolean =
        composeRule
            .onAllNodesWithText(text, substring = false, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isEmpty()

    @Test
    fun theUpdateBlockIsRemovedFromTheAboutTab() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                About(navController = navController)
            }
        }
        composeRule.waitForIdle()

        assertTrue(
            "the check-for-update button must be gone",
            noNodeWithTextExactly(context.getString(R.string.check_update))
        )
        assertTrue(
            "the last-check status must be gone",
            noNodeWithTextExactly(context.getString(R.string.never_checked))
        )
        assertTrue(
            "the update card title must be gone",
            noNodeWithTextExactly(context.getString(R.string.update))
        )
    }

    @Test
    fun theAppInfoTroubleshootingAndContributorsStay() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                About(navController = navController)
            }
        }
        composeRule.waitForIdle()

        // App info: the author line (app name + icon are drawn above it in the
        // same card).
        composeRule
            .onNodeWithText(context.getString(R.string.by_string), substring = false, useUnmergedTree = true)
            .fetchSemanticsNode()
        // Troubleshooting and contributors cards keep their headers.
        composeRule
            .onNodeWithText(context.getString(R.string.troubleshooting), substring = false, useUnmergedTree = true)
            .fetchSemanticsNode()
        composeRule
            .onNodeWithText(context.getString(R.string.contributors), substring = false, useUnmergedTree = true)
            .fetchSemanticsNode()
    }
}
