package app.n_zik.android.components.ui.screens.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeCollapsingHeaderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativePanelMovesItsBackgroundAndHitTargetsTogetherWhileFading() =
        verifyPanel()

    private fun verifyPanel() {
        val offset = mutableFloatStateOf(0f)
        val height = mutableIntStateOf(0)
        var pageClicks = 0
        var titleClicks = 0
        var actionClicks = 0
        compose.setContent {
            Box(Modifier.size(240.dp, 360.dp).testTag("scene")) {
                Box(Modifier.fillMaxSize().background(Color.Blue).clickable { pageClicks++ })
                HomeCollapsingHeader(
                    headerOffset = offset,
                    headerHeight = height,
                    topContentInset = { 88.dp },
                    backgroundColor = Color.Red,
                    shape = RoundedCornerShape(0.dp),
                ) {
                    Column {
                        Box(Modifier.fillMaxWidth().height(56.dp).clickable { titleClicks++ })
                        Box(Modifier.fillMaxWidth().height(56.dp).clickable { actionClicks++ })
                    }
                }
            }
        }
        val scene = compose.onNodeWithTag("scene")
        fun pixel(y: Int): Color = scene.captureToImage().toPixelMap()[120, y]
        fun tap(y: Float) = scene.performTouchInput { click(Offset(120f, y)) }
        val panelColor = Color.Red

        assertEquals(112, height.intValue)
        assertEquals(panelColor, pixel(100))
        tap(100f)
        assertEquals(1, titleClicks)

        compose.runOnIdle { offset.floatValue = -56f }
        assertEquals(112, height.intValue)
        assertEquals(Color.Blue, pixel(20))
        val fading = pixel(100)
        assertTrue(fading.red > 0f && fading.red < 1f)
        assertTrue(fading.blue > 0f && fading.blue < 1f)
        assertEquals(Color.Blue, pixel(170))
        tap(20f)
        tap(60f)
        tap(100f)
        assertEquals(1, pageClicks)
        assertEquals(2, titleClicks)
        assertEquals(1, actionClicks)

        compose.runOnIdle { offset.floatValue = -112f }
        assertEquals(112, height.intValue)
        assertEquals(Color.Blue, pixel(60))
        assertEquals(Color.Blue, pixel(100))
        tap(100f)
        assertEquals(2, pageClicks)
        assertEquals(1, actionClicks)
    }

    @Test fun roundedPanelKeepsOpaqueBackingBehindItsLowerCorners() {
        val offset = mutableFloatStateOf(0f)
        val height = mutableIntStateOf(0)
        compose.setContent {
            Box(Modifier.size(240.dp, 360.dp).testTag("rounded-scene")) {
                Box(Modifier.fillMaxSize().background(Color.Blue))
                HomeCollapsingHeader(
                    headerOffset = offset,
                    headerHeight = height,
                    topContentInset = { 88.dp },
                    backgroundColor = Color.Red,
                    panelColor = Color.Green,
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(112.dp))
                }
            }
        }
        val pixels = compose.onNodeWithTag("rounded-scene").captureToImage().toPixelMap()
        assertEquals(Color.Green, pixels[120, 190])
        assertEquals(Color.Red, pixels[2, 195])
    }

    @Test fun sideNavigationStatusBackingFadesWithPortraitToolbar() {
        val offset = mutableFloatStateOf(0f)
        val height = mutableIntStateOf(0)
        compose.setContent {
            CompositionLocalProvider(LocalHomeTopCover provides true) {
                Box(Modifier.size(240.dp, 360.dp).testTag("side-status")) {
                    Box(Modifier.fillMaxSize().background(Color.Blue))
                    HomeCollapsingHeader(
                        headerOffset = offset,
                        headerHeight = height,
                        topContentInset = { 24.dp },
                        backgroundColor = Color.Red,
                        shape = RoundedCornerShape(0.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().height(100.dp))
                    }
                }
            }
        }
        fun statusPixel() = compose.onNodeWithTag("side-status").captureToImage().toPixelMap()[120, 4]
        assertEquals(Color.Red, statusPixel())
        compose.runOnIdle { offset.floatValue = -50f }
        val fading = statusPixel()
        assertTrue(fading.red > 0f && fading.red < 1f)
        assertTrue(fading.blue > 0f && fading.blue < 1f)
        compose.runOnIdle { offset.floatValue = -100f }
        assertEquals(Color.Blue, statusPixel())
        compose.runOnIdle { offset.floatValue = 0f }
        assertEquals(Color.Red, statusPixel())
    }

    @Test fun compactTopDockRemovesOnlyTheBackingAboveTheHomePanel() {
        val offset = mutableFloatStateOf(0f)
        val height = mutableIntStateOf(0)
        val fold = mutableFloatStateOf(0f)
        val anchor = HomeTopMiniPlayerAnchorState(
            baseTopInset = mutableStateOf<(() -> Dp)?>({ 24.dp }),
            topGap = mutableStateOf(0.dp),
            miniBeforeHeaderHeight = mutableStateOf<Dp?>(80.dp),
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalHomeTopMiniPlayerAnchor provides anchor,
                LocalHomeTopDockFoldProgress provides { fold.floatValue },
            ) {
                Box(Modifier.size(240.dp, 360.dp).testTag("top-dock")) {
                    Box(Modifier.fillMaxSize().background(Color.Blue))
                    HomeCollapsingHeader(
                        headerOffset = offset,
                        headerHeight = height,
                        topContentInset = { 24.dp },
                        backgroundColor = Color.Red,
                        panelColor = Color.Green,
                        shape = RoundedCornerShape(0.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().height(100.dp))
                    }
                }
            }
        }
        fun pixel(y: Int) = compose.onNodeWithTag("top-dock").captureToImage().toPixelMap()[120, y]
        assertEquals(Color.Red, pixel(50))
        assertEquals(Color.Green, pixel(150))
        compose.runOnIdle { fold.floatValue = .5f }
        val fading = pixel(50)
        assertTrue(fading.red > 0f && fading.red < 1f)
        assertTrue(fading.blue > 0f && fading.blue < 1f)
        compose.runOnIdle { fold.floatValue = 1f }
        assertEquals(Color.Blue, pixel(50))
        assertEquals(Color.Green, pixel(150))
        compose.runOnIdle { fold.floatValue = 0f }
        assertEquals(Color.Red, pixel(50))
    }
}
