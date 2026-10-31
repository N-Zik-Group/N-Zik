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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
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
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassToastOverlaySceneTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `toast source contains real hosted dialog then returns to identical scene source`() {
        val host = GlassMenuHostState()
        val dialogVisible = mutableStateOf(false)
        val dialogColor = mutableStateOf(Color.Red)
        val toastPresent = mutableStateOf(true)
        var scene: Backdrop? = null
        var sampled: Backdrop? = null
        compose.setContent {
            val source = rememberLayerBackdrop()
            SideEffect { scene = source; host.sceneBackdrop = source }
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box(Modifier.size(200.dp).testTag("root")) {
                    Box(Modifier.fillMaxSize().layerBackdrop(source).background(Color.Blue)) {
                        if (dialogVisible.value) GlassDialogWindow(true, {}, DialogProperties()) {
                            Box(Modifier.fillMaxSize().background(dialogColor.value))
                        }
                    }
                    GlassToastOverlayScene(source, toastPresent.value && host.dialogs.hasDialogs,
                        overlays = { GlassDialogOverlayHost(host) }) { completed ->
                        SideEffect { sampled = completed }
                        Box(Modifier.size(80.dp).background(Color.Magenta)
                            .drawPlainBackdrop(requireNotNull(completed), { RectangleShape }, effects = {}))
                    }
                }
            }
        }
        fun sample(): Color {
            // Draw the whole sibling group before the above-modal consumer samples it.
            val pixels = compose.onNodeWithTag("root").captureToImage().toPixelMap()
            return pixels[pixels.width / 5, pixels.height / 5]
        }
        assertEquals(Color.Blue, sample())
        compose.runOnIdle { assertSame(scene, sampled); dialogVisible.value = true }
        assertEquals(Color.Red, sample())
        compose.runOnIdle { assertNotSame(scene, sampled); dialogColor.value = Color.Green }
        assertEquals("An open modal stays live without a gesture", Color.Green, sample())
        compose.runOnIdle { dialogVisible.value = false }
        assertEquals(Color.Blue, sample())
        compose.runOnIdle { assertSame("No modal preserves the original source", scene, sampled) }
        // Recording can stop and restart while the dialog owner stays mounted.
        compose.runOnIdle { dialogVisible.value = true }
        assertEquals(Color.Green, sample())
        compose.runOnIdle { toastPresent.value = false }
        assertEquals(Color.Blue, sample())
        compose.runOnIdle { assertSame(scene, sampled); toastPresent.value = true }
        assertEquals(Color.Green, sample())
    }

    @Test fun `overlay group preserves transparent scene regions and retained exit layers`() {
        val retained = mutableStateOf(true)
        val closing = mutableStateOf(false)
        val topVisible = mutableStateOf(true)
        var scene: Backdrop? = null
        var sampled: Backdrop? = null
        compose.setContent {
            val source = rememberLayerBackdrop()
            SideEffect { scene = source }
            Box(Modifier.size(200.dp).testTag("root")) {
                Box(Modifier.fillMaxSize().layerBackdrop(source).background(Color.Blue))
                GlassToastOverlayScene(source, retained.value, overlays = {
                    if (retained.value) {
                        // A retained exit layer still belongs to the source after dismissal.
                        Box(Modifier.size(100.dp).background(if (closing.value) Color.Green else Color.Red))
                        if (topVisible.value) Box(Modifier.size(50.dp).background(Color.Yellow))
                    }
                }) { completed ->
                    SideEffect { sampled = completed }
                    Box(Modifier.fillMaxSize().background(Color.Magenta)
                        .drawPlainBackdrop(requireNotNull(completed), { RectangleShape }, effects = {}))
                }
            }
        }
        fun check(top: Color, middle: Color) {
            val pixels = compose.onNodeWithTag("root").captureToImage().toPixelMap()
            assertEquals(top, pixels[pixels.width / 8, pixels.height / 8])
            assertEquals(middle, pixels[pixels.width * 3 / 8, pixels.height * 3 / 8])
            assertEquals("Transparent modal regions retain the scene", Color.Blue,
                pixels[pixels.width * 3 / 4, pixels.height * 3 / 4])
        }
        check(Color.Yellow, Color.Red)
        compose.runOnIdle { closing.value = true; topVisible.value = false }
        check(Color.Green, Color.Green)
        compose.runOnIdle { assertNotSame(scene, sampled); retained.value = false }
        check(Color.Blue, Color.Blue)
        compose.runOnIdle { assertSame(scene, sampled) }
    }
}
