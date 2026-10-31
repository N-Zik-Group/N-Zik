package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import app.n_zik.android.components.navigation.header.HeaderIcon
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassMenuTriggerState
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LiquidButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmallActionRoundnessTest {
    @get:Rule val compose = createComposeRule()
    private val settings = GlassSettings(film = 0f, rim = 0f, blur = 0f,
        vibrancy = 0f, depth = 0f, amount = 0f)

    @Test fun `default LiquidButton uses the exact production UI shape at zero eight and forty eight`() {
        val radius = mutableStateOf(0f)
        val trigger = GlassMenuTriggerState()
        var expected: Shape? = null
        compose.setContent {
            val appearance = Appearance.restore(listOf(null, null, 12f, radius.value, 48f))
            expected = appearance.uiRoundnessShape
            CompositionLocalProvider(LocalAppearance provides appearance) {
                LiquidButton({}, null, Modifier.size(40.dp), settings = settings,
                    menuTrigger = trigger, contentHeight = 40.dp, contentPadding = 0.dp) {}
            }
        }
        for (value in listOf(0f, 8f, 48f)) {
            compose.runOnIdle { radius.value = value }
            compose.waitForIdle()
            compose.runOnIdle {
                assertSame("Default actions must not convert the UI .4 cap into the search .5 cap", expected, trigger.shape)
                val outline = requireNotNull(trigger.shape).createOutline(androidx.compose.ui.geometry.Size(40f,40f), LayoutDirection.Ltr, Density(1f))
                val actual = when (outline) {
                    is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
                    is Outline.Rectangle -> 0f
                    else -> error("Expected ordinary rounded UI outline")
                }
                assertEquals(minOf(value, 16f), actual, .01f)
            }
        }
    }

    @Test fun `actual header back action explicitly keeps UI shape and original canvas in both modes`() {
        val radius = mutableStateOf(0f)
        val glass = mutableStateOf(false)
        val trigger = GlassMenuTriggerState()
        var expected: Shape? = null
        var clicks = 0
        compose.setContent {
            val appearance = Appearance.restore(listOf(null, null, 12f, radius.value, 48f))
            expected = appearance.uiRoundnessShape
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalGlassPreviewSettings provides settings.copy(enabled = glass.value)) {
                HeaderIcon(R.drawable.chevron_back, tint = Color.Transparent,
                    menuTrigger = trigger, modifier = Modifier.testTag("header.back"), onClick = { clicks++ })
            }
        }
        for (enabled in listOf(false, true)) for (value in listOf(0f, 8f, 48f)) {
            compose.runOnIdle { glass.value = enabled; radius.value = value }
            val node = compose.onNodeWithTag("header.back")
            val size = if (enabled) 40.dp else 48.dp
            node.assertWidthIsEqualTo(size).assertHeightIsEqualTo(size)
            compose.runOnIdle {
                assertSame("The actual back action and its menu trigger must share the ordinary UI outline", expected, trigger.shape)
                val outline = requireNotNull(trigger.shape).createOutline(
                    androidx.compose.ui.geometry.Size(size.value, size.value), LayoutDirection.Ltr, Density(1f))
                val actual = when (outline) {
                    is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
                    is Outline.Rectangle -> 0f
                    else -> error("Expected UI-rounded header action")
                }
                assertEquals(minOf(value, size.value * .4f), actual, .01f)
            }
            val before = clicks
            for (edge in 0..3) node.performTouchInput {
                click(when (edge) {
                    0 -> Offset(1f, center.y)
                    1 -> Offset(width - 1f, center.y)
                    2 -> Offset(center.x, 1f)
                    else -> Offset(center.x, height - 1f)
                })
            }
            compose.runOnIdle { assertEquals(before + 4, clicks) }
        }
    }

    @Test fun `actual mini skip and play plus three top actions retain UI corners instead of circular Glass masks`() {
        val radius = mutableStateOf(0f)
        val action = mutableStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val appearance = Appearance.restore(listOf(null, null, 12f, radius.value, 48f))
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Box(Modifier.size(96.dp).background(Color.Black).testTag("small.action.host"), contentAlignment = Alignment.Center) {
                    androidx.compose.runtime.key(radius.value, action.value) {
                    when (action.value) {
                        0 -> GlassMiniPlayerIconButton(R.drawable.play_skip_back, Color.Transparent, {}, "target", settings = settings)
                        1 -> GlassMiniPlayerPlayPauseButton({}, Color.Black, "target", settings = settings) {
                            // Real Play always paints its centered24dp glyph (or buffering spinner).
                            Image(painterResource(R.drawable.play), null, colorFilter = ColorFilter.tint(Color.Blue),
                                modifier = Modifier.align(Alignment.Center).size(24.dp))
                        }
                        else -> PlayerTopAction(listOf(R.drawable.chevron_down, R.drawable.ic_launcher_monochrome,
                            R.drawable.ellipsis_vertical)[action.value - 2], Color.Transparent, 0f, {}, settings,
                            contentDescription = "target")
                    }
                    }
                }
            }
        }
        for (value in listOf(0f, 8f, 48f)) for (index in 0..4) {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { radius.value = value; action.value = index }
            compose.waitForIdle()
            // Flush both recomposition and Android recording before freezing the press clock.
            ShadowLooper.idleMainLooper(32, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(160)
            compose.waitForIdle()
            val node = compose.onNodeWithContentDescription("target")
            val size = if (index < 2) 42.dp else 40.dp
            node.assertWidthIsEqualTo(size).assertHeightIsEqualTo(size)
            // Capture the real parent frame: a control's semantics node can be inside the
            // ancestor graphics layer that draws its shared feedback (notably Play's content Box).
            val host = compose.onNodeWithTag("small.action.host")
            val hostBounds = host.fetchSemanticsNode().boundsInRoot
            val nodeBounds = node.fetchSemanticsNode().boundsInRoot
            val before = host.captureToImage().toPixelMap()
            val baselineCenterX = (nodeBounds.center.x - hostBounds.left).toInt()
            val baselineCenterY = (nodeBounds.center.y - hostBounds.top).toInt()
            if (index == 1) assertEquals("Play baseline must already contain its real blue glyph before down",
                Color.Blue, before[baselineCenterX,baselineCenterY])
            compose.mainClock.autoAdvance = false
            node.performTouchInput { down(center) }
            ShadowLooper.idleMainLooper(150, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(900)
            val held = host.captureToImage().toPixelMap()
            // At full press the shared4dp expansion puts this sample inside the ordinary .4 UI
            // corner, but outside a .5 circular bubble. This exercises each real action renderer.
            val inset = with(compose.density) { 3.dp.roundToPx() }
            val x = (nodeBounds.left - hostBounds.left).toInt() + inset
            val y = (nodeBounds.top - hostBounds.top).toInt() + inset
            val centerX = (nodeBounds.center.x - hostBounds.left).toInt()
            val centerY = (nodeBounds.center.y - hostBounds.top).toInt()
            val changed = (0 until held.height).sumOf { py -> (0 until held.width).count { px -> before[px,py] != held[px,py] } }
            assertTrue("action=$index UI=$value must light its ordinary UI corner, not remain circular: " +
                "${before[x,y]} -> ${held[x,y]}, center=${before[centerX,centerY]} -> ${held[centerX,centerY]}, " +
                "changedPixels=$changed canvas=$nodeBounds", held[x,y].red > before[x,y].red + .01f)
            node.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1600)
        }
    }
}
