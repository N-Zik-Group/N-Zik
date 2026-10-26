package app.n_zik.android.legacyoffmain.ui.screens.settings

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.NavRoutes
import app.n_zik.android.components.settings.data.DataSettings
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Wiring test for the burger menu's History deep link (spec-customizable-burger-menu,
 * goal B): the legacy [DataSettings] screen opened with `focus=history` must scroll its
 * History card into view, without focus the screen must stay at the top, and the card's
 * "Open" row must navigate to the full History screen through the real screen.
 *
 * JUnit 4 + [RobolectricTestRunner] through the project's junit-vintage-engine on the JUnit 5
 * platform (`createComposeRule()` only works with JUnit 4), with the plain [Application] so
 * the app's heavy init is skipped. The Compose main clock is FROZEN and pumped by hand
 * ([pumpUntil]): in this harness the cards' AnimatedVisibility entry animations only progress
 * while the focus scroll animation is running (verified with probes and a continuous
 * withFrameNanos canary: with `focus=""` the cards stay zero-height no matter how long the
 * clock is pumped), so any bounds assertion on an animated card is only possible in the
 * focus case, where the pump drives the scroll to completion. Static (non-animated) nodes
 * are laid out immediately, and `performClick` reaches the rows through semantics without a
 * viewport pass, so the no-focus and Open-row tests need no pump at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class BurgerMenuFocusScrollTest {

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
        composeRule.mainClock.autoAdvance = false
        // DataSettings reads app-wide state (image cache) through the locator, so wire a
        // plain mock app: the test must not depend on a sibling test having initialized it.
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns context
        Dependencies.init(mainApplication)
    }

    /**
     * Drives the frozen Compose clock and the Robolectric main looper until [condition]
     * holds (or [timeoutMs] elapses). No `waitForIdle` in the loop: on a frozen clock a
     * pending entry animation keeps it from ever settling, so the loop would block forever
     * - advancing the clock IS the pump. [label] is reported on timeout.
     */
    private fun pumpUntil(label: String, timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.mainClock.advanceTimeBy(250)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            if (condition()) return
        }
        assertTrue("[$label] not met within ${timeoutMs}ms", condition())
    }

    @OptIn(ExperimentalAnimationApi::class, UnstableApi::class)
    private fun showDataSettings(focus: String = "") {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                DataSettings(navController = navController, focus = focus)
            }
        }
    }

    /** The card title is the only node with exactly "History" (the Open row says "Open the History screen"). */
    private val historyCardTitle: androidx.compose.ui.test.SemanticsNodeInteraction
        get() = composeRule.onNodeWithText(
            context.getString(R.string.history),
            substring = false,
            useUnmergedTree = true
        )

    /**
     * The clickable ancestor of the Open row text (the row Surface, unmerged tree). The
     * framework returns fresh SemanticsNode instances per fetch (no stable id is exposed),
     * so the row is matched as the unique clickable whose bounds contain the text's bounds.
     */
    private fun openRowClickable(): androidx.compose.ui.test.SemanticsNodeInteraction {
        val textBounds = composeRule
            .onNodeWithText(context.getString(R.string.history_open_desc), substring = false, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        return composeRule.onNode(
            androidx.compose.ui.test.SemanticsMatcher("the History Open row") { n ->
                androidx.compose.ui.test.hasClickAction().matches(n) &&
                    n.boundsInRoot.left <= textBounds.left &&
                    textBounds.right <= n.boundsInRoot.right &&
                    n.boundsInRoot.top <= textBounds.top &&
                    textBounds.bottom <= n.boundsInRoot.bottom
            },
            useUnmergedTree = true
        )
    }

    @Test
    fun withoutFocusTheScreenStaysAtTheTop() {
        // The scroll is focus-gated in DataSettings (focus != "history" returns early), so
        // without focus the screen opens at the top and nothing scrolls. Static
        // (non-animated) nodes are laid out without a frame pump, which makes the
        // description text a reliable no-scroll anchor: the animated cards stay zero-height
        // in this harness without the scroll effect, so they cannot be measured here.
        showDataSettings(focus = "")

        val top = composeRule
            .onNodeWithText(context.getString(R.string.data_settings_description))
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue(
            "without focus the screen must stay at the top (desc top=$top)",
            top in 0f..120f
        )
    }

    @Test
    fun theHistoryFocusScrollsTheCardIntoView() {
        // The burger History item deep-links to settings?tab=4&focus=history; the screen
        // must scroll until the History card is in view (production targets the card top to
        // the container top, so the title ends up just below y=0).
        showDataSettings(focus = "history")
        pumpUntil(
            "the focused History card is scrolled into the viewport",
            30_000
        ) {
            val b = try {
                historyCardTitle.fetchSemanticsNode().boundsInRoot
            } catch (e: AssertionError) {
                null
            }
            b != null && b.height > 0f && b.top in 0f..640f
        }

        val bounds = historyCardTitle.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "the focused History card must be inside the 640px viewport (bounds=$bounds)",
            bounds.top in 0f..640f
        )
    }

    @Test
    fun theHistoryOpenRowNavigatesToTheHistoryScreen() {
        every { navController.navigate(any<String>()) } just runs
        // The real deep-link path: the burger History item opens the screen with
        // focus=history, which in this harness also drives the card's entry animation
        // (see class KDoc) - the row only becomes click-targetable once it is laid out.
        showDataSettings(focus = "history")
        pumpUntil("the History Open row is laid out", 30_000) {
            try {
                composeRule
                    .onNodeWithText(context.getString(R.string.history_open_desc), substring = false)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .height > 0f
            } catch (e: AssertionError) {
                false
            }
        }

        // Dispatch through the semantics action instead of coordinates: the scroll may
        // still be settling when the row first lays out, and a coordinate tap on a moving
        // target misses (same pattern as ArtistRoundnessSearchGateTest).
        openRowClickable().performSemanticsAction(SemanticsActions.OnClick)

        verify { navController.navigate(NavRoutes.history.name) }
    }
}
