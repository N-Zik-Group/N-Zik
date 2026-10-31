package app.n_zik.android.components.ui

import android.app.Application
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import org.junit.Assume.assumeTrue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.ripple
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import app.n_zik.android.uiRoundnessShape
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassDialogIconButton
import app.n_zik.android.components.glass.GlassRippleProvider
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import org.junit.Assert.assertEquals
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
class ReservedIconActionCanvasTest {
    @get:Rule val compose = createComposeRule()
    private val shape = RoundedCornerShape(4.dp)
    private val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)

    @Test fun `all legacy overloads header and image dialog include their reserved padding in actual edge clicks`() {
        val glass = mutableStateOf(false)
        val clicks = IntArray(4)
        var parentClicks = 0
        compose.setContent {
            val settings = GlassSettings(enabled = glass.value)
            CompositionLocalProvider(LocalAppearance provides appearance, LocalGlassPreviewSettings provides settings) {
                GlassRippleProvider(settings) {
                    Row(Modifier.size(240.dp, 80.dp).clickable { parentClicks++ }, verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ clicks[0]++ }, R.drawable.play_skip_back, Color.Blue,
                            Modifier.testTag("resource").padding(8.dp).size(24.dp))
                        IconButton({ clicks[1]++ }, Icons.Filled.Computer, Color.Blue,
                            Modifier.testTag("vector").padding(8.dp).size(24.dp))
                        HeaderIconButton({ clicks[2]++ }, R.drawable.play_skip_forward, Color.Blue,
                            Modifier.testTag("header").padding(8.dp))
                        GlassDialogIconButton({ clicks[3]++ }, R.drawable.reorder, Color.Blue,
                            Modifier.testTag("dialog").padding(8.dp).size(24.dp))
                    }
                }
            }
        }
        for (enabled in listOf(false, true)) {
            compose.runOnIdle { glass.value = enabled }
            val bounds = listOf("resource", "vector", "header", "dialog").mapIndexed { index, tag ->
                val node = compose.onNodeWithTag(tag)
                node.assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
                val before = clicks[index]
                for (edge in 0..3) node.performTouchInput {
                    click(when(edge) {
                        0 -> Offset(1f, center.y)
                        1 -> Offset(width - 1f, center.y)
                        2 -> Offset(center.x, 1f)
                        else -> Offset(center.x, height - 1f)
                    })
                }
                compose.runOnIdle { assertEquals(before + 4, clicks[index]); assertEquals(0, parentClicks) }
                node.fetchSemanticsNode().boundsInRoot
            }
            bounds.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
        }
        // One action owner per control, plus the parent whose callback must never run.
        assertEquals(5, compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Test fun `native short and long toolbar variants use equal full canvases and automatic corner radii`() {
        var rootView: View? = null
        val clicks = IntArray(6)
        var parentClicks = 0
        compose.setContent {
            val view = LocalView.current
            SideEffect { rootView = view.rootView }
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalGlassPreviewSettings provides GlassSettings(enabled = false)) {
                Row(Modifier.size(320.dp, 80.dp).clickable { parentClicks++ }) {
                    TabToolBar.Icon(painterResource(R.drawable.play), modifier = Modifier.testTag("toolbar.0"),
                        onClick = { clicks[0]++ }, onLongClick = {})
                    TabToolBar.Icon(R.drawable.play, modifier = Modifier.testTag("toolbar.1"),
                        onShortClick = { clicks[1]++ }, onLongClick = {})
                    TabToolBar.Toggleable(R.drawable.play, R.drawable.pause, true,
                        modifier = Modifier.testTag("toolbar.2"), onShortClick = { clicks[2]++ }, onLongClick = {})
                    TabToolBar.Toggleable(R.drawable.play, toggleCondition = true, enabled = true,
                        modifier = Modifier.testTag("toolbar.3"), onShortClick = { clicks[3]++ }, onLongClick = {})
                    TabToolBar.Icon(painterResource(R.drawable.play), modifier = Modifier.testTag("toolbar.short.painter"),
                        onClick = { clicks[4]++ })
                    TabToolBar.Icon(iconId = R.drawable.play, modifier = Modifier.testTag("toolbar.short.resource"),
                        onClick = { clicks[5]++ })
                }
            }
        }
        fun ripples(view: View): List<RippleDrawable> = listOfNotNull(view.background as? RippleDrawable) +
            (if (view is ViewGroup) (0 until view.childCount).flatMap { ripples(view.getChildAt(it)) } else emptyList())
        val geometry = (0..5).map { index ->
            val action = if (index < 4) compose.onNodeWithTag("toolbar.$index") else
                compose.onNode(androidx.compose.ui.test.hasClickAction() and
                    androidx.compose.ui.test.hasAnyDescendant(androidx.compose.ui.test.hasTestTag(
                        if (index == 4) "toolbar.short.painter" else "toolbar.short.resource")) and
                    !androidx.compose.ui.test.hasAnyDescendant(androidx.compose.ui.test.hasClickAction()), useUnmergedTree = true)
            action.assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
            action.performTouchInput { down(Offset(1f, center.y)) }
            ShadowLooper.idleMainLooper(160, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeByFrame()
            val pixels = action.fetchSemanticsNode().boundsInRoot.width.toInt()
            compose.runOnIdle {
                val pressed = ripples(requireNotNull(rootView)).filter { android.R.attr.state_pressed in it.state }
                assertEquals("The actual toolbar control owns one pressed platform ripple", 1, pressed.size)
                val drawable = pressed.single()
                assertEquals(pixels, drawable.bounds.width())
                assertEquals(pixels, drawable.bounds.height())
                val cornerDistance = kotlin.math.sqrt(2f) * pixels / 2f
                assertTrue("Automatic radius must reach the UI-clipped canvas corners, not a fixed20dp circle",
                    drawable.radius >= cornerDistance - .5f)
            }
            action.performTouchInput { up() }
            for (edge in 1..3) action.performTouchInput {
                click(when (edge) {
                    1 -> Offset(width - 1f, center.y)
                    2 -> Offset(center.x, 1f)
                    else -> Offset(center.x, height - 1f)
                })
            }
            compose.runOnIdle { assertEquals(4, clicks[index]); assertEquals(0, parentClicks) }
            action.fetchSemanticsNode().boundsInRoot
        }
        geometry.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
    }

    @Test fun `native ripple receives the complete reserved canvas with a real held pointer`() {
        verifyNativeRipple(compareRaster = false)
    }

    @Test fun `native ripple paints padding whenever the independent Material baseline can render`() {
        verifyNativeRipple(compareRaster = true)
    }

    private fun verifyNativeRipple(compareRaster: Boolean) {
        compose.mainClock.autoAdvance = false
        var rootView: View? = null
        val baselineSource = MutableInteractionSource()
        compose.setContent {
            val view = LocalView.current
            SideEffect { rootView = view.rootView }
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalGlassPreviewSettings provides GlassSettings(enabled = false),
                LocalRippleConfiguration provides RippleConfiguration(color = Color.White)) {
                GlassRippleProvider(GlassSettings(enabled = false)) {
                    Row {
                        Box(Modifier.size(80.dp).background(Color.Black).testTag("host"), contentAlignment = Alignment.Center) {
                            IconButton({}, R.drawable.play_skip_back, Color.Blue,
                                Modifier.testTag("control").padding(8.dp).size(24.dp),
                                indication = ripple(bounded = false, color = Color.White))
                        }
                        Box(Modifier.size(80.dp).background(Color.Black).testTag("baseline.host"), contentAlignment = Alignment.Center) {
                            // Independent bare Material control: no project canvas helper or indication replacement.
                            androidx.compose.material3.IconButton(onClick = {}, interactionSource = baselineSource,
                                modifier = Modifier.size(40.dp)) {
                                Image(painterResource(R.drawable.play_skip_back), null,
                                    colorFilter = ColorFilter.tint(Color.Blue), modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val host = compose.onNodeWithTag("host")
        val baseline = compose.onNodeWithTag("baseline.host")
        val action = compose.onNodeWithTag("control")
        val before = host.captureToImage().toPixelMap()
        val baselineBefore = baseline.captureToImage().toPixelMap()
        val baselinePress = PressInteraction.Press(Offset(20f, 20f))
        compose.runOnIdle { assertTrue(baselineSource.tryEmit(baselinePress)) }
        action.performTouchInput { down(Offset(2f, center.y)) }
        // Android ripple uses RippleDrawable / AnimationUtils, not Compose's animation clock.
        repeat(60) {
            ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeByFrame()
        }
        fun ripples(view: View): List<RippleDrawable> =
            listOfNotNull(view.background as? RippleDrawable) +
                (if (view is ViewGroup) (0 until view.childCount).flatMap { ripples(view.getChildAt(it)) } else emptyList())
        val platformRipples = requireNotNull(rootView).let(::ripples)
        val expectedPixels = action.fetchSemanticsNode().boundsInRoot.width.toInt()
        val pressed = platformRipples.filter { android.R.attr.state_pressed in it.state }
        assertEquals("Both independent Material baseline and real pointer-driven project action must be pressed", 2, pressed.size)
        pressed.forEach {
            assertEquals("Actual native ripple width must cover the full reserved canvas", expectedPixels, it.bounds.width())
            assertEquals("Actual native ripple height must cover the full reserved canvas", expectedPixels, it.bounds.height())
        }
        val held = host.captureToImage().toPixelMap()
        val baselineHeld = baseline.captureToImage().toPixelMap()
        action.performTouchInput { up() }
        compose.runOnIdle { assertTrue(baselineSource.tryEmit(PressInteraction.Release(baselinePress))) }
        if (!compareRaster) return
        val changed = (0 until held.height).sumOf { y -> (0 until held.width).count { x -> held[x,y] != before[x,y] } }
        val baselineChanged = (0 until baselineHeld.height).sumOf { y -> (0 until baselineHeld.width).count { x -> baselineHeld[x,y] != baselineBefore[x,y] } }
        if (baselineChanged == 0) {
            assertEquals("A missing platform ripple raster must affect both baseline and project control", 0, changed)
            assumeTrue("Robolectric native graphics did not rasterize either pressed Android RippleDrawable; " +
                "both real drawables have full40px bounds but baselineChanged=0 and actionChanged=0. " +
                "Geometry and pointer coverage are verified separately; device raster coverage remains required.", false)
        }
        val hostBounds = host.fetchSemanticsNode().boundsInRoot
        val bounds = action.fetchSemanticsNode().boundsInRoot
        val sampleX = (bounds.left - hostBounds.left + 3f).toInt()
        val sampleY = (bounds.center.y - hostBounds.top).toInt()
        assertTrue("Reserved padding must visibly respond beyond the 24dp glyph: baselineChanged=" +
            "$baselineChanged, actionChanged=$changed", held[sampleX, sampleY] != before[sampleX, sampleY])
        assertEquals("No ripple outside the original reserved action seat", before[1, before.height / 2], held[1, held.height / 2])
    }

    @Test fun `song download keeps its exact twenty dp glyph raster after the canvas correction`() {
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalGlassPreviewSettings provides GlassSettings(enabled = false)) {
                Row {
                    Box(Modifier.size(40.dp).background(Color.Black).testTag("before"), contentAlignment = Alignment.Center) {
                        // The original SongItem reserves20dp for this glyph; its legacy clip stays unchanged.
                        Image(painterResource(R.drawable.download), null, colorFilter = ColorFilter.tint(Color.Blue),
                            modifier = Modifier.size(20.dp).clip(uiRoundnessShape()))
                    }
                    Box(Modifier.size(40.dp).background(Color.Black).testTag("after"), contentAlignment = Alignment.Center) {
                        IconButton({}, R.drawable.download, Color.Blue, Modifier.size(20.dp))
                    }
                }
            }
        }
        val before = compose.onNodeWithTag("before").captureToImage().toPixelMap()
        val after = compose.onNodeWithTag("after").captureToImage().toPixelMap()
        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
        for (y in 0 until before.height) for (x in 0 until before.width) {
            assertEquals("Download glyph must retain each original raster pixel at $x,$y", before[x,y], after[x,y])
        }
    }

}
