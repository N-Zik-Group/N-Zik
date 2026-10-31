package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassOverlaySceneCaptureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `real scene producer skips off and Tint capture and resumes a live role source`() {
        val settings = mutableStateOf(GlassSettings(enabled = false))
        val color = mutableStateOf(Color.Red)
        var source: Backdrop? = null
        var role = GlassScope.Shared
        compose.setContent {
            CompositionLocalProvider(LocalGlassPreviewSettings provides settings.value,
                LocalGlassBackdrop provides null) {
                GlassOverlayScene(Color.Black, Modifier.size(60.dp).testTag("scene"),
                    scope = GlassScope.Lyrics,
                    scene = { Box(Modifier.fillMaxSize().background(color.value)) }) {
                    val current = LocalGlassContainerBackdrop.current
                    val currentRole = LocalGlassScope.current
                    SideEffect { source = current; role = currentRole }
                }
            }
        }
        fun visible(expected: Color) {
            val pixels = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
            assertEquals(expected, pixels[pixels.width / 2, pixels.height / 2])
        }
        visible(Color.Red)
        compose.runOnIdle { assertNull(source); assertEquals(GlassScope.Lyrics, role) }
        compose.runOnIdle {
            settings.value = GlassSettings(quality = GlassQuality.Performance)
            color.value = Color.Blue
        }
        visible(Color.Blue)
        compose.runOnIdle { assertNull(source) }
        compose.runOnIdle { settings.value = GlassSettings(quality = GlassQuality.Balanced) }
        visible(Color.Blue)
        var version = 0
        compose.runOnIdle {
            val layer = source as LayerBackdrop
            assertTrue(layer.hasRecording)
            version = layer.contentVersion
            color.value = Color.Green
        }
        visible(Color.Green)
        compose.runOnIdle { assertTrue((source as LayerBackdrop).contentVersion > version) }
        compose.runOnIdle { settings.value = GlassSettings(enabled = false) }
        visible(Color.Green)
        compose.runOnIdle { assertNull(source) }
    }
}
