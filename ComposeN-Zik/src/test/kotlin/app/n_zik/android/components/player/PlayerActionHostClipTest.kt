package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.BoundedCornerSize
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
class PlayerActionHostClipTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `two real panes retain their whole contours and native host masks stay identical`() {
        val mode = mutableStateOf(GlassAreaMode.GlassOnly)
        val landscape = mutableStateOf(false)
        val roundness = mutableStateOf(0.dp)
        // Reference 1 is the historical double mask; reference 2 leaves each pane alone.
        val reference = mutableStateOf(0)
        var queueClicks = 0
        compose.setContent {
            val shape = RoundedCornerShape(BoundedCornerSize(roundness.value, .4f))
            val scene = rememberCanvasBackdrop { drawRect(Color.Red) }
            val settings = GlassSettings(areaModes = mapOf(GlassArea.ActionButtons to mode.value),
                film = 0f, rim = 0f, blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f,
                tintStrength = 0f, jellyEnabled = false)
            CompositionLocalProvider(LocalGlassContainerBackdrop provides scene,
                LocalAppearance provides Appearance(DefaultDarkColorPalette,
                    typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape)) {
                val layout = rememberPlayerActionToolbarLayout()
                val actions = listOf(PlayerActionSlot("first") {}, PlayerActionSlot("last") {})
                val outer = Modifier.requiredWidth(320.dp)
                    .requiredHeight(layout.hostHeight(90.dp + playerWhatsNextGlassExtraHeight(settings)))
                val secondShape = if (landscape.value) CircleShape else shape
                fun mask(modifier: Modifier, second: Boolean): Modifier = when (reference.value) {
                    1 -> modifier.clip(if (second) secondShape else shape)
                    2 -> modifier
                    else -> modifier.playerActionHostClip(settings, if (second) secondShape else shape)
                }
                Box(Modifier.requiredWidth(320.dp).background(Color.Black).testTag("scene")) {
                    Row(mask(mask(outer, false).playerActionQueueTap(true) { queueClicks++ }, true)
                        .testTag("host"), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.fillMaxSize(), Arrangement.SpaceAround, Alignment.CenterHorizontally) {
                            PlayerWhatsNextGlassSurface(Color.Red, settings, landscape.value, WindowInsets(0)) { surface ->
                                Box(surface.fillMaxWidth().height(40.dp))
                            }
                            PlayerActionToolbar(actions, true, settings = settings, layout = layout,
                                horizontalInsets = WindowInsets(0), landscape = landscape.value,
                                nativeBackground = Color.Red)
                        }
                    }
                }
            }
        }
        fun snapshot(mask: Int): IntArray {
            compose.runOnIdle { reference.value = mask }
            val pixels = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
            return IntArray(pixels.width * pixels.height) { index ->
                pixels[index % pixels.width, index / pixels.width].toArgb()
            }
        }
        var exposedHistoricalCut = false
        for (wide in listOf(false, true)) for (radius in listOf(0.dp, 20.dp, 96.dp)) {
            for (areaMode in GlassAreaMode.entries) {
                compose.runOnIdle { landscape.value = wide; roundness.value = radius; mode.value = areaMode }
                val expected = snapshot(if (areaMode.hasMaterial) 2 else 1)
                val actual = snapshot(0)
                assertArrayEquals("Full contours wide=$wide radius=$radius mode=$areaMode", expected, actual)
                if (areaMode.hasMaterial && !snapshot(1).contentEquals(expected)) exposedHistoricalCut = true
                compose.runOnIdle { reference.value = 0 }
                // The gap between the two independently shaped plates still belongs to the queue catcher.
                val upper = compose.onNodeWithTag("player.whatsNext.surface", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val lower = compose.onNodeWithTag(if (areaMode.hasMaterial) "player.actionToolbar.material"
                    else "player.actionToolbar.native", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val host = compose.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
                val before = queueClicks
                compose.onNodeWithTag("host").performTouchInput {
                    click(Offset(host.width / 2f, (upper.bottom + lower.top) / 2f - host.top))
                }
                compose.runOnIdle { assertEquals(before + 1, queueClicks) }
            }
        }
        assertTrue("The real shared host must reproduce the historical missing corner pixels", exposedHistoricalCut)
    }
}
