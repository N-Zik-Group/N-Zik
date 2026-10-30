package app.n_zik.android.components.glass

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassImageRimTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `rim preserves native image interior and follows live rectangle rounded and circle shapes`() {
        var shape: Shape by mutableStateOf(RectangleShape)
        var enabled by mutableStateOf(false)
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setPixel(x, y, if ((x / 4 + y / 4) % 2 == 0) {
                        android.graphics.Color.rgb(22, 80, 128)
                    } else android.graphics.Color.rgb(160, 210, 40))
                }
            }
        }.asImageBitmap()
        composeRule.setContent {
            Box(Modifier.size(80.dp).background(Color.Black).testTag("image")) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).clip(shape).glassImageRim(
                        GlassSettings(enabled = enabled, rim = 1f), shape, Color.Unspecified,
                    ),
                )
            }
        }
        listOf(RectangleShape, RoundedCornerShape(12.dp), CircleShape,
            glassImageShapeIntersection(RoundedCornerShape(12.dp),
                RoundedCornerShape(topStart = 30.dp, bottomEnd = 30.dp))).forEach { current ->
            composeRule.runOnIdle {
                shape = current
                enabled = false
            }
            val original = composeRule.onNodeWithTag("image").captureToImage().toPixelMap()
            composeRule.runOnIdle { enabled = true }
            val pixels = composeRule.onNodeWithTag("image").captureToImage().toPixelMap()
            val middle = pixels.height / 2
            // A whole interior region stays exact, rather than checking only one flat pixel.
            for (y in pixels.height / 3 until pixels.height * 2 / 3) {
                for (x in pixels.width / 3 until pixels.width * 2 / 3) {
                    assertEquals(original[x, y], pixels[x, y])
                }
            }
            assertNotEquals(original[0, middle], pixels[0, middle])
            if (current != RectangleShape) assertEquals(Color.Black, pixels[0, 0])
        }
    }

    @Test fun `master disable area disable and zero rim retain every source pixel while selected tint only colors edge`() {
        var settings by mutableStateOf(GlassSettings(enabled = false, rim = 1f))
        val imageColor = Color(0xFF165080)
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(22, 80, 128))
        }.asImageBitmap()
        composeRule.setContent {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(80.dp).testTag("image").glassImageRim(
                    settings, RectangleShape, Color.Red,
                ),
            )
        }
        listOf(settings, GlassSettings(enabled = true, imageRims = false),
            GlassSettings(enabled = true, rim = 0f)).forEach { current ->
            composeRule.runOnIdle { settings = current }
            val pixels = composeRule.onNodeWithTag("image").captureToImage().toPixelMap()
            for (y in 0 until pixels.height) {
                for (x in 0 until pixels.width) assertEquals(imageColor, pixels[x, y])
            }
        }
        composeRule.runOnIdle {
            settings = GlassSettings(enabled = true, rim = 1f, tintStrength = 1f,
                source = GlassTintSource.Custom)
        }
        val pixels = composeRule.onNodeWithTag("image").captureToImage().toPixelMap()
        assertEquals(imageColor, pixels[pixels.width / 2, pixels.height / 2])
        assertNotEquals(imageColor, pixels[pixels.width / 2, 0])
        assertTrue(pixels[pixels.width / 2, 0].red > imageColor.red)
        assertTrue(pixels[pixels.width / 2, 0].green < imageColor.green)
    }
}
