package app.n_zik.android.components.dialog.common

import android.app.Application
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w640dp-h360dp-land")
class DialogScrollableBodyTest {
    @get:Rule val composeRule = createComposeRule()
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape, uiRoundnessShape = CircleShape, artistThumbnailShape = CircleShape,
    )

    @Test fun `long sync results scroll to the final item while footer remains visible`() {
        val dialog = SyncResultDialog(mutableStateOf(true), List(50) { "Result $it" to true }, 1)
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) { dialog.Render() }
        }
        repeat(5) { composeRule.mainClock.advanceTimeByFrame() }
        val footerBefore = composeRule.onNodeWithTag("dialogFrameFooter").getBoundsInRoot()
        composeRule.onNodeWithText("✓ Result 49").performScrollTo().assertIsDisplayed()
        val footerAfter = composeRule.onNodeWithTag("dialogFrameFooter").assertIsDisplayed().getBoundsInRoot()
        assertEquals(footerBefore.top.value, footerAfter.top.value, 1f)
        val body = composeRule.onNodeWithTag("dialogFrameBody").getBoundsInRoot()
        val divider = composeRule.onNodeWithTag("dialogFrameFooterDivider").getBoundsInRoot()
        assertEquals(body.bottom.value, divider.top.value, 1f)
    }

    @Test fun `default dialog scroll viewport meets both dividers and retains fixed footer`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                DefaultDialog(onDismiss = {}, title = "Details", footer = {
                    ButtonRow(listOf(DialogButton("Close", onClick = {})))
                }) {
                    repeat(50) { BasicText("Detail $it") }
                }
            }
        }
        repeat(5) { composeRule.mainClock.advanceTimeByFrame() }
        val viewport = composeRule.onNode(hasScrollAction()).getBoundsInRoot()
        val headerDivider = composeRule.onNodeWithTag("dialogFrameHeaderDivider").getBoundsInRoot()
        val footerDivider = composeRule.onNodeWithTag("dialogFrameFooterDivider").getBoundsInRoot()
        assertEquals(headerDivider.bottom.value, viewport.top.value, 1f)
        assertEquals(footerDivider.top.value, viewport.bottom.value, 1f)
        val first = composeRule.onNodeWithText("Detail 0").getBoundsInRoot()
        assertEquals(24f, first.top.value - viewport.top.value, 1f)
        composeRule.onNodeWithText("Detail 49").performScrollTo().assertIsDisplayed()
        assertEquals(footerDivider.top.value,
            composeRule.onNodeWithTag("dialogFrameFooterDivider").getBoundsInRoot().top.value, 1f)
        composeRule.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test fun `matching progress separates short heading from instructions`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                InProgressDialog(total = 10, done = 2, text = "Matching songs", message = "Keep the app open while songs are matched.")
            }
        }
        repeat(5) { composeRule.mainClock.advanceTimeByFrame() }
        val header = composeRule.onNodeWithTag("dialogFrameHeader").getBoundsInRoot()
        val heading = composeRule.onNodeWithText("Matching songs").assertIsDisplayed().getBoundsInRoot()
        val message = composeRule.onNodeWithText("Keep the app open while songs are matched.").getBoundsInRoot()
        assertTrue(heading.top >= header.top && heading.bottom <= header.bottom)
        assertTrue(message.top >= header.bottom)
    }
}
