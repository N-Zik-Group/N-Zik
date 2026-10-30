package app.n_zik.android.components.navigation.header

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeaderScrollFadeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `header lower corners remain square`() {
        compose.setContent {
            Box(Modifier.size(200.dp, 100.dp).background(Color.White).testTag("rounded")) {
                Box(Modifier.headerScrollFade(24f, 64f, Color.Red,
                    clipStatusAtRest = true) { 0f }.size(200.dp, 88.dp))
            }
        }
        fun pixels() = compose.onNodeWithTag("rounded").captureToImage().toPixelMap()
        assertEquals(Color.Red, pixels()[1, 86])
        assertEquals(Color.Red, pixels()[100, 86])
        assertEquals(Color.Red, pixels()[198, 86])
    }

    @Test fun `configured top header keeps status transparent until actual top tabs are visible`() {
        val hasVisibleTopTabs = mutableStateOf(false)
        compose.setContent {
            Box(Modifier.size(200.dp, 160.dp).background(Color.White).testTag("scene")) {
                Box(Modifier.headerScrollFade(
                    24f, 64f, surfaceColor = Color.Blue,
                    clipStatusAtRest = !hasVisibleTopTabs.value,
                ) { 0f }.size(200.dp, 88.dp))
            }
        }
        fun sample(y: Int) = compose.onNodeWithTag("scene").captureToImage().toPixelMap()[100, y]
        assertEquals(Color.White, sample(12))
        assertEquals(Color.Blue, sample(40))
        compose.runOnIdle { hasVisibleTopTabs.value = true }
        assertEquals(Color.Blue, sample(12))
        compose.runOnIdle { hasVisibleTopTabs.value = false }
        assertEquals(Color.White, sample(12))
        assertEquals(Color.Blue, sample(40))
    }

    @Test fun `moving header surface and glyphs share the status fade and retain their hue`() {
        val offset = mutableFloatStateOf(0f)
        compose.setContent {
            Box(Modifier.size(200.dp, 160.dp).background(Color.White).testTag("scene")) {
                Box(Modifier.headerScrollFade(24f, 64f, Color.Blue.copy(alpha = .4f)) { offset.floatValue }
                    .size(200.dp, 88.dp)) {
                    Box(Modifier.offset(80.dp, 56.dp).size(40.dp, 24.dp).background(Color.Red))
                }
            }
        }
        fun sample(x: Int, y: Int) = compose.onNodeWithTag("scene").captureToImage().toPixelMap()[x, y]
        assertEquals(.6f, sample(20, 40).red, .02f)
        assertEquals(Color.Red, sample(100, 64))
        compose.runOnIdle { offset.floatValue = -32f }
        assertEquals(.6f, sample(20, 40).red, .02f)
        assertEquals(Color.Red, sample(100, 36))
        assertEquals(Color.White, sample(100, 64))
        assertEquals(Color.White, sample(100, 12))
        compose.runOnIdle { offset.floatValue = -52f }
        assertEquals(.8f, sample(20, 30).red, .02f)
        assertEquals(.5f, sample(100, 26).green, .02f)
        compose.runOnIdle { offset.floatValue = -64f }
        assertEquals(Color.White, sample(20, 12))
        assertEquals(Color.White, sample(20, 40))
        assertEquals(Color.White, sample(100, 36))
        assertEquals(Color.White, sample(100, 64))
        compose.runOnIdle { offset.floatValue = 0f }
        assertEquals(Color.Red, sample(100, 64))
    }

    @Test fun `header stays opaque below status icons and hidden endpoint releases page input`() {
        val offset = mutableFloatStateOf(0f)
        var headerClicks = 0
        var pageClicks = 0
        compose.setContent {
            val density = LocalDensity.current
            val status = with(density) { 24.dp.toPx() }
            val travel = with(density) { 64.dp.toPx() }
            Box(Modifier.size(200.dp, 160.dp).background(Color.White).testTag("scene")) {
                Box(Modifier.fillMaxSize().clickable { pageClicks++ })
                Box(Modifier.headerScrollFade(status, travel) { offset.floatValue * travel }
                    .size(200.dp, 88.dp).background(Color.Red).clickable { headerClicks++ })
            }
        }
        val scene = compose.onNodeWithTag("scene")
        fun sample(y: Int) = scene.captureToImage().toPixelMap()[100, y]
        assertEquals(Color.Red, sample(40))
        scene.performTouchInput { click(Offset(100f, 40f)) }
        compose.runOnIdle { offset.floatValue = -.5f }
        assertEquals(Color.White, sample(12))
        assertEquals(Color.Red, sample(40))
        scene.performTouchInput { click(Offset(100f, 12f)) }
        compose.runOnIdle { offset.floatValue = -1f }
        assertEquals(Color.White, sample(12))
        assertEquals(Color.White, sample(40))
        scene.performTouchInput { click(Offset(100f, 12f)) }
        compose.runOnIdle {
            assertEquals(1, headerClicks)
            assertEquals(2, pageClicks)
        }
    }
}
