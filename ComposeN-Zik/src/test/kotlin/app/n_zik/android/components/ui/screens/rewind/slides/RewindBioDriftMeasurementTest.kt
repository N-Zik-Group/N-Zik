package app.n_zik.android.components.ui.screens.rewind.slides

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Layout-semantics tests for the artist-bio overflow measurement
 * ([RewindTopArtistSpotlightCard]).
 *
 * The bio's overflow detection (the "SEE MORE" pill decision — the auto-drift has been
 * removed) compares two measurements: the bio area's height and the bio's NATURAL
 * height, read from a hidden full-text copy inside the same box. That hidden copy
 * must be measured past the box's bounded constraint, via
 * `wrapContentHeight(align = Top, unbounded = true)` — these tests pin the same
 * [onGloballyPositioned]-based geometry invariant with fixed-size boxes (no text
 * metrics — fully deterministic), so a future Compose behavior change fails loudly
 * instead of silently reporting zero overflow.
 *
 * Background (why the unbounded measurement is mandatory): in Compose 1.12 a Column
 * capped by a bounded constraint SQUASHES its children instead of letting them
 * overflow (each child is measured with the remaining main-axis space), so a hidden
 * copy measured the naive way would always read as fitting and the SEE MORE pill
 * would never appear.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class RewindBioDriftMeasurementTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unboundedColumnKeepsItsChildrenAtNaturalOffsetsWhenItOverflows() {
        // Mimics the bio's hidden-copy measurement: a capped block (the bio area Box)
        // containing a top-aligned unbounded Column whose total child height
        // (60 + 80 = 140dp) exceeds the block (100dp). The last element must land at
        // its natural offset (140dp from the block top), i.e. 40dp below the block's
        // bottom — that 40dp is the natural overflow the SEE MORE decision reads.
        val blockBottom = arrayOf(0)
        val lastElementBottom = arrayOf(0)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .onGloballyPositioned { coords ->
                        blockBottom[0] =
                            coords.localToWindow(Offset(0f, coords.size.height.toFloat())).y.toInt()
                    }
            ) {
                Column(
                    modifier = Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true)
                ) {
                    Box(Modifier.height(60.dp))
                    Box(
                        modifier = Modifier
                            .height(80.dp)
                            .onGloballyPositioned { coords ->
                                lastElementBottom[0] =
                                    coords.localToWindow(Offset(0f, coords.size.height.toFloat())).y.toInt()
                            }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(
            "the unbounded column's last element must be placed at its natural offset " +
                "even though the block is capped (block bottom ${blockBottom[0]}px, last " +
                "element bottom ${lastElementBottom[0]}px, expected 40dp overflow at density 1.0)",
            40,
            lastElementBottom[0] - blockBottom[0]
        )
    }

    @Test
    fun cappedColumnSquashesItsLastChildInsteadOfOverflowing() {
        // The counterfactual the hidden copy must never fall into: a plain capped
        // Column squashes its children to the remaining space, so the last element's
        // bottom stays on the block's bottom and the overflow would measure as zero —
        // the exact failure that would keep the SEE MORE pill from ever appearing.
        val blockBottom = arrayOf(0)
        val lastElementBottom = arrayOf(0)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .onGloballyPositioned { coords ->
                        blockBottom[0] =
                            coords.localToWindow(Offset(0f, coords.size.height.toFloat())).y.toInt()
                    }
            ) {
                Column {
                    Box(Modifier.height(60.dp))
                    Box(
                        modifier = Modifier
                            .height(80.dp)
                            .onGloballyPositioned { coords ->
                                lastElementBottom[0] =
                                    coords.localToWindow(Offset(0f, coords.size.height.toFloat())).y.toInt()
                            }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        assertTrue(
            "a capped column squashes its last child instead of overflowing, so its " +
                "bottom never goes past the block's bottom " +
                "(block bottom ${blockBottom[0]}px, last element bottom ${lastElementBottom[0]}px)",
            lastElementBottom[0] - blockBottom[0] <= 0
        )
    }

    @Test
    fun overflowIsZeroWhenTheContentFitsTheBlock() {
        // A bio that fits must not report any overflow: the hidden copy's bottom
        // stays at or above the block's bottom, so the overflow decision
        // (natural > area) stays false — the pill never appears, the text is
        // rendered in full with no clamp.
        val blockBottom = arrayOf(0)
        val lastElementBottom = arrayOf(0)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .onGloballyPositioned { coords ->
                        blockBottom[0] =
                            coords.localToWindow(Offset(0f, coords.size.height.toFloat())).y.toInt()
                    }
            ) {
                Column(
                    modifier = Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true)
                ) {
                    Box(Modifier.height(60.dp))
                    Box(
                        modifier = Modifier
                            .height(80.dp)
                            .onGloballyPositioned { coords ->
                                lastElementBottom[0] =
                                    coords.localToWindow(Offset(0f, coords.size.height.toFloat())).y.toInt()
                            }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        assertTrue(
            "content that fits must not report overflow " +
                "(block bottom ${blockBottom[0]}px, last element bottom ${lastElementBottom[0]}px)",
            lastElementBottom[0] <= blockBottom[0]
        )
    }
}
