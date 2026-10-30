package app.n_zik.android.components.dialog.common

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.glassEnabledKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class ButtonRowTest {
    @get:Rule val composeRule = createComposeRule()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @Test
    fun oneThroughFourActionsKeepAnchorsAndCallbacksAtSmallWidthsAndLargeFonts() {
        val count = mutableStateOf(1)
        val scale = mutableStateOf(1f)
        val width = mutableStateOf(220.dp)
        val labels = listOf("Action principale supplémentaire", "Réinitialiser tous les paramètres", "Annuler les modifications", "Confirmer les modifications")
        var clicked = ""
        var pixelsPerDp = 1f
        fun button(index: Int) = DialogButton(
            labels[index], { clicked = labels[index] },
            primary = index == 0 || index == 3,
            danger = index == 2,
        )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val density = LocalDensity.current
            pixelsPerDp = density.density
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalDensity provides Density(density.density, scale.value),
            ) {
                Box(Modifier.width(width.value).testTag("footer")) {
                    ButtonRow((4 - count.value until 4).map { button(it) })
                }
            }
        }
        fun settle() {
            repeat(3) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
            }
        }
        fun bounds(index: Int) = composeRule.onNodeWithText(labels[index]).fetchSemanticsNode().boundsInRoot
        for (fontScale in listOf(1f, 1.8f)) {
            for (footerWidth in listOf(220.dp, 180.dp)) {
                for (number in 1..4) {
                    composeRule.runOnIdle {
                        scale.value = fontScale
                        width.value = footerWidth
                        count.value = number
                    }
                    settle()
                    val footer = composeRule.onNodeWithTag("footer").fetchSemanticsNode().boundsInRoot
                    val confirmation = bounds(3)
                    val finalRowIndices = if (number >= 3) 1..3 else 4 - number..3
                    val finalRow = finalRowIndices.map { bounds(it) }
                    assertEquals(footer.left + 16 * pixelsPerDp, finalRow.first().left, 1f)
                    assertEquals(footer.right - 16 * pixelsPerDp, finalRow.last().right, 1f)
                    finalRow.forEach { assertEquals(confirmation.width, it.width, 1f) }
                    if (number == 1) {
                        assertEquals(footer.center.x, confirmation.center.x, 1f)
                    } else if (number == 2) {
                        assertEquals(footer.center.x, (bounds(2).left + confirmation.right) / 2, 1f)
                        assertEquals(confirmation.bottom, bounds(2).bottom, 1f)
                        assertTrue(bounds(2).right <= confirmation.left)
                    } else {
                        assertEquals(footer.right - 16 * pixelsPerDp, confirmation.right, 1f)
                        assertEquals(footer.center.x, bounds(2).center.x, 1f)
                        assertEquals(confirmation.bottom, bounds(2).bottom, 1f)
                    }
                    if (number >= 3) {
                        assertEquals(footer.left + 16 * pixelsPerDp, bounds(1).left, 1f)
                        assertEquals(confirmation.bottom, bounds(1).bottom, 1f)
                    }
                    if (number == 4) {
                        assertTrue(bounds(0).bottom <= bounds(1).top)
                        assertEquals(footer.center.x, bounds(0).center.x, 1f)
                        assertEquals(footer.width - 32 * pixelsPerDp, bounds(0).width, 1f)
                    }
                    for (first in 4 - number until 4) {
                        val a = bounds(first)
                        assertTrue("Pill stays inside footer: $a in $footer", a.left >= footer.left && a.right <= footer.right)
                        assertTrue("Keep 48dp touch width when available: $a", a.width >= 48 * pixelsPerDp - 1)
                        for (second in first + 1 until 4) assertNoOverlap(a, bounds(second))
                        composeRule.onNodeWithText(labels[first]).performTouchInput { click() }
                        composeRule.runOnIdle { assertEquals(labels[first], clicked) }
                    }
                }
            }
        }
    }

    @Test
    fun upperRowsAndOrdinaryActionsUseCenteredPairsAndThreeAnchors() {
        val count = mutableStateOf(2)
        val confirmationPair = mutableStateOf(false)
        var pixelsPerDp = 1f
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            pixelsPerDp = LocalDensity.current.density
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Box(Modifier.width(220.dp).testTag("footer")) {
                    ButtonRow((0 until count.value).map { index ->
                        DialogButton(
                            "Action $index", {},
                            primary = (count.value > 2 || confirmationPair.value) && index == count.value - 1,
                            danger = (count.value > 2 || confirmationPair.value) && index == count.value - 2,
                        )
                    })
                }
            }
        }
        fun bounds(index: Int) = composeRule.onNodeWithText("Action $index").fetchSemanticsNode().boundsInRoot
        for ((number, pair) in listOf(2 to false, 2 to true, 5 to false, 6 to false)) {
            composeRule.runOnIdle {
                count.value = number
                confirmationPair.value = pair
            }
            repeat(3) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
            }
            val footer = composeRule.onNodeWithTag("footer").fetchSemanticsNode().boundsInRoot
            if (number == 6) {
                assertEquals(footer.center.x, bounds(1).center.x, 1f)
                assertEquals(bounds(3).left, bounds(0).left, 1f)
                assertEquals(bounds(5).right, bounds(2).right, 1f)
            } else {
                assertEquals(footer.center.x, (bounds(0).left + bounds(1).right) / 2, 1f)
            }
            val upperCount = if (number == 2) 2 else number - 3
            assertEquals(footer.left + 16 * pixelsPerDp, bounds(0).left, 1f)
            assertEquals(footer.right - 16 * pixelsPerDp, bounds(upperCount - 1).right, 1f)
            for (index in 1 until upperCount) assertEquals(bounds(0).width, bounds(index).width, 1f)
            for (index in 1 until upperCount) assertEquals(bounds(0).top, bounds(index).top, 1f)
            if (number > 2) assertTrue(bounds(0).bottom <= bounds(number - 3).top)
            for (first in 0 until number) {
                for (second in first + 1 until number) assertNoOverlap(bounds(first), bounds(second))
            }
        }
    }

    @Test
    fun explicitPlacementOverridesDangerAndDisabledPillDoesNotCallBack() {
        var clearClicks = 0
        var confirmClicks = 0
        var disabledClicks = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Box(Modifier.width(220.dp).testTag("footer")) {
                    ButtonRow(listOf(
                        DialogButton("Clear", { clearClicks++ }, danger = true, placement = DialogButtonPlacement.Start),
                        DialogButton("Disabled", { disabledClicks++ }, enabled = false, placement = DialogButtonPlacement.Center),
                        DialogButton("Done", { confirmClicks++ }, primary = true),
                    ))
                }
            }
        }
        val clear = composeRule.onNodeWithText("Clear").fetchSemanticsNode().boundsInRoot
        val disabled = composeRule.onNodeWithText("Disabled").fetchSemanticsNode().boundsInRoot
        val done = composeRule.onNodeWithText("Done").fetchSemanticsNode().boundsInRoot
        assertTrue(clear.right <= disabled.left)
        assertTrue(disabled.right <= done.left)
        composeRule.onNodeWithText("Disabled").assertIsNotEnabled().performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(0, clearClicks)
            assertEquals(0, confirmClicks)
            assertEquals(0, disabledClicks)
        }
        composeRule.onNodeWithText("Clear").performTouchInput { click() }
        composeRule.onNodeWithText("Done").performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(1, clearClicks)
            assertEquals(1, confirmClicks)
        }
    }

    @Test
    fun glassActionsKeepDisabledAndRetainedPresentationInputGuards() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setActiveProfile("default", context)
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        val store = GlassStore(context)
        store.reset()
        store.setProfile(GlassQuality.Performance)
        val interactive = mutableStateOf(true)
        val enabled = mutableStateOf(true)
        val presentation = DialogPresentation(interactive)
        var clicks = 0
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalDialogInput provides presentation,
            ) {
                Box(Modifier.width(220.dp)) {
                    ButtonRow(listOf(DialogButton("Apply", { clicks++ }, primary = true, enabled = enabled.value)))
                }
            }
        }
        try {
            composeRule.onNodeWithText("Apply").performTouchInput { click() }
            composeRule.runOnIdle { assertEquals(1, clicks); enabled.value = false }
            composeRule.onNodeWithText("Apply").assertIsNotEnabled().performTouchInput { click() }
            composeRule.runOnIdle { assertEquals(1, clicks); enabled.value = true; interactive.value = false }
            composeRule.onNodeWithText("Apply").assertIsNotEnabled().performTouchInput { click() }
            composeRule.runOnIdle { assertEquals(1, clicks); interactive.value = true }
            composeRule.onNodeWithText("Apply").performTouchInput { click() }
            composeRule.runOnIdle { assertEquals(2, clicks) }
        } finally {
            context.preferences.edit().putBoolean(glassEnabledKey, false).commit()
            store.reset()
        }
    }

    private fun assertNoOverlap(a: Rect, b: Rect) {
        assertTrue("Pills do not overlap: $a and $b", a.bottom <= b.top || b.bottom <= a.top || a.right <= b.left || b.right <= a.left)
    }
}
