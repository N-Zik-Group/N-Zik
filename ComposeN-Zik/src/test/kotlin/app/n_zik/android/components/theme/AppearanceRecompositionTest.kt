package app.n_zik.android.components.theme

import android.app.Application
import android.os.Looper
import java.time.Duration
import kotlin.math.abs
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.colorPalette
import app.n_zik.android.components.player.PaletteFade
import app.n_zik.android.typography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@OptIn(ExperimentalMaterial3Api::class)
class AppearanceRecompositionTest {
    @get:Rule val composeRule = createComposeRule()

    private val red = appearance(Color.Red)
    private val blue = appearance(Color.Blue)
    private val green = appearance(Color.Green)

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `global frames update palette readers without recomposing content or unrelated children`() {
        val target = mutableStateOf(red)
        val fadeFrom = mutableStateOf<Appearance?>(null)
        val colors = mutableListOf<Appearance>()
        var contentCount = 0
        var unrelatedCount = 0
        var completed = 0
        val completionCounts = mutableListOf<Pair<Int, Int>>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ProvideAnimatedAppearance(target.value, fadeFrom.value, 320, {
                completionCounts += contentCount to unrelatedCount
                completed++
                fadeFrom.value = null
            }) {
                SideEffect { contentCount++ }
                UnrelatedChild { unrelatedCount++ }
                PaletteReader { colors += it }
                Box(Modifier.size(16.dp).testTag("root-background").appearanceBackground())
                AppearanceVideoSheet(showSheet = false, onDismissRequest = {}) {}
                AppearanceMenuSheet(
                    showSheet = false,
                    onDismissRequest = {},
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
                ) {}
            }
        }
        assertRenderedColor("root-background", red.colorPalette.background0)
        composeRule.runOnIdle {
            assertEquals(red, colors.last())
            fadeFrom.value = red
            target.value = blue
        }
        advanceFrames(16)
        assertRenderedColor("root-background", red.colorPalette.background0)
        advanceFrames(32)
        var stableContentCount = 0
        var stableUnrelatedCount = 0
        composeRule.runOnIdle {
            stableContentCount = contentCount
            stableUnrelatedCount = unrelatedCount
        }
        advanceFrames(96)
        composeRule.runOnIdle {
            assertIntermediate(colors.last(), red, blue)
            assertEquals(stableContentCount, contentCount)
            assertEquals(stableUnrelatedCount, unrelatedCount)
        }
        assertRenderedColor("root-background", colors.last().colorPalette.background0)
        advanceFrames(400)
        composeRule.runOnIdle {
            assertEquals(blue, colors.last())
            assertEquals(stableContentCount to stableUnrelatedCount, completionCounts.last())
            assertTrue(contentCount <= stableContentCount + 1)
            assertTrue(unrelatedCount <= stableUnrelatedCount + 1)
            assertEquals(1, completed)
            assertEquals(null, fadeFrom.value)
        }
        assertRenderedColor("root-background", blue.colorPalette.background0)
        composeRule.runOnIdle {
            fadeFrom.value = blue
            target.value = green
        }
        advanceFrames(48)
        composeRule.runOnIdle {
            stableContentCount = contentCount
            stableUnrelatedCount = unrelatedCount
        }
        advanceFrames(400)
        composeRule.runOnIdle {
            assertEquals(green, colors.last())
            assertEquals(2, completed)
            assertEquals(null, fadeFrom.value)
            assertEquals(stableContentCount to stableUnrelatedCount, completionCounts.last())
            stableContentCount = contentCount
            stableUnrelatedCount = unrelatedCount
        }
        assertRenderedColor("root-background", green.colorPalette.background0)
        advanceFrames(96)
        composeRule.runOnIdle {
            assertEquals(stableContentCount, contentCount)
            assertEquals(stableUnrelatedCount, unrelatedCount)
            assertEquals(2, completed)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `visible video sheet follows animated colors and both sheets dismiss their content`() {
        val roundedRed = red.copy(
            uiRoundnessShape = RoundedCornerShape(
                topStart = 36.dp,
                topEnd = 12.dp,
                bottomStart = 72.dp,
                bottomEnd = 60.dp,
            ),
        )
        val target = mutableStateOf(roundedRed)
        val fadeFrom = mutableStateOf<Appearance?>(null)
        val showVideo = mutableStateOf(true)
        val showMenu = mutableStateOf(false)
        val videoColors = mutableListOf<Color>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ProvideAnimatedAppearance(target.value, fadeFrom.value, 320, { fadeFrom.value = null }) {
                AppearanceVideoSheet(showVideo.value, { showVideo.value = false }) {
                    val contentColor = LocalContentColor.current
                    SideEffect { videoColors += contentColor }
                    Box(Modifier.fillMaxWidth().height(160.dp).testTag("video-content"))
                }
                AppearanceMenuSheet(
                    showSheet = showMenu.value,
                    onDismissRequest = { showMenu.value = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
                ) {
                    Box(Modifier.fillMaxWidth().height(160.dp).testTag("menu-content"))
                }
            }
        }
        advanceFrames(600)
        composeRule.onNodeWithTag("video-content").assertIsDisplayed()
        assertRenderedColor("video-content", red.colorPalette.background0)
        val roundedPixels = composeRule.onNodeWithTag("video-content").captureToImage().toPixelMap()
        assertNotEquals(Color.Red.toArgb(), roundedPixels[1, 1].toArgb())
        assertEquals(Color.Red.toArgb(), roundedPixels[1, roundedPixels.height - 2].toArgb())
        composeRule.runOnIdle {
            fadeFrom.value = roundedRed
            target.value = blue.copy(uiRoundnessShape = CutCornerShape(36.dp))
        }
        advanceFrames(144)
        composeRule.runOnIdle {
            assertNotEquals(Color.Red, videoColors.last())
            assertNotEquals(Color.Blue, videoColors.last())
        }
        assertRenderedColor("video-content", videoColors.last())
        advanceFrames(240)
        assertRenderedColor("video-content", blue.colorPalette.background0)
        val fallbackPixels = composeRule.onNodeWithTag("video-content").captureToImage().toPixelMap()
        assertNotEquals(Color.Blue.toArgb(), fallbackPixels[1, fallbackPixels.height - 2].toArgb())
        composeRule.runOnIdle {
            assertEquals(Color.Blue, videoColors.last())
            showVideo.value = false
        }
        advanceFrames(600)
        composeRule.onNodeWithTag("video-content").assertDoesNotExist()
        composeRule.runOnIdle { showMenu.value = true }
        advanceFrames(600)
        composeRule.onNodeWithTag("menu-content").assertIsDisplayed()
        composeRule.runOnIdle {
            fadeFrom.value = blue
            target.value = green
        }
        advanceFrames(400)
        composeRule.onNodeWithTag("menu-content").assertIsDisplayed()
        composeRule.runOnIdle { showMenu.value = false }
        advanceFrames(600)
        composeRule.onNodeWithTag("menu-content").assertDoesNotExist()
    }

    @Test
    fun `scoped fallback fade invalidates readers only`() {
        val target = mutableStateOf(red)
        val colors = mutableListOf<Appearance>()
        var contentCount = 0
        var unrelatedCount = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides target.value) {
                PaletteFade(durationMillis = 320) {
                    SideEffect { contentCount++ }
                    UnrelatedChild { unrelatedCount++ }
                    PaletteReader { colors += it }
                }
            }
        }
        advanceFrames(32)
        composeRule.runOnIdle { target.value = blue }
        advanceFrames(48)
        var stableContentCount = 0
        var stableUnrelatedCount = 0
        composeRule.runOnIdle {
            stableContentCount = contentCount
            stableUnrelatedCount = unrelatedCount
        }
        advanceFrames(96)
        composeRule.runOnIdle {
            assertIntermediate(colors.last(), red, blue)
            assertEquals(stableContentCount, contentCount)
            assertEquals(stableUnrelatedCount, unrelatedCount)
        }
        advanceFrames(400)
        composeRule.runOnIdle {
            assertEquals(blue, colors.last())
            assertEquals(stableContentCount, contentCount)
            assertEquals(stableUnrelatedCount, unrelatedCount)
        }
    }

    @Test
    fun `nested scoped fade keeps a stable target during global frames`() {
        val target = mutableStateOf(red)
        val fadeFrom = mutableStateOf<Appearance?>(null)
        val globalColors = mutableListOf<Appearance>()
        val scopedColors = mutableListOf<Appearance>()
        var unrelatedCount = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ProvideAnimatedAppearance(target.value, fadeFrom.value, 320, {}) {
                PaletteReader { globalColors += it }
                PaletteFade(durationMillis = 320) {
                    UnrelatedChild { unrelatedCount++ }
                    PaletteReader { scopedColors += it }
                }
            }
        }
        advanceFrames(32)
        composeRule.runOnIdle {
            fadeFrom.value = red
            target.value = blue
        }
        advanceFrames(48)
        var stableCount = 0
        composeRule.runOnIdle { stableCount = unrelatedCount }
        repeat(3) {
            advanceFrames(32)
            composeRule.runOnIdle {
                assertIntermediate(globalColors.last(), red, blue)
                assertPaletteColorsNear(globalColors.last(), scopedColors.last())
                assertEquals(stableCount, unrelatedCount)
            }
        }
        var origin: Appearance? = null
        composeRule.runOnIdle {
            origin = scopedColors.last()
            globalColors.clear()
            scopedColors.clear()
            target.value = green
        }
        advanceFrames(16)
        composeRule.runOnIdle {
            assertRetargetContinuity(requireNotNull(origin), globalColors.first(), blue)
            assertRetargetContinuity(requireNotNull(origin), scopedColors.first(), blue)
            assertPaletteColorsNear(globalColors.first(), scopedColors.first())
            stableCount = unrelatedCount
        }
        repeat(9) {
            advanceFrames(32)
            composeRule.runOnIdle {
                assertPaletteColorsNear(globalColors.last(), scopedColors.last())
                assertEquals(stableCount, unrelatedCount)
            }
        }
        advanceFrames(48)
        composeRule.runOnIdle {
            assertEquals(green, globalColors.last())
            assertEquals(green, scopedColors.last())
            assertEquals(stableCount, unrelatedCount)
        }
    }

    @Test
    fun `immediate application replaces the displayed palette and next fade starts there`() {
        val target = mutableStateOf(red)
        val fadeFrom = mutableStateOf<Appearance?>(null)
        val colors = mutableListOf<Appearance>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ProvideAnimatedAppearance(target.value, fadeFrom.value, 320, {}) {
                PaletteReader { colors += it }
            }
        }
        advanceFrames(32)
        composeRule.runOnIdle {
            fadeFrom.value = red
            target.value = blue
        }
        advanceFrames(112)
        composeRule.runOnIdle { assertIntermediate(colors.last(), red, blue) }
        composeRule.runOnIdle {
            fadeFrom.value = null
            target.value = green
        }
        advanceFrames(16)
        composeRule.runOnIdle {
            assertEquals(green, colors.last())
            fadeFrom.value = green
            target.value = red
        }
        advanceFrames(16)
        composeRule.runOnIdle { assertEquals(green, colors.last()) }
        advanceFrames(400)
        composeRule.runOnIdle { assertEquals(red, colors.last()) }
    }

    @Test
    fun `rapid retarget keeps the currently displayed origin and compatibility callback`() {
        val target = mutableStateOf(red)
        val fadeFrom = mutableStateOf<Appearance?>(null)
        val colors = mutableListOf<Appearance>()
        var completed = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AnimatedAppearance(target.value, fadeFrom.value, 320, { completed++ }) {
                SideEffect { colors += it }
            }
        }
        advanceFrames(32)
        composeRule.runOnIdle {
            fadeFrom.value = red
            target.value = blue
        }
        advanceFrames(112)
        var origin: Appearance? = null
        composeRule.runOnIdle {
            origin = colors.last()
            assertIntermediate(requireNotNull(origin), red, blue)
            colors.clear()
            target.value = green
        }
        advanceFrames(16)
        composeRule.runOnIdle {
            assertRetargetContinuity(requireNotNull(origin), colors.first(), blue)
        }
        advanceFrames(400)
        composeRule.runOnIdle {
            assertEquals(green, colors.last())
            assertEquals(1, completed)
        }
    }

    @Composable
    private fun PaletteReader(onRead: (Appearance) -> Unit) {
        val appearance = LocalAppearance.current
        val helperPalette = colorPalette()
        val helperTypography = typography()
        SideEffect {
            assertEquals(appearance.colorPalette, helperPalette)
            assertEquals(appearance.typography, helperTypography)
            onRead(appearance)
        }
    }

    @Composable
    private fun UnrelatedChild(onCompose: () -> Unit) {
        SideEffect(onCompose)
    }

    private fun assertIntermediate(value: Appearance, from: Appearance, target: Appearance) {
        assertNotEquals(from.colorPalette.background0, value.colorPalette.background0)
        assertNotEquals(target.colorPalette.background0, value.colorPalette.background0)
        assertEquals(value.colorPalette.text, value.typography.s.color)
        assertTrue(value.colorPalette.background0.alpha > 0f)
    }

    private fun advanceFrames(durationMillis: Long) {
        repeat(((durationMillis + 15) / 16).toInt()) {
            composeRule.mainClock.advanceTimeByFrame()
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            composeRule.waitForIdle()
        }
    }

    private fun assertPaletteColorsNear(expected: Appearance, actual: Appearance) {
        val expectedColor = expected.colorPalette.background0
        val actualColor = actual.colorPalette.background0
        assertEquals(expectedColor.red, actualColor.red, 2f / 255f)
        assertEquals(expectedColor.green, actualColor.green, 2f / 255f)
        assertEquals(expectedColor.blue, actualColor.blue, 2f / 255f)
    }

    private fun assertRetargetContinuity(previous: Appearance, origin: Appearance, oldTarget: Appearance) {
        val before = previous.colorPalette.background0
        val after = origin.colorPalette.background0
        val endpoint = oldTarget.colorPalette.background0
        // The old animation can deliver one final frame before target recomposition cancels it.
        assertTrue(abs(before.red - after.red) <= 0.16f)
        assertTrue(abs(before.green - after.green) <= 0.16f)
        assertTrue(abs(before.blue - after.blue) <= 0.16f)
        assertNotEquals(endpoint, after)
        assertEquals(after, origin.typography.s.color)
    }

    private fun assertRenderedColor(tag: String, expected: Color) {
        val pixels = composeRule.onNodeWithTag(tag).captureToImage().toPixelMap()
        val actual = pixels[pixels.width / 2, pixels.height / 2]
        assertEquals(expected.red, actual.red, 1f / 255f)
        assertEquals(expected.green, actual.green, 1f / 255f)
        assertEquals(expected.blue, actual.blue, 1f / 255f)
        assertEquals(expected.alpha, actual.alpha, 1f / 255f)
    }

    private fun appearance(color: Color) = Appearance(
        colorPalette = ColorPalette.fallback.copy(background0 = color, text = color),
        typography = Typography.fallback.withColor(color),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )
}
