package app.n_zik.android.components.player

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.showRemainingSongTimeKey
import app.kreate.android.themed.rimusic.screen.player.timeline.DurationIndicator
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.glassFilmKey
import app.n_zik.android.components.glass.glassRimKey
import app.n_zik.android.components.glass.glassTintStrengthKey
import app.n_zik.android.components.glass.glassBlurKey
import app.n_zik.android.components.glass.glassDepthKey
import app.n_zik.android.components.glass.glassAmountKey
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.listentogether.listenTogetherGuestLock
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerTimeGlassTest {
    @get:Rule val compose = createComposeRule()
    private val preferences get() = ApplicationProvider.getApplicationContext<Application>().preferences

    @Before fun setup() {
        preferences.edit().clear().putBoolean(showRemainingSongTimeKey, false).commit()
        listenTogetherGuestLock.value = false
    }

    @Test fun `both actual tiny seek buttons preserve native canvases and pull pane with glyph`() {
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        every { binder.player } returns player
        every { player.duration } returns 100_000L
        // Isolate the sampled red pane from tint, film and edge optics for centroid measurement.
        val store = GlassStore(ApplicationProvider.getApplicationContext<Application>())
        store.reset()
        for (key in listOf(glassFilmKey, glassRimKey, glassTintStrengthKey,
            glassBlurKey, glassDepthKey, glassAmountKey)) store.setFloat(key, 0f)
        val palette = DefaultDarkColorPalette.copy(accent = Color.Cyan, red = Color.Cyan)
        preferences.edit().putBoolean(glassEnabledKey, false).commit()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppearanceProvider(palette) {
                val scene = rememberCanvasBackdrop { drawRect(Color.Red) }
                CompositionLocalProvider(LocalGlassContainerBackdrop provides scene) {
                    Box(Modifier.size(320.dp, 80.dp).testTag("tinySeekScene"), contentAlignment = Alignment.Center) {
                        Box(Modifier.requiredWidth(280.dp)) {
                            DurationIndicator(binder, null, 20_000L, 100_000L,
                                onSeekIssued = {}, seekBasePosition = { 20_000L })
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(64)
        val native = listOf("Rewind", "Forward").map {
            compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot
        }
        assertTrue("The two fixed seek canvases cannot overlap", native[0].right <= native[1].left)
        compose.runOnIdle { preferences.edit().putBoolean(glassEnabledKey, true).commit() }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onAllNodes(hasTestTag("player.skipTime.material")).assertCountEquals(2)
        val root = compose.onNodeWithTag("tinySeekScene")
        fun xCenter(pixels: androidx.compose.ui.graphics.PixelMap, region: Rect, pane: Boolean): Float {
            val points = buildList {
                for (y in region.top.toInt().coerceAtLeast(0) until region.bottom.toInt().coerceAtMost(pixels.height))
                    for (x in region.left.toInt().coerceAtLeast(0) until region.right.toInt().coerceAtMost(pixels.width)) {
                        val color = pixels[x, y]
                        if (if (pane) color.red > .5f && color.green < .4f && color.blue < .4f
                            else color.red < .4f && color.green > .6f && color.blue > .6f) add(x)
                    }
            }
            assertTrue("Actual tiny seek ${if (pane) "pane" else "glyph"} must render", points.isNotEmpty())
            return points.average().toFloat()
        }
        for ((index, name) in listOf("Rewind", "Forward").withIndex()) {
            val action = compose.onNodeWithContentDescription(name)
            assertEquals("Glass keeps the original $name canvas", native[index], action.fetchSemanticsNode().boundsInRoot)
            val region = native[index].translate(-root.fetchSemanticsNode().boundsInRoot.topLeft).inflate(10f)
            val before = root.captureToImage().toPixelMap()
            action.performTouchInput { down(center); moveTo(center + Offset(40f, 0f), 200) }
            shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(400)
            val held = root.captureToImage().toPixelMap()
            assertTrue("$name pane follows the held finger", xCenter(held, region, true) > xCenter(before, region, true) + .5f)
            assertTrue("$name glyph follows its pane", xCenter(held, region, false) > xCenter(before, region, false) + .5f)
            action.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1800)
        }
    }

    @Test fun `actual duration row keeps label and icon positions with single native seek owner`() {
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        every { binder.player } returns player
        every { player.duration } returns 100_000L
        var issued = 0L
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.requiredWidth(300.dp).testTag("metadata.host")) {
                    DurationIndicator(binder, null, 20_000L, 100_000L,
                        onSeekIssued = { issued = it }, seekBasePosition = { 20_000L })
                }
            }
        }
        compose.runOnIdle { preferences.edit().putBoolean(glassEnabledKey, false).commit() }
        val before = compose.onAllNodesWithText("0:20", useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
        val icon = compose.onNodeWithTag("player.skipTime.glyph.Forward", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val nativeTarget = compose.onNodeWithContentDescription("Forward").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { preferences.edit().putBoolean(glassEnabledKey, true).commit() }
        assertEquals(before, compose.onAllNodesWithText("0:20", useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot)
        val glassIcon = compose.onNodeWithTag("player.skipTime.glyph.Forward", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(icon.center, glassIcon.center)
        assertEquals(with(compose.density) { 14.dp.toPx() }, glassIcon.width, 1f)
        assertEquals(with(compose.density) { 14.dp.toPx() }, glassIcon.height, 1f)
        val glassTarget = compose.onNodeWithContentDescription("Forward").fetchSemanticsNode().boundsInRoot
        assertEquals(icon.center, glassTarget.center)
        assertEquals(nativeTarget, glassTarget)
        assertEquals(icon, glassTarget)
        compose.onAllNodes(hasTestTag("player.skipTime.material")).assertCountEquals(2)
        val host = compose.onNodeWithTag("metadata.host").fetchSemanticsNode().boundsInRoot
        compose.onAllNodes(hasTestTag("player.skipTime.material")).fetchSemanticsNodes().forEach { node ->
            val surface = node.boundsInRoot
            assertTrue(surface.left >= host.left && surface.right <= host.right)
            assertTrue(surface.top >= host.top && surface.bottom <= host.bottom)
            assertEquals(with(compose.density) { 20.dp.toPx() }, surface.width, 1f)
            assertEquals(with(compose.density) { 20.dp.toPx() }, surface.height, 1f)
        }
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        // Feedback and hit bounds cover the entire compact, reserved slot.
        compose.onNodeWithContentDescription("Forward").performTouchInput { click(Offset(1f, height / 2f)) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(25_000L, issued); verify(exactly = 1) { player.seekTo(25_000L) } }
    }

    @Test fun `skip bubble has one owner for double tap and hold across glass toggle`() {
        val settings = mutableStateOf(GlassSettings())
        var taps = 0
        var doubles = 0
        var holds = 0
        compose.setContent {
            AppearanceProvider {
                PlayerSkipTimeGlass(settings.value, onClick = { taps++ },
                    onDoubleClick = { doubles++ }, onLongClick = { holds++ }) {
                    Box(Modifier.size(20.dp).testTag("skip.glyph"))
                }
            }
        }
        fun owner() = compose.onNode(hasClickAction())
        val glyph = compose.onNodeWithTag("skip.glyph", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val slot = owner().fetchSemanticsNode().boundsInRoot
        assertEquals(glyph.center, slot.center)
        assertEquals(with(compose.density) { 14.dp.toPx() }, glyph.width, 1f)
        assertEquals(with(compose.density) { 20.dp.toPx() }, slot.width, 1f)
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        owner().performTouchInput { doubleClick(Offset(1f, height / 2f)) }
        compose.runOnIdle { assertEquals(1, doubles); assertEquals(0, taps) }
        owner().performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, holds); assertEquals(0, taps) }
        compose.runOnIdle { settings.value = GlassSettings(enabled = false) }
        assertEquals(slot, compose.onNodeWithTag("skip.glyph", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
        assertEquals(slot, owner().fetchSemanticsNode().boundsInRoot)
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        owner().performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(1, taps); assertEquals(1, doubles); assertEquals(1, holds) }
    }

    @Test fun `glass off skip button keeps the surrounding native indication`() {
        var indications = 0
        val native = object : IndicationNodeFactory {
            override fun equals(other: Any?) = other === this
            override fun hashCode() = System.identityHashCode(this)
            override fun create(interactionSource: InteractionSource): DelegatableNode {
                indications++
                return object : Modifier.Node(), DrawModifierNode {
                    override fun ContentDrawScope.draw() { drawContent() }
                }
            }
        }
        compose.setContent {
            AppearanceProvider {
                CompositionLocalProvider(LocalIndication provides native) {
                    PlayerSkipTimeGlass(GlassSettings(enabled = false)) {
                        Box(Modifier.size(20.dp).testTag("native.skip.glyph"))
                    }
                }
            }
        }
        val owner = compose.onNode(hasClickAction())
        owner.performTouchInput { down(center) }
        compose.runOnIdle { assertTrue("Glass OFF must retain native ripple", indications > 0) }
        assertEquals(compose.onNodeWithTag("native.skip.glyph", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot, owner.fetchSemanticsNode().boundsInRoot)
        owner.performTouchInput { up() }
    }

    @Test fun `capsule follows text width while preserving native geometry and area opt out`() {
        val settings = mutableStateOf(GlassSettings(enabled = false))
        val text = mutableStateOf("3h26m7s")
        compose.setContent {
            AppearanceProvider {
                PlayerQueueDurationGlass(settings.value) {
                    Row(Modifier.testTag("duration")) { BasicText(text.value) }
                }
            }
        }
        val before = compose.onNodeWithTag("duration").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { settings.value = GlassSettings() }
        assertEquals(before, compose.onNodeWithTag("duration").fetchSemanticsNode().boundsInRoot)
        val surface = compose.onNodeWithTag("player.queueDuration.material").fetchSemanticsNode().boundsInRoot
        assertEquals(before.center, surface.center)
        assertEquals(with(compose.density) { 16.dp.toPx() }, surface.width - before.width, 1f)
        compose.runOnIdle { text.value = "123h26m7s" }
        assertTrue(compose.onNodeWithTag("player.queueDuration.material").fetchSemanticsNode().boundsInRoot.width > surface.width)
        compose.runOnIdle { settings.value = GlassSettings(areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.ContourOnly)) }
        compose.onNodeWithTag("player.queueDuration.material").assertExists()
        val contourBounds = compose.onNodeWithTag("duration").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { settings.value = GlassSettings(areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.GlassOnly)) }
        assertEquals(contourBounds, compose.onNodeWithTag("duration").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { settings.value = GlassSettings(areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled)) }
        compose.onNodeWithTag("player.queueDuration.material").assertDoesNotExist()
    }

    @Test fun `bubbles sample live player scene instead of unrelated global scene`() {
        val scene = mutableStateOf(Color.Red)
        compose.setContent {
            AppearanceProvider {
                val player = rememberCanvasBackdrop { drawRect(scene.value) }
                val global = rememberCanvasBackdrop { drawRect(Color.Green) }
                CompositionLocalProvider(LocalGlassContainerBackdrop provides player, LocalGlassBackdrop provides global) {
                    PlayerSkipTimeGlass(GlassSettings(tintStrength = 0f)) { Box(Modifier.size(20.dp)) }
                }
            }
        }
        fun center(): Color {
            val image = compose.onNodeWithTag("player.skipTime.material").captureToImage().toPixelMap()
            return image[image.width / 2, image.height / 2]
        }
        assertTrue(center().red > center().green)
        compose.runOnIdle { scene.value = Color.Blue }
        assertTrue(center().blue > center().green)
    }

    @Composable private fun AppearanceProvider(palette: ColorPalette = DefaultDarkColorPalette,
        content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(16.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(
            colorPalette = palette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        ), content = content)
    }
}
