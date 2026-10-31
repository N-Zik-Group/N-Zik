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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.BorderStroke
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
import app.n_zik.android.components.ui.screens.rewind.rewindCaptureMode
import app.n_zik.android.components.ui.screens.rewind.rewindShareCaptureActive
import app.n_zik.android.components.ui.screens.rewind.rewindImageDecorationPolicy
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

    @Test fun `actual Rewind deck policy suppresses image contours during either export path`() {
        var master by mutableStateOf(false)
        composeRule.setContent {
            CompositionLocalProvider(LocalGlassPreviewSettings provides GlassSettings(enabled = master, rim = 1f)) {
                CompositionLocalProvider(rewindImageDecorationPolicy()) {
                    Box(Modifier.size(60.dp).testTag("rewindImage")
                        .glassImageRim(RectangleShape).background(Color.Blue))
                }
            }
        }
        fun pixels() = composeRule.onNodeWithTag("rewindImage").captureToImage().toPixelMap()
        val original = pixels()
        try {
            composeRule.runOnIdle { master = true }
            val live = pixels()
            assertNotEquals(original[0, original.height / 2], live[0, live.height / 2])
            for (capture in listOf(rewindCaptureMode, rewindShareCaptureActive)) {
                composeRule.runOnIdle { capture.value = true }
                val exported = pixels()
                for (y in 0 until original.height) for (x in 0 until original.width)
                    assertEquals(original[x, y], exported[x, y])
                composeRule.runOnIdle { capture.value = false }
                val restored = pixels()
                assertNotEquals(original[0, original.height / 2], restored[0, restored.height / 2])
            }
        } finally {
            composeRule.runOnIdle { rewindCaptureMode.value = false; rewindShareCaptureActive.value = false }
        }
    }

    @Test fun `capture suppression preserves image pixels and restores the original native border`() {
        var settings by mutableStateOf(GlassSettings(enabled = false, rim = 1f))
        var decorate by mutableStateOf(true)
        var useNativeBorder by mutableStateOf(false)
        val fill = Color(0xFF165080)
        composeRule.setContent {
            CompositionLocalProvider(LocalGlassPreviewSettings provides settings,
                LocalGlassImageDecorationsEnabled provides decorate) {
                Box(Modifier.size(60.dp).testTag("captureImage")
                    .then(if (useNativeBorder) Modifier.glassImageBorder(RectangleShape,
                        BorderStroke(2.dp, Color.Red)) else Modifier.glassImageRim(RectangleShape))
                    .background(fill))
            }
        }
        fun pixels() = composeRule.onNodeWithTag("captureImage").captureToImage().toPixelMap()
        val native = pixels()
        composeRule.runOnIdle { settings = settings.copy(enabled = true) }
        val live = pixels()
        assertNotEquals(native[0, native.height / 2], live[0, live.height / 2])
        composeRule.runOnIdle { decorate = false }
        val captured = pixels()
        for (y in 0 until native.height) for (x in 0 until native.width)
            assertEquals(native[x, y], captured[x, y])
        composeRule.runOnIdle { useNativeBorder = true; settings = settings.copy(enabled = false) }
        val nativeBorder = pixels()
        composeRule.runOnIdle { settings = settings.copy(enabled = true) }
        val capturedBorder = pixels()
        for (y in 0 until nativeBorder.height) for (x in 0 until nativeBorder.width)
            assertEquals(nativeBorder[x, y], capturedBorder[x, y])
        composeRule.runOnIdle { decorate = true }
        val liveBorder = pixels()
        assertNotEquals(nativeBorder[0, nativeBorder.height / 2], liveBorder[0, liveBorder.height / 2])
    }

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
