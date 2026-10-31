package app.n_zik.android.components.onboarding

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassRippleProvider
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingSecondaryButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `secondary variants follow roundness and keep native geometry across modes`() {
        val wrapped = mutableStateOf(false)
        val outlined = mutableStateOf(false)
        val radius = mutableStateOf(0)
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        var clicks = 0
        compose.setContent {
            val shape = RoundedCornerShape(radius.value.dp)
            val settings = GlassSettings(enabled = true, jellyEnabled = false,
                areaModes = mapOf(GlassArea.ActionButtons to mode.value))
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                Typography.fallback, thumbnailShape = shape, artistThumbnailShape = shape, uiRoundnessShape = shape),
                LocalGlassPreviewSettings provides settings) {
                GlassRippleProvider(settings) {
                    Box(Modifier.size(120.dp, 80.dp).background(Color.Black).testTag("canvas"), Alignment.Center) {
                        val glyph: @Composable RowScope.() -> Unit = {
                            Box(Modifier.size(12.dp).background(Color.Magenta))
                        }
                        Box(Modifier.testTag("surface")) {
                            if (wrapped.value) OnboardingSecondaryButton({ clicks++ }, outlined.value, glyph)
                            else if (outlined.value) OutlinedButton({}, shape = shape,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colorPalette().text),
                                border = BorderStroke(1.dp, colorPalette().textSecondary), content = glyph)
                            else TextButton({}, shape = shape,
                                colors = ButtonDefaults.buttonColors(contentColor = colorPalette().text), content = glyph)
                        }
                    }
                }
            }
        }
        for (outline in listOf(false, true)) for (corner in listOf(0, 48)) {
            compose.runOnIdle { wrapped.value = false; outlined.value = outline; radius.value = corner; mode.value = GlassAreaMode.Disabled }
            val canvas = compose.onNodeWithTag("canvas")
            val native = canvas.captureToImage().toPixelMap()
            val expectedBounds = compose.onAllNodes(hasClickAction())[0].fetchSemanticsNode().boundsInRoot
            for (variant in listOf(GlassAreaMode.Disabled, GlassAreaMode.ContourOnly, GlassAreaMode.GlassOnly)) {
                compose.runOnIdle { wrapped.value = true; mode.value = variant }
                val action = compose.onAllNodes(hasClickAction())[0]
                assertEquals(expectedBounds, action.fetchSemanticsNode().boundsInRoot)
                val idle = canvas.captureToImage().toPixelMap()
                if (variant == GlassAreaMode.Disabled) {
                    for (y in 0 until native.height) for (x in 0 until native.width)
                        assertEquals("outline=$outline radius=$corner at $x,$y", native[x, y], idle[x, y])
                }
                compose.mainClock.autoAdvance = false
                action.performTouchInput { down(center) }
                shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
                compose.mainClock.advanceTimeBy(350)
                val held = canvas.captureToImage().toPixelMap()
                val canvasBounds = canvas.fetchSemanticsNode().boundsInRoot
                val actionBounds = if (variant == GlassAreaMode.GlassOnly)
                    compose.onNodeWithTag("surface").fetchSemanticsNode().boundsInRoot
                    else action.fetchSemanticsNode().boundsInRoot
                val scale = held.height / canvasBounds.height
                val left = (actionBounds.left - canvasBounds.left) * scale
                val right = (actionBounds.right - canvasBounds.left) * scale
                val visualHeight = if (variant == GlassAreaMode.GlassOnly) actionBounds.height * scale
                    else 40f * held.height / 80f
                val top = (held.height - visualHeight) / 2f
                val bottom = top + visualHeight
                val cornerRadius = minOf(corner * held.height / 80f, (right - left) / 2f, visualHeight / 2f)
                var changed = 0
                for (y in 0 until held.height) for (x in 0 until held.width) {
                    if (held[x, y] != idle[x, y]) {
                        changed++
                        val diagnostic = "outline=$outline radius=$corner mode=$variant at $x,$y surface=$actionBounds canvas=$canvasBounds"
                        val px = x + .5f
                        val py = y + .5f
                        assertTrue("Feedback escaped branch surface: $diagnostic",
                            px >= left && px < right && py >= top && py < bottom)
                        val dx = px - px.coerceIn(left + cornerRadius, right - cornerRadius)
                        val dy = py - py.coerceIn(top + cornerRadius, bottom - cornerRadius)
                        // Include the one-pixel raster antialiasing fringe of the rounded outline.
                        assertTrue("Feedback escaped branch roundness: $diagnostic",
                            dx * dx + dy * dy <= (cornerRadius + .75f) * (cornerRadius + .75f))
                    }
                }
                assertTrue("Feedback appears for $outline/$corner/$variant", changed > 0)
                action.performTouchInput { up() }
                compose.mainClock.advanceTimeBy(1600)
                compose.mainClock.autoAdvance = true
            }
        }
        compose.runOnIdle { assertEquals(12, clicks) }
    }
}
