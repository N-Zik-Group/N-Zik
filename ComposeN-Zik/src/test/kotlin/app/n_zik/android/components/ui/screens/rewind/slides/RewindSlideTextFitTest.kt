package app.n_zik.android.components.ui.screens.rewind.slides

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Layout tests for the Rewind deck's text-fitting discipline
 * (spec problem-solution-2026-09-28-rewind, A6): on a narrow (w360dp) screen the hero
 * display texts must stay inside the slide, the sizing policy must visibly shrink them
 * below the calibration reference, and the shared deck components must render without
 * pushing content past the slide edge.
 *
 * JUnit 4 + [RobolectricTestRunner], same conventions as the onboarding card tests: plain
 * [Application] (the app's heavy init is skipped), `mainClock.autoAdvance = false` (the
 * marquee is draw-only and would otherwise pump frames forever), and container-node
 * assertions — the Text semantics node reports the natural width of the text even when
 * clamped, so the clamp is asserted on the container the text occupies.
 *
 * The adaptive-title test ([adaptiveTitleKeepsItsTwoLineDesignWhenItFits]) pins the
 * "two lines max" half of the rule: a title that fits in two lines keeps its design
 * line breaks (no flattening, no truncation). The overflow half (two lines full ->
 * single-line marquee fallback) is verified on device per spec A8: Robolectric's text
 * metrics are nondeterministic between runs (font-loading race — the same overflow
 * reproduces in some runs and not in others), so a forced overflow cannot be pinned
 * here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class RewindSlideTextFitTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun heroTitleIsClampedToSlideWidthOnNarrowScreen() {
        // The same wiring as the intro card's hero: the live scale from the 360dp
        // configuration, single line, ellipsis fallback, marquee honoring the "disable
        // scrolling text" setting (rewindHeroLine). The hero line must land exactly on
        // the 90% width cap. Robolectric's compressed text metrics do not reproduce the
        // real-device overflow of a long 50sp name, so this test pins the cap itself;
        // the marquee overflow behavior is covered by the manual 3-configuration
        // validation (spec A8).
        val name = "x".repeat(40)
        val density = arrayOf(Density(1f, 1f))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            density[0] = LocalDensity.current
            Box(Modifier.width(320.dp)) {
                val textScale = rememberRewindScale()
                Box(Modifier.testTag("heroLine")) {
                    Text(
                        text = name,
                        color = Color.White,
                        fontSize = textScale.size(50.sp),
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth(0.90f)
                            .rewindHeroLine()
                    )
                }
            }
        }

        val capPx = with(density[0]) { (320.dp * 0.90f).toPx() }
        val heroWidth = composeRule.onNodeWithTag("heroLine").fetchSemanticsNode().size.width

        assertTrue(
            "the hero line must be clamped to 90% of the slide content width " +
                "(got ${heroWidth}px, cap ${capPx}px)",
            heroWidth <= capPx + 2
        )
        assertTrue(
            "the clamped hero line must reach the cap instead of shrinking below it " +
                "(a width under the cap would mean the overflow did not happen at all)",
            heroWidth >= capPx - 2
        )
    }

    @Test
    fun liveScaleAtNarrowViewportShrinksBaseSizes() {
        // The scale the cards actually read (rememberRewindScale) must be built from the
        // 360dp viewport and shrink base sizes below the calibration reference — the
        // sizing invariant behind every scaled fontSize in the deck. (Robolectric's text
        // measurement does not vary with font size, so the policy value itself is the
        // observable here.)
        val captured = arrayOf<RewindTextScale?>(null)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            captured[0] = rememberRewindScale()
        }
        val scale = captured[0] ?: error("the live scale must be available in the deck composition")

        val narrow = scale.size(50.sp)
        assertTrue(
            "the live 360dp scale must shrink a 50sp hero base below the base " +
                "(got ${narrow.value}sp)",
            narrow.value < 50f
        )
        assertEquals(
            "the reference scale must return the base size exactly",
            50f,
            RewindTextScale(REWIND_TEXT_REFERENCE_WIDTH_DP, 1f).size(50.sp).value,
            0.001f
        )
        assertTrue(
            "the legibility floor must be held for a caption base (7sp): " +
                "got ${scale.size(7.sp).value}sp",
            scale.size(7.sp).value >= 7f * RewindTextTier.CAPTION.minFactor - 0.001f
        )
    }

    @Test
    fun emptyStateStaysSingleLineHeroOnNarrowScreen() {
        // The shared empty-state component (kicker + hero title + body) must render
        // inside a narrow slide with a long title, and the hero title must stay on a
        // single line instead of wrapping out of the slide.
        val title = "x".repeat(40)
        val body = "This deck has nothing to show for the selected period yet."
        val density = arrayOf(Density(1f, 1f))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            density[0] = LocalDensity.current
            val textScale = rememberRewindScale()
            Column(Modifier.width(320.dp)) {
                // Control: the empty-state's scaled hero size, an equally long text,
                // forced to one line — the empty-state title must measure like it.
                Text(
                    text = "y".repeat(40),
                    color = Color.White,
                    fontSize = textScale.size(34.sp),
                    lineHeight = textScale.size(34.sp),
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("controlLine")
                )
                RewindEmptyState(
                    title = title,
                    body = body,
                    foreground = Color.White,
                    accent = Color(0xFFC8F03C)
                )
            }
        }

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(body).assertIsDisplayed()

        val titleNode = composeRule.onNodeWithText(title).fetchSemanticsNode()
        val controlHeight = composeRule.onNodeWithTag("controlLine").fetchSemanticsNode().size.height

        assertTrue(
            "the empty-state hero title must stay on one line on a narrow screen " +
                "(title ${titleNode.size.height}px, single-line control ${controlHeight}px)",
            titleNode.size.height <= controlHeight + 2
        )
    }

    @Test
    fun adaptiveTitleKeepsItsTwoLineDesignWhenItFits() {
        // The "two lines max, then marquee" rule, first half: a title that fits in two
        // lines keeps its design line breaks — it is neither flattened into one line
        // nor cut. This holds under any text metrics (the text is far too short to
        // overflow), so it is deterministic where the overflow half is not (see the
        // class KDoc — the overflow -> marquee flip is verified on device per A8).
        val designText = "ab\ncd"
        val density = arrayOf(Density(1f, 1f))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            density[0] = LocalDensity.current
            val textScale = rememberRewindScale()
            Column(Modifier.width(320.dp)) {
                // Control: one line at the same scaled size.
                Text(
                    text = "ef",
                    color = Color.White,
                    fontSize = textScale.size(34.sp),
                    lineHeight = textScale.size(34.sp),
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier.testTag("oneLineControl")
                )
                RewindAdaptiveTitle(
                    text = designText,
                    color = Color.White,
                    fontSize = textScale.size(34.sp),
                    lineHeight = textScale.size(34.sp),
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.testTag("adaptiveTitle")
                )
            }
        }
        composeRule.waitForIdle()

        val adaptiveNode = composeRule.onNodeWithTag("adaptiveTitle").fetchSemanticsNode()
        val controlHeight = composeRule.onNodeWithTag("oneLineControl").fetchSemanticsNode().size.height

        // The design line break must survive (no flattening, no truncation).
        val rendered = adaptiveNode.config[SemanticsProperties.Text]?.firstOrNull()?.text.orEmpty()
        assertTrue(
            "the adaptive title must keep its design line breaks when the text fits " +
                "(rendered '${rendered.replace("\n", "\\n")}')",
            rendered == designText
        )
        val adaptiveHeight = adaptiveNode.size.height
        assertTrue(
            "the adaptive title must occupy exactly its two design lines, no more " +
                "(got ${adaptiveHeight}px, one line ${controlHeight}px)",
            adaptiveHeight > controlHeight + 2 && adaptiveHeight <= controlHeight * 2 + 2
        )
    }
}
