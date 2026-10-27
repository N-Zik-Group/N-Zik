package app.n_zik.android.components.dialog.common

import android.app.Application
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf

/** Default-width selectors must settle during native dialog measurement. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class DialogFrameLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @Test fun `default selector settles initially within portrait bounds`() {
        selector(initiallyVisible = true, maxWidth = 342.dp, maxHeight = 512.dp)
    }

    @Test fun `default selector settles after state change within portrait bounds`() {
        selector(initiallyVisible = false, maxWidth = 342.dp, maxHeight = 512.dp)
    }

    @Config(qualifiers = "w900dp-h500dp-land")
    @Test fun `default selector settles initially within landscape bounds`() {
        selector(initiallyVisible = true, maxWidth = 540.dp, maxHeight = 400.dp)
    }

    @Config(qualifiers = "w900dp-h500dp-land")
    @Test fun `default selector settles after state change within landscape bounds`() {
        selector(initiallyVisible = false, maxWidth = 540.dp, maxHeight = 400.dp)
    }

    @Test fun `selector without subtitle has equal body space above and below options`() {
        selectorBodySpacing(subtitle = null)
    }

    @Test fun `selector subtitle has a real description gap while outer body padding remains balanced`() {
        selectorBodySpacing(subtitle = "Choose the source used for playback")
    }

    private fun selectorBodySpacing(subtitle: String?) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                ValueSelectorDialog(
                    onDismiss = {}, title = "Font type", titleSecondary = subtitle,
                    selectedValue = "a", values = listOf("a", "b"), onValueSelected = {}
                )
            }
        }
        repeat(5) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()
        val body = composeRule.onNodeWithTag("dialogFrameBody").getBoundsInRoot()
        val list = composeRule.onNodeWithTag("valueSelectorList").getBoundsInRoot()
        val first = composeRule.onNode(hasText("a") and hasClickAction()).getBoundsInRoot()
        val last = composeRule.onNode(hasText("b") and hasClickAction()).getBoundsInRoot()
        assertEquals("scroll viewport starts at body edge", body.top.value, list.top.value, 1f)
        assertEquals("scroll viewport ends at body edge", body.bottom.value, list.bottom.value, 1f)
        assertEquals("bottom option space", 24f, (body.bottom - last.bottom).value, 1f)
        if (subtitle == null) {
            composeRule.onNodeWithTag("valueSelectorSubtitle").assertDoesNotExist()
            assertEquals("top option-list space", 24f, (first.top - body.top).value, 1f)
            assertEquals("equal outer gaps", (first.top - body.top).value, (body.bottom - last.bottom).value, 1f)
        } else {
            val description = composeRule.onNodeWithTag("valueSelectorSubtitle").getBoundsInRoot()
            assertEquals("top description space", 24f, (description.top - body.top).value, 1f)
            assertEquals("description to list space", 16f, (first.top - description.bottom).value, 1f)
        }
    }

    private fun selector(initiallyVisible: Boolean, maxWidth: Dp, maxHeight: Dp) {
        val visible = mutableStateOf(initiallyVisible)
        // Infinite marquee animation requires a manual clock; native measurement must still settle.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                if (visible.value) {
                    ValueSelectorDialog(
                        onDismiss = { visible.value = false },
                        title = "Choose source",
                        selectedValue = "a",
                        values = listOf("a", "b"),
                        onValueSelected = {},
                    )
                }
            }
        }
        if (!initiallyVisible) {
            composeRule.onAllNodes(isDialog()).assertCountEquals(0)
            composeRule.runOnIdle { visible.value = true }
        }
        repeat(30) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)
        composeRule.onNodeWithText("Choose source").assertExists()
        composeRule.onNodeWithText("a").assertExists()
        composeRule.onNodeWithText("b").assertExists()
        val bounds = composeRule.onNodeWithTag("dialogFrameCard").getBoundsInRoot()
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        assertTrue("Dialog width must be positive and bounded: $width", width > 0.dp && width <= maxWidth)
        assertTrue("Dialog height must be positive and bounded: $height", height > 0.dp && height <= maxHeight)
    }
}
