package app.n_zik.android.components.glass

import android.app.Application
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.menu.GlassDropdownMenu
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Floating dropdowns need a closed perimeter even though attached sheets stay open below. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DropdownClosedBoundaryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `shared optical dropdown has a visible lower rim and rounded lower corners`() =
        assertClosedBoundary(shared = true, quality = GlassQuality.Balanced)

    @Test fun `shared tint dropdown has a visible lower rim and rounded lower corners`() =
        assertClosedBoundary(shared = true, quality = GlassQuality.Performance)

    @Test fun `shared native contour dropdown has a visible lower rim and rounded lower corners`() =
        assertClosedBoundary(shared = true, quality = GlassQuality.Performance, contourOnly = true)

    @Test fun `popup tint dropdown has a visible lower rim and rounded lower corners`() =
        assertClosedBoundary(shared = false, quality = GlassQuality.Performance)

    @Test fun `popup native contour dropdown has a visible lower rim and rounded lower corners`() =
        assertClosedBoundary(shared = false, quality = GlassQuality.Performance, contourOnly = true)

    private fun assertClosedBoundary(shared: Boolean, quality: GlassQuality, contourOnly: Boolean = false) {
        compose.setContent {
            Fixture(shared, quality, contourOnly) { host ->
                Box(Modifier.absoluteOffset(40.dp, 80.dp)) {
                    GlassDropdownMenu(true, {}, Color(0xFF303030), RoundedCornerShape(24.dp),
                        modifier = Modifier.testTag("dropdown.pane")) {
                        Box(Modifier.size(180.dp, 96.dp))
                    }
                }
                if (shared) GlassOverlayHost(host)
            }
        }
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("dropdown.pane").captureToImage().toPixelMap()
        fun brightness(x: Int, y: Int) = pixels[x, y].let { (it.red + it.green + it.blue) / 3f }
        val center = pixels.width / 2
        val fill = brightness(center, pixels.height / 2)
        val lowerRim = (0..2).maxOf { brightness(center, pixels.height - 1 - it) }
        assertTrue("Floating dropdown must close its lower perimeter: shared=$shared quality=$quality contour=$contourOnly fill=$fill rim=$lowerRim",
            lowerRim > fill + .04f)
        for (x in listOf(2, pixels.width - 3)) {
            assertTrue("Floating dropdown must retain its rounded lower corners: shared=$shared x=$x",
                brightness(x, pixels.height - 3) < fill - .05f)
        }
    }

    @Test fun `long shared dropdown near lower edge keeps its final action reachable`() =
        assertLastActionReachable(shared = true, keyboard = false)

    @Test fun `long popup near lower edge keeps its final action reachable`() =
        assertLastActionReachable(shared = false, keyboard = false)

    @Test @Config(sdk = [33])
    fun `long shared dropdown above keyboard keeps its final action reachable`() =
        assertLastActionReachable(shared = true, keyboard = true)

    private fun assertLastActionReachable(shared: Boolean, keyboard: Boolean) {
        var view: View? = null
        var viewport = IntSize.Zero
        compose.setContent {
            Fixture(shared, GlassQuality.Performance) { host ->
                view = LocalView.current
                Box(Modifier.fillMaxSize().onSizeChanged { viewport = it }) {
                    val trigger = remember { GlassMenuTriggerState() }
                    // The anchor leaves room for several rows while requiring the long menu to scroll.
                    Box(Modifier.absoluteOffset(240.dp, if (keyboard) 140.dp else 440.dp)) {
                        GlassMenuTrigger(trigger, RoundedCornerShape(24.dp)) { Box(Modifier.size(48.dp)) }
                        GlassDropdownMenu(true, {}, Color(0xFF303030), RoundedCornerShape(24.dp),
                            trigger = trigger, modifier = Modifier.testTag("dropdown.scroll")) {
                            repeat(30) { index ->
                                DropdownMenuItem(text = { Text("Action $index") }, onClick = {})
                            }
                        }
                    }
                    if (shared) GlassOverlayHost(host)
                }
            }
        }
        compose.waitForIdle()
        val keyboardHeight = if (keyboard) viewport.height / 2 else 0
        if (keyboard) {
            compose.runOnIdle {
                ViewCompat.dispatchApplyWindowInsets(requireNotNull(view), WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboardHeight))
                    .setVisible(WindowInsetsCompat.Type.ime(), true).build())
            }
            compose.waitForIdle()
        }
        compose.onNodeWithText("Action 29").performScrollTo().assertIsDisplayed()
        val pane = compose.onNodeWithTag("dropdown.scroll").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithText("Action 29").fetchSemanticsNode().boundsInRoot
        val safeBottom = viewport.height - keyboardHeight
        assertTrue("The lower boundary remains above system/keyboard area: $pane safeBottom=$safeBottom",
            pane.bottom <= safeBottom + 1f)
        assertTrue("The last action remains inside the complete menu body: $last / $pane",
            last.top >= pane.top && last.bottom <= pane.bottom)
    }

    @Composable private fun Fixture(
        shared: Boolean,
        quality: GlassQuality,
        contourOnly: Boolean = false,
        content: @Composable (GlassMenuHostState) -> Unit,
    ) {
        val shape = RoundedCornerShape(24.dp)
        val backdrop = rememberCanvasBackdrop { drawRect(Color.Black) }
        val host = remember { GlassMenuHostState() }.apply { this.backdrop = backdrop }
        val settings = GlassSettings(quality = quality, film = 1f, blur = 0f,
            vibrancy = 0f, depth = 0f, amount = 0f, rim = 1f, tintStrength = 0f,
            menuDimming = 0f, areaModes = if (contourOnly)
                mapOf(GlassArea.Dropdowns to GlassAreaMode.ContourOnly) else emptyMap())
        CompositionLocalProvider(
            LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
            LocalGlassPreviewSettings provides settings,
            LocalGlassMenuHost provides if (shared) host else null,
            LocalGlassBackdrop provides backdrop,
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) { content(host) }
        }
    }
}
