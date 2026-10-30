package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
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
class GlassOverlayBackdropTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `overlay source includes header while chrome retains independent page source`() {
        val host = GlassMenuHostState()
        val includeHeader = mutableStateOf(false)
        val header = Color.Red
        val page = Color.Blue
        val missingCapture = Color.Green
        composeRule.setContent {
            val pageBackdrop = rememberLayerBackdrop()
            val overlayBackdrop = rememberLayerBackdrop()
            val captureHeader = includeHeader.value
            SideEffect {
                host.backdrop = pageBackdrop
                host.overlayBackdrop = if (captureHeader) overlayBackdrop else null
            }
            Box(Modifier.size(240.dp)) {
                Scaffold(
                    modifier = Modifier.fillMaxSize().layerBackdrop(overlayBackdrop),
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0),
                    topBar = { Box(Modifier.fillMaxWidth().height(60.dp).background(header)) },
                ) { padding ->
                    Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop).padding(padding).background(page))
                }
                val sampledBackdrop = host.modalBackdrop as? LayerBackdrop
                // An opaque underlay makes missing source pixels visible instead of
                // accidentally showing the real, uncaptured header underneath.
                Box(Modifier.fillMaxSize().background(missingCapture).drawBehind {
                    sampledBackdrop?.let { drawLayer(it.graphicsLayer) }
                }.testTag("sample"))
            }
        }
        val pagePixels = composeRule.onNodeWithTag("sample").captureToImage().toPixelMap()
        assertEquals("the page-only recording has no topBar pixels",
            missingCapture.toArgb(), pagePixels[pagePixels.width / 2, pagePixels.height / 8].toArgb())
        composeRule.runOnIdle {
            assertSame(host.backdrop, host.modalBackdrop)
            includeHeader.value = true
        }
        val fullPixels = composeRule.onNodeWithTag("sample").captureToImage().toPixelMap()
        assertEquals("expanded overlays can sample the header",
            header.toArgb(), fullPixels[fullPixels.width / 2, fullPixels.height / 8].toArgb())
        assertEquals("the same recording continues into the page",
            page.toArgb(), fullPixels[fullPixels.width / 2, fullPixels.height / 2].toArgb())
        composeRule.runOnIdle {
            assertSame(host.overlayBackdrop, host.modalBackdrop)
            assertNotSame("header glass must never sample its own containing capture", host.backdrop, host.modalBackdrop)
        }
    }
}
