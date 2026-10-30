package app.n_zik.android.components.navigation.header

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeaderControlsBackdropTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `opaque native header controls never reveal covered page artwork`() {
        val nativeColor = mutableStateOf(Color(0xFF242424))
        val pageColor = mutableStateOf(Color(0xFFFF8800))
        compose.setContent {
            val page = rememberCanvasBackdrop { drawRect(pageColor.value) }
            CompositionLocalProvider(LocalGlassBackdrop provides page) {
                val source = requireNotNull(rememberHeaderControlsBackdrop(true, false, nativeColor.value))
                Box(Modifier.size(100.dp).background(nativeColor.value)) {
                    Box(Modifier.size(64.dp).testTag("control")
                        .drawPlainBackdrop(source, { RectangleShape }, effects = {}))
                }
            }
        }
        assertSample(nativeColor.value)
        compose.runOnIdle { pageColor.value = Color.Magenta }
        assertSample(nativeColor.value)
        compose.runOnIdle { nativeColor.value = Color(0xFF123456) }
        assertSample(nativeColor.value)
    }

    @Test fun `actual Top glass and disabled header glass retain inherited optical source`() {
        val topGlass = mutableStateOf(true)
        val headerGlass = mutableStateOf(true)
        val pageColor = mutableStateOf(Color.Cyan)
        var inherited: Backdrop? = null
        var observed: Backdrop? = null
        compose.setContent {
            val page = rememberCanvasBackdrop { drawRect(pageColor.value) }
            inherited = page
            CompositionLocalProvider(LocalGlassBackdrop provides page) {
                val source = rememberHeaderControlsBackdrop(headerGlass.value, topGlass.value, Color.Black)
                observed = source
                Box(Modifier.size(64.dp).testTag("control")
                    .drawPlainBackdrop(requireNotNull(source), { RectangleShape }, effects = {}))
            }
        }
        compose.runOnIdle { assertSame(inherited, observed) }
        assertSample(Color.Cyan)
        compose.runOnIdle { pageColor.value = Color.Green }
        assertSample(Color.Green)
        compose.runOnIdle { headerGlass.value = false; topGlass.value = false }
        compose.runOnIdle { assertSame(inherited, observed) }
    }

    private fun assertSample(expected: Color) {
        val pixels = compose.onNodeWithTag("control").captureToImage().toPixelMap()
        val actual = pixels[pixels.width / 2, pixels.height / 2]
        assertEquals(expected.red, actual.red, .015f)
        assertEquals(expected.green, actual.green, .015f)
        assertEquals(expected.blue, actual.blue, .015f)
    }
}
