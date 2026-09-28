package app.n_zik.android.components.onboarding

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the shared onboarding card ([OnboardingActionCard]) and its
 * overflow handling on small screens / enlarged fonts.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine
 * on the JUnit 5 platform (the `createComposeRule()` rule only works with JUnit 4),
 * with the plain [Application] so the app's heavy init (DI, Room, player) is skipped.
 * Narrow-screen qualifiers (w360dp) approximate a small phone viewport.
 *
 * The marquee itself is draw-only (BasicMarquee animates the drawing offset, not the
 * layout), so it is not observable through layout in a unit test — the tests assert the
 * layout invariants instead: action labels are clamped to [ONBOARDING_ACTION_LABEL_MAX_WIDTH_DP]
 * so they can never push the card past the screen edge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class OnboardingActionCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    private fun content(block: @Composable () -> Unit) {
        // Overflow labels run a marquee animation: drive the clock manually so no frame
        // is pumped and the tests never wait on the animation to settle.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                block()
            }
        }
    }

    @Test
    fun longActionLabelIsClampedToMaxWidth() {
        // Robolectric measures text at ~1px/char (compressed default font), so the label
        // must be longer than the cap in characters to overflow it; on device any real
        // long translation overflows at far fewer characters.
        val label = "x".repeat(150)
        val density = arrayOf(Density(1f, 1f))
        content {
            density[0] = LocalDensity.current
            // The Text semantics node reports the full natural width of the text even when
            // clamped (the clamp is a layout effect), so assert the clamp on the container
            // the label occupies — the same convention as the header badge tests.
            Box(Modifier.testTag("labelContainer")) {
                OnboardingActionLabel(label)
            }
        }

        val capPx = with(density[0]) { ONBOARDING_ACTION_LABEL_MAX_WIDTH_DP.dp.toPx() }
        val containerWidth = composeRule.onNodeWithTag("labelContainer").fetchSemanticsNode().size.width
        val textWidth = composeRule.onNodeWithText(label).fetchSemanticsNode().size.width

        assertTrue(
            "the label must actually overflow the cap in this test " +
                "(natural text width ${textWidth}px, cap ${capPx}px)",
            textWidth > capPx
        )
        assertTrue(
            "a label wider than the cap must be clamped to ${ONBOARDING_ACTION_LABEL_MAX_WIDTH_DP}dp " +
                "(container got ${containerWidth}px, cap ${capPx}px)",
            containerWidth <= capPx + 2
        )
        assertTrue(
            "the clamped label must reach the cap instead of shrinking below it",
            containerWidth >= capPx - 2
        )
    }

    @Test
    fun shortActionLabelKeepsContentWidth() {
        val label = "OK"
        val density = arrayOf(Density(1f, 1f))
        content {
            density[0] = LocalDensity.current
            OnboardingActionLabel(label)
        }

        val labelWidth = composeRule.onNodeWithText(label).fetchSemanticsNode().size.width
        val maxWidthPx = with(density[0]) { ONBOARDING_ACTION_LABEL_MAX_WIDTH_DP.dp.toPx() }

        assertTrue(
            "a short label must stay content-width (no visible change on a normal screen)",
            labelWidth < maxWidthPx - 4
        )
    }

    @Test
    fun cardShowsTitleAndActionAtNarrowWidth() {
        val longTitle = "Autorisation de notifications push et alarmes"
        val actionLabel = "Paramètres"
        content {
            OnboardingActionCard(
                icon = R.drawable.checkmark,
                title = longTitle,
                description = "Description longue qui doit simplement wraper sur plusieurs lignes " +
                    "sans jamais pousser la carte hors de l'écran.",
                action = {
                    Button(onClick = { }) {
                        OnboardingActionLabel(actionLabel)
                    }
                }
            )
        }

        composeRule.onNodeWithText(longTitle).assertIsDisplayed()
        composeRule.onNodeWithText(actionLabel).assertIsDisplayed()
    }

    @Test
    fun sectionCardShowsTitle() {
        content {
            OnboardingSectionCard(title = "Autorisations")
        }

        composeRule.onNodeWithText("Autorisations").assertIsDisplayed()
    }
}
