package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.n_zik.android.utils.animatedDoubleShadowDrop
import app.n_zik.android.utils.doubleShadowDrop
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ElevatedButtonShadowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `native shadow motion draws the same pixels and keeps its outline`() {
        val travel = mutableStateOf(4.dp)
        val rounded = RoundedCornerShape(8.dp)
        var outlineCount = 0
        val shape = object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                outlineCount++
                return rounded.createOutline(size, layoutDirection, density)
            }
        }
        compose.setContent {
            Row {
                Box(Modifier.size(80.dp).background(Color.White).testTag("static"), contentAlignment = Alignment.Center) {
                    val offset = travel.value
                    Box(Modifier.size(32.dp).doubleShadowDrop(rounded, offset, offset * 2).background(Color.Red, rounded))
                }
                Box(Modifier.size(80.dp).background(Color.White).testTag("animated"), contentAlignment = Alignment.Center) {
                    val shadow = remember(shape) {
                        Modifier.animatedDoubleShadowDrop(shape, offset = { travel.value }, blur = { travel.value * 2 })
                    }
                    Box(Modifier.size(32.dp).then(shadow).background(Color.Red, rounded))
                }
            }
        }
        fun pixels(tag: String): IntArray {
            val image = compose.onNodeWithTag(tag).captureToImage()
            return IntArray(image.width * image.height).also { image.readPixels(it) }
        }
        assertArrayEquals(pixels("static"), pixels("animated"))
        val initialOutlines = outlineCount
        compose.runOnIdle { travel.value = 2.dp }
        assertArrayEquals(pixels("static"), pixels("animated"))
        compose.runOnIdle { travel.value = 0.dp }
        assertArrayEquals(pixels("static"), pixels("animated"))
        assertEquals(initialOutlines, outlineCount)
    }
}
