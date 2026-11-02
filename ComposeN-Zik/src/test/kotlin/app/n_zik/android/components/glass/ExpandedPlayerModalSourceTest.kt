package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
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
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
import app.n_zik.android.components.menu.GlassDropdownMenu
import app.n_zik.android.components.player.playerGlassSourceCaptureActive
import app.n_zik.android.components.player.playerGlassSceneCaptureActive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExpandedPlayerModalSourceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `dropdown over performance player samples actual player and queue instead of application`() =
        assertPlayerOwnsSource(Overlay.Dropdown, GlassQuality.Performance, inheritedOptics = true, transition = 1f)

    @Test fun `dialog over player with inhibited control optics retains actual player and queue source`() =
        assertPlayerOwnsSource(Overlay.Dialog, GlassQuality.Balanced, inheritedOptics = false, transition = 1f)

    @Test fun `sheet over expanded player at deployment fade start retains actual player and queue source`() =
        assertPlayerOwnsSource(Overlay.Sheet, GlassQuality.Balanced, inheritedOptics = true, transition = 0f)

    private enum class Overlay { Dropdown, Dialog, Sheet }

    private fun assertPlayerOwnsSource(overlay: Overlay, quality: GlassQuality, inheritedOptics: Boolean, transition: Float) {
        val host = GlassMenuHostState()
        lateinit var completedPlayer: LayerBackdrop
        val pageColor = mutableStateOf(Color.Red)
        val queueColor = mutableStateOf(Color.Green)
        compose.setContent {
            val player = rememberLayerBackdrop()
            completedPlayer = player
            val shape = RoundedCornerShape(0.dp)
            // The menu has optical material even if its owner's controls use a cheaper capture policy.
            val menuSettings = GlassSettings(quality = GlassQuality.Balanced, film = 0f, rim = 0f,
                menuDimming = 0f, dialogDimming = 0f, blur = 0f, depth = 0f, amount = 0f)
            val playerSettings = GlassSettings(quality = quality)
            val captureBackground = playerGlassSourceCaptureActive(playerSettings,
                mutableStateOf(transition), inheritedOptics)
            val captureScene = playerGlassSceneCaptureActive(playerSettings,
                expanded = true, backgroundCapture = captureBackground)
            CompositionLocalProvider(
                LocalGlassMenuHost provides host,
                LocalGlassPreviewSettings provides menuSettings,
                LocalAppearance provides Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape),
            ) {
                Box(Modifier.size(320.dp).testTag("source.scene")) {
                    GlassSceneContent(host, enabled = true) {
                        Box(Modifier.fillMaxSize().background(pageColor.value))
                        CompositionLocalProvider(LocalGlassOverlayBackdrop provides
                            if (captureScene) player else LocalGlassOverlayBackdrop.current) {
                            Box(Modifier.fillMaxSize().glassPlayerSceneSource(player,
                                active = captureScene, fullyCovering = true).background(Color.Blue)) {
                                Box(Modifier.align(Alignment.BottomEnd).size(160.dp).background(queueColor.value))
                                when (overlay) {
                                    Overlay.Dropdown -> Box {
                                        GlassDropdownMenu(true, {}, Color.Transparent, shape) { Samples() }
                                    }
                                    Overlay.Dialog -> GlassDialogWindow(true, {}, DialogProperties()) { Samples() }
                                    Overlay.Sheet -> SharedGlassMenuSheet(true, {},
                                        rememberModalBottomSheetState(skipPartiallyExpanded = true),
                                        shape = shape, scrimColor = Color.Transparent) { Samples() }
                                }
                            }
                        }
                    }
                    GlassOverlayHost(host)
                    GlassDialogOverlayHost(host)
                }
            }
        }
        assertColor("source.player", Color.Blue)
        assertColor("source.queue", Color.Green)
        var coveredSceneVersion = 0
        compose.runOnIdle {
            assertSame("Expanded player owns the source boundary even when its control capture is disabled",
                completedPlayer, host.modalBackdrop)
            coveredSceneVersion = (requireNotNull(host.sceneBackdrop) as LayerBackdrop).contentVersion
        }
        compose.runOnIdle { pageColor.value = Color.Yellow; queueColor.value = Color.Cyan }
        assertColor("source.player", Color.Blue)
        assertColor("source.queue", Color.Cyan)
        compose.runOnIdle {
            assertEquals("Covered application is excluded from modal scene recording", coveredSceneVersion,
                (requireNotNull(host.sceneBackdrop) as LayerBackdrop).contentVersion)
        }
    }

    @Composable private fun Samples() {
        Box(Modifier.size(240.dp)) {
            Sample("source.player", Modifier.align(Alignment.TopStart))
            Sample("source.queue", Modifier.align(Alignment.BottomEnd))
        }
    }

    @Composable private fun Sample(tag: String, modifier: Modifier) {
        val source = requireNotNull(LocalGlassBackdrop.current)
        Box(modifier.size(40.dp).background(Color.Magenta)
            .drawPlainBackdrop(source, { RectangleShape }, effects = {}).testTag(tag))
    }

    private fun assertColor(tag: String, expected: Color) {
        val scene = compose.onNodeWithTag("source.scene")
        val sceneBounds = scene.fetchSemanticsNode().boundsInRoot
        val sampleBounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val pixels = scene.captureToImage().toPixelMap()
        val x = (sampleBounds.center.x - sceneBounds.left).toInt().coerceIn(0, pixels.width - 1)
        val y = (sampleBounds.center.y - sceneBounds.top).toInt().coerceIn(0, pixels.height - 1)
        val actual = pixels[x, y]
        assertEquals("$tag red", expected.red, actual.red, .03f)
        assertEquals("$tag green", expected.green, actual.green, .03f)
        assertEquals("$tag blue", expected.blue, actual.blue, .03f)
    }
}
