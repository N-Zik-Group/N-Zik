package app.n_zik.android.components.ui.header

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the shared header badge container ([HeaderBadgeBox]), used by both
 * the version badge ([HeaderVersionBadge]) and the debug badge ([DebugLogsBadge]).
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine
 * on the JUnit 5 platform (the `createComposeRule()` rule only works with JUnit 4),
 * with the plain [Application] so the app's heavy init (DI, Room, player) is skipped.
 *
 * Covers the spec's badge I/O matrix rows: LONG_LABEL (clamped to the reference width —
 * the marquee is draw-only and finite, not observable through layout in a unit test),
 * SHORT_LABEL (compact badge) and REFERENCE (the "DEBUG" badge is the width reference).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HeaderVersionBadgeTest {

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
        // Overflow badges run a marquee animation: drive the clock manually so no frame
        // is pumped and the tests never wait on the animation to settle.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                block()
            }
        }
    }

    @Test
    fun longBadgeIsClampedToDebugBadgeWidth() {
        content {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderBadgeBox("DEBUG", Color.Blue)
                HeaderBadgeBox("MINIFIED", Color.Red)
            }
        }

        val refWidth = composeRule.onNodeWithText("DEBUG")
            .onParent().fetchSemanticsNode().size.width
        val clampedWidth = composeRule.onNodeWithText("MINIFIED")
            .onParent().fetchSemanticsNode().size.width

        assertEquals(
            "a label wider than 'DEBUG' must be clamped to the same badge width",
            refWidth,
            clampedWidth
        )
    }

    @Test
    fun shortBadgeStaysNarrowerThanDebugBadge() {
        content {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderBadgeBox("DEBUG", Color.Blue)
                HeaderBadgeBox("BETA", Color.Red)
            }
        }

        val refWidth = composeRule.onNodeWithText("DEBUG")
            .onParent().fetchSemanticsNode().size.width
        val shortWidth = composeRule.onNodeWithText("BETA")
            .onParent().fetchSemanticsNode().size.width

        assertTrue(
            "a label shorter than 'DEBUG' must keep a compact badge",
            shortWidth < refWidth
        )
    }

    @Test
    fun clampedLongestBadgeLabelStaysAtDebugBadgeWidth() {
        // "MINIFIED 32" is the longest real badge label: it must stay clamped to the
        // reference width. (The marquee itself is draw-only — BasicMarquee animates the
        // drawing offset, not the layout — so its scrolling cannot be observed through
        // layout positions in a unit test.)
        content {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderBadgeBox("DEBUG", Color.Blue)
                HeaderBadgeBox("MINIFIED 32", Color.Red)
            }
        }

        composeRule.onNodeWithText("MINIFIED 32").assertIsDisplayed()

        val refWidth = composeRule.onNodeWithText("DEBUG")
            .onParent().fetchSemanticsNode().size.width
        val clampedWidth = composeRule.onNodeWithText("MINIFIED 32")
            .onParent().fetchSemanticsNode().size.width

        assertEquals(
            "the longest badge label must be clamped to the DEBUG badge width",
            refWidth,
            clampedWidth
        )
    }
}
