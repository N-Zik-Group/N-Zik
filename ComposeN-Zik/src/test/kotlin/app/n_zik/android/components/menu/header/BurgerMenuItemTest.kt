package app.n_zik.android.components.menu.header

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [BurgerMenuItem], the shared hamburger-menu row
 * (spec-customizable-burger-menu, LONG_PRESS acceptance row): a short tap fires the feature
 * callback and then the consume callback (the menu closes); a long press fires the deep-link
 * callback WITHOUT consuming (the caller closes the menu inside the long-press handler); the
 * no-consume variant (offline mode / PiP items) leaves the menu open after a short tap. A
 * label longer than the "Rescue Center" reference width keeps its full intrinsic width
 * under the marquee (scrolling, no ellipsis), and is clamped to that width when the global
 * "disable scrolling text" setting is on (ellipsis instead).
 *
 * JUnit 4 + [RobolectricTestRunner] through the project's junit-vintage-engine on the JUnit 5
 * platform (`createComposeRule()` only works with JUnit 4), with the plain [Application] so the
 * app's heavy init (DI, Room, player) is skipped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BurgerMenuItemTest {

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

    private class Counters {
        var shortTaps = 0
        var longTaps = 0
        var consumes = 0
    }

    private fun showItem(counters: Counters, consumeOnShortTap: Boolean) {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                BurgerMenuItem(
                    iconRes = R.drawable.history,
                    label = "History",
                    onShortTap = { counters.shortTaps++ },
                    onLongTap = { counters.longTaps++ },
                    onConsume = if (consumeOnShortTap) {
                        { counters.consumes++ }
                    } else {
                        { }
                    }
                )
            }
        }
    }

    @Test
    fun shortTapFiresTheFeatureThenConsumes() {
        val counters = Counters()
        showItem(counters, consumeOnShortTap = true)

        composeRule.onNodeWithText("History").performClick()

        assertEquals(1, counters.shortTaps)
        assertEquals(0, counters.longTaps)
        assertEquals(1, counters.consumes)
    }

    @Test
    fun longTapFiresTheDeepLinkWithoutConsuming() {
        val counters = Counters()
        showItem(counters, consumeOnShortTap = true)

        // The ui-test API of this Compose version has no performLongClick(): inject a real
        // long-press touch gesture (held past the long-press timeout) on the node center.
        composeRule.onNodeWithText("History").performTouchInput { longClick() }
        composeRule.waitForIdle()

        assertEquals(0, counters.shortTaps)
        assertEquals(1, counters.longTaps)
        assertEquals(0, counters.consumes)
    }

    @Test
    fun shortTapWithoutConsumeKeepsTheMenuOpen() {
        // The offline-mode / PiP items pass no onConsume: the menu must stay open after
        // the short tap (re-toggle in place).
        val counters = Counters()
        showItem(counters, consumeOnShortTap = false)

        composeRule.onNodeWithText("History").performClick()

        assertEquals(1, counters.shortTaps)
        assertEquals(0, counters.consumes)
    }

    // The label longer than the "Rescue Center" reference width of the burger menu.
    private val longLabel = "A deliberately very long burger menu label that must not widen the row"

    /**
     * Composes the clamped item plus the width reference (the "Rescue Center" label in
     * the menu's own typography - what clampedLabelMaxWidth measures). The clock is
     * frozen: an overflowing label runs an infinite marquee, which keeps a
     * self-advancing clock from ever settling.
     */
    private fun showClampedItem() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Column {
                    BurgerMenuItem(
                        iconRes = R.drawable.history,
                        label = longLabel,
                        onShortTap = {},
                        onLongTap = {},
                    )
                    Text(context.getString(R.string.rescue_center), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** The inner Text node (NOT the merged row, which always fills the width). */
    private fun innerLabelWidth(): Float =
        composeRule
            .onNodeWithText(longLabel, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .width

    private fun referenceWidth(): Float =
        composeRule
            .onNodeWithText(context.getString(R.string.rescue_center), useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .width

    @Test
    fun aLongLabelScrollsInAMarqueeInsteadOfWideningTheRow() {
        showClampedItem()

        // With the marquee on, the label is measured unbounded: the inner text keeps its
        // full intrinsic width (no ellipsis) instead of being clamped to the reference.
        val inner = innerLabelWidth()
        val reference = referenceWidth()
        assertTrue(
            "with the marquee on, the long label must keep its full width (inner=$inner, reference=$reference)",
            inner > reference
        )
    }

    @Test
    fun aLongLabelIsClampedToTheReferenceWidthWhenScrollingTextIsDisabled() {
        context.preferences.edit().putBoolean(disableScrollingTextKey, true).commit()
        showClampedItem()

        // With scrolling text disabled, the label is measured against the reference width
        // and clamped (ellipsis) instead of marquee-scrolling.
        val inner = innerLabelWidth()
        val reference = referenceWidth()
        assertTrue(
            "with scrolling text disabled, the label must be clamped to the reference width (inner=$inner, reference=$reference)",
            inner <= reference
        )
    }

    @After
    fun cleanUp() {
        context.preferences.edit().remove(disableScrollingTextKey).commit()
    }
}
