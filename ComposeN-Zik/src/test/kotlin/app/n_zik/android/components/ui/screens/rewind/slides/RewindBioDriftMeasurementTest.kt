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
 * Layout-semantics tests for the artist-bio auto-drift's overflow measurement
 * ([RewindTopArtistSpotlightCard]).
 *
 * The drift measures its travel distance from the rendered geometry: the clipping
 * block's window-space bottom vs. the bio's LAST element's window-space bottom, via
 * [onGloballyPositioned] + `LayoutCoordinates.localToWindow` (the window position
 * accessors — `positionInRoot` / `positionInWindow` — are gone in Compose 1.12).
 *
 * In Compose 1.12 a Column capped by a bounded constraint SQUASHES its children
 * instead of letting them overflow (each child is measured with the remaining main-axis
 * space), so the last element's bottom never goes past the column's top + max — the
 * overflow would always measure as zero and the drift would never start. The bio
 * column therefore carries `wrapContentHeight(align = Top, unbounded = true)`: the only
 * case where the content is measured past the incoming max constraint. These tests pin
 * that invariant with fixed-size boxes (no text metrics — fully deterministic), so a
 * future Compose behavior change fails loudly instead of silently stopping the drift.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class RewindBioDriftMeasurementTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unboundedColumnKeepsItsChildrenAtNaturalOffsetsWhenItOverflows() {
        // Mimics the drift's measurement: a capped block (the clipping Box) containing
        // a top-aligned unbounded Column whose total child height (60 + 80 = 140dp)
        // exceeds the block (100dp). The last element must land at its natural offset
        // (140dp from the block top), i.e. 40dp below the block's bottom — that 40dp is
        // the drift's travel distance.
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
        // The counterfactual the drift must never fall into: a plain capped Column
        // squashes its children to the remaining space, so the last element's bottom
        // stays on the block's bottom and the overflow would measure as zero — the
        // exact failure that kept the drift from ever starting on device.
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
        // A bio that fits must not report any overflow: the last element's bottom
        // stays at or above the block's bottom, so the drift guard (overflow <= 0)
        // keeps the text still — nothing moves, exactly as before.
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
