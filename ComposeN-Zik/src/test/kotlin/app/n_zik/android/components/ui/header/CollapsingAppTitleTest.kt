package app.n_zik.android.components.ui.header

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [CollapsingAppTitle], the header title row that collapses its
 * sliding extras ("N-ZIK" text + badges) behind the app logo when the header runs out
 * of horizontal space.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine
 * on the JUnit 5 platform (the `createComposeRule()` rule only works with JUnit 4), with
 * the plain [TestApplication] so the app's heavy init is skipped — the logo bitmap only
 * needs an application context, wired through [Dependencies] as in MediaItemUtilsTest.
 *
 * The frame clock is driven manually (autoAdvance off) so the collapse animation is
 * advanced deterministically to its end state before assertions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class CollapsingAppTitleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    private val navController = mockk<NavController>(relaxed = true)

    @Before
    fun initDependencies() {
        val app = ApplicationProvider.getApplicationContext<TestApplication>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
    }

    // ------------------------------------------------------------------
    // Pure threshold logic
    // ------------------------------------------------------------------

    @Test
    fun `title stays expanded when the available width covers the ideal width`() {
        assertFalse(shouldCollapseTitle(availablePx = 300, idealPx = 280, tolerancePx = 8))
    }

    @Test
    fun `title collapses when the available width is below the ideal width minus tolerance`() {
        assertTrue(shouldCollapseTitle(availablePx = 200, idealPx = 280, tolerancePx = 8))
    }

    @Test
    fun `title stays expanded inside the tolerance band`() {
        // Subpixel jitter at the fit boundary must not trigger the collapse
        assertFalse(shouldCollapseTitle(availablePx = 276, idealPx = 280, tolerancePx = 8))
    }

    @Test
    fun `unmeasured ideal width never collapses`() {
        assertFalse(shouldCollapseTitle(availablePx = 0, idealPx = 0, tolerancePx = 8))
    }

    // ------------------------------------------------------------------
    // Layout behavior
    // ------------------------------------------------------------------

    @Test
    fun `extras stay expanded when the header has enough width`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val context = LocalContext.current
            CompositionLocalProvider(LocalAppearance provides appearance) {
                // Use a constrained box so the weight modifier gets finite constraints.
                Box(modifier = Modifier.size(300.dp, 64.dp)) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        CollapsingAppTitle(
                            navController = navController,
                            context = context,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Let the animation settle
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()

        // The sliding extras keep their full natural width when the header fits them
        composeRule.onNodeWithText("N-ZIK").assertIsDisplayed()
        val extrasWidth = composeRule.onNodeWithTag("appTitleExtras").fetchSemanticsNode().size.width
        assertTrue(
            "the sliding extras keep their full measured width when the header fits them (width=$extrasWidth)",
            extrasWidth > 0
        )
    }

    @Test
    fun `extras collapse behind the logo when the header is too narrow`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val context = LocalContext.current
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Box(modifier = Modifier.size(200.dp, 64.dp)) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Stand-in for the animated back button (48dp)
                        Box(modifier = Modifier.size(48.dp, 48.dp))
                        CollapsingAppTitle(
                            navController = navController,
                            context = context,
                            modifier = Modifier.weight(1f)
                        )
                        // Stand-in for the action bar (search + burger = 96dp)
                        Box(modifier = Modifier.size(96.dp, 48.dp))
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()

        val extrasWidth = composeRule.onNodeWithTag("appTitleExtras").fetchSemanticsNode().size.width
        assertTrue(
            "the sliding extras must fully retract so the action bar keeps its space",
            extrasWidth == 0
        )
        composeRule.onNodeWithText("N-ZIK").assertIsNotDisplayed()
        // The logo keeps its full size — it is never compressed
        val logoWidth = composeRule.onNodeWithTag("appTitleLogo").fetchSemanticsNode().size.width
        val expectedLogoPx = with(composeRule.density) { 36.dp.toPx().toInt() }
        assertTrue(
            "the logo must stay at its full 36dp width even while collapsed (expected=$expectedLogoPx, actual=$logoWidth)",
            logoWidth == expectedLogoPx
        )
        composeRule.onNodeWithTag("appTitleLogo").assertIsDisplayed()
    }
}

/**
 * Test-only application: MainApplication.onCreate() migrates credentials through
 * AndroidKeyStore (MasterKey), which is not available on the Robolectric JVM.
 */
class TestApplication : Application()
