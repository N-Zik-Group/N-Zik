package app.n_zik.android.components.glass

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
// API 32 exercises native geometry without Robolectric's unsupported API 33 AGSL color layout.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassActionGroupTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `dropdown scene sample removes profile pixels when the measured menu takes ownership`() {
        val host = GlassMenuHostState()
        val trigger = GlassMenuTriggerState()
        val id = Any()
        compose.setContent {
            AppearanceProvider {
                CompositionLocalProvider(LocalGlassMenuHost provides host) {
                    Box(Modifier.size(88.dp, 40.dp)) {
                        GlassSceneContent(host, enabled = true) {
                            Box(Modifier.fillMaxSize().background(Color.Red))
                            GlassActionGroup(GlassSettings(enabled = true, quality = GlassQuality.Performance, film = 1f),
                                GlassArea.Header, trailingTrigger = trigger) {
                                GlassActionGroupItem({}) { }
                                GlassActionGroupItem({}, menuTrigger = trigger) {
                                    Box(Modifier.size(20.dp).background(Color.Blue))
                                }
                            }
                        }
                        host.sceneBackdrop?.let { source ->
                            Box(Modifier.fillMaxSize().testTag("sampledScene").drawPlainBackdrop(
                                backdrop = source, shape = { RectangleShape }, effects = {},
                            ))
                        }
                    }
                }
            }
        }
        compose.runOnIdle {
            host.show(GlassMenuEntry(id, trigger.bounds, GlassSettings(), {}, {},
                trigger = trigger, shape = RectangleShape))
        }
        compose.waitForIdle()
        val claimed = compose.onNodeWithTag("sampledScene").captureToImage().toPixelMap()
        assertEquals("unmeasured source remains visible", Color.Blue, claimed[claimed.width * 3 / 4, claimed.height / 2])
        compose.runOnIdle { trigger.updateMenuBounds(id, trigger.bounds) }
        compose.waitForIdle()
        val transferred = compose.onNodeWithTag("sampledScene").captureToImage().toPixelMap()
        assertEquals("sampled scene follows source removal without preserving a ghost avatar", Color.Red,
            transferred[transferred.width * 3 / 4, transferred.height / 2])
    }

    @Test fun `shared capsule keeps three distinct click targets`() {
        val clicks = IntArray(3)
        compose.setContent {
            AppearanceProvider {
                GlassActionGroup(GlassSettings(enabled = true, quality = GlassQuality.Performance), GlassArea.ActionButtons,
                    modifier = Modifier.testTag("group")) {
                    repeat(3) { index ->
                        GlassActionGroupItem({ clicks[index]++ }, Modifier.testTag("action.$index")) { }
                    }
                }
            }
        }
        compose.onNodeWithTag("group").assertWidthIsEqualTo(132.dp).assertHeightIsEqualTo(40.dp)
        repeat(3) { index ->
            compose.onNodeWithTag("action.$index").assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(40.dp)
                .performTouchInput { click() }
        }
        compose.runOnIdle { assertEquals(listOf(1, 1, 1), clicks.toList()) }
    }

    @Test fun `group press follows Square capsule dip while material hit targets and profile anchor stay fixed`() {
        var clicks = 0
        val trigger = GlassMenuTriggerState()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.background(Color.Red).testTag("host")) {
                    GlassActionGroup(GlassSettings(enabled = true, quality = GlassQuality.Performance, film = 1f),
                        GlassArea.Header, trailingTrigger = trigger) {
                        GlassActionGroupItem({ clicks++ }, Modifier.testTag("action"), menuTrigger = trigger) {
                            Box(Modifier.size(20.dp).background(Color.Blue).testTag("glyph"))
                        }
                        GlassActionGroupItem({}) { }
                    }
                }
            }
        }
        val action = compose.onNodeWithTag("action")
        val glyph = compose.onNodeWithTag("glyph", useUnmergedTree = true)
        val bounds = action.fetchSemanticsNode().boundsInRoot
        val anchor = trigger.bounds
        val glyphBounds = glyph.fetchSemanticsNode().boundsInRoot
        val before = compose.onNodeWithTag("host").captureToImage().toPixelMap()
        action.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        assertEquals(bounds, action.fetchSemanticsNode().boundsInRoot)
        assertEquals(anchor, trigger.bounds)
        assertEquals(glyphBounds.width * .92f, glyph.fetchSemanticsNode().boundsInRoot.width, 1f)
        val held = compose.onNodeWithTag("host").captureToImage().toPixelMap()
        assertEquals(before[before.width * 3 / 4, before.height / 2], held[held.width * 3 / 4, held.height / 2])
        action.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1600)
        compose.runOnIdle { assertEquals(1, clicks) }
        assertEquals(glyphBounds, glyph.fetchSemanticsNode().boundsInRoot)

        action.performTouchInput { down(center); moveTo(center + Offset(200f, 0f)); up() }
        compose.mainClock.advanceTimeBy(1600)
        compose.runOnIdle { assertEquals(1, clicks) }
        assertEquals(glyphBounds, glyph.fetchSemanticsNode().boundsInRoot)
        assertEquals(bounds, action.fetchSemanticsNode().boundsInRoot)
        assertEquals(anchor, trigger.bounds)

        action.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(64)
        assertTrue(glyph.fetchSemanticsNode().boundsInRoot.width < glyphBounds.width)
        compose.mainClock.advanceTimeBy(1600)
        compose.runOnIdle { assertEquals(2, clicks) }
        assertEquals(glyphBounds, glyph.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun `transferred profile removes trailing material without moving its source anchor`() {
        val trigger = GlassMenuTriggerState()
        val owner = Any()
        var searchClicks = 0
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.background(Color.Red).testTag("host")) {
                    GlassActionGroup(GlassSettings(enabled = true, quality = GlassQuality.Performance, film = 1f),
                        GlassArea.Header, trailingTrigger = trigger) {
                        GlassActionGroupItem({ searchClicks++ }, Modifier.glassMenuNeighbor(trigger).testTag("search")) { }
                        GlassActionGroupItem({}, menuTrigger = trigger) { }
                    }
                }
            }
        }
        val before = compose.onNodeWithTag("host").captureToImage().toPixelMap()
        assertNotEquals(Color.Red, before[before.width * 3 / 4, before.height / 2])
        var bounds = Rect.Zero
        compose.runOnIdle {
            bounds = trigger.bounds
            assertNotNull(trigger.content)
            trigger.claim(owner)
            trigger.updateMenuBounds(owner, bounds)
        }
        val after = compose.onNodeWithTag("host").captureToImage().toPixelMap()
        assertEquals(Color.Red, after[after.width * 3 / 4, after.height / 2])
        compose.runOnIdle { assertEquals(bounds, trigger.bounds) }
        compose.onNodeWithTag("search").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, searchClicks); trigger.release(owner) }
        val restored = compose.onNodeWithTag("host").captureToImage().toPixelMap()
        assertNotEquals(Color.Red, restored[restored.width * 3 / 4, restored.height / 2])
    }

    @Test fun `single profile transfers its whole surface and returns without a phantom neighbor`() {
        val trigger = GlassMenuTriggerState()
        val owner = Any()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.background(Color.Red).testTag("single.host")) {
                    GlassActionGroup(
                        GlassSettings(enabled = true, quality = GlassQuality.Performance, film = 1f),
                        GlassArea.Header, trailingTrigger = trigger, menuActionCount = 1, menuTriggerIndex = 0,
                    ) {
                        GlassActionGroupItem({}, menuTrigger = trigger) {
                            Box(Modifier.size(20.dp).background(Color.Blue))
                        }
                    }
                }
            }
        }
        val host = compose.onNodeWithTag("single.host")
        host.assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(40.dp)
        val anchor = trigger.bounds
        val resting = host.captureToImage().toPixelMap()
        assertNotEquals(Color.Red, resting[resting.width / 8, resting.height / 2])
        compose.runOnIdle { trigger.claim(owner) }
        compose.mainClock.advanceTimeByFrame()
        val unmeasured = host.captureToImage().toPixelMap()
        assertEquals(Color.Blue, unmeasured[unmeasured.width / 2, unmeasured.height / 2])

        repeat(2) {
            compose.runOnIdle {
                trigger.claim(owner)
                trigger.updateMenuBounds(owner, anchor)
                Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeByFrame()
            val transferred = host.captureToImage().toPixelMap()
            assertTrue("no material or avatar remains behind the single-source menu",
                (0 until transferred.height).all { y ->
                    (0 until transferred.width).all { x -> transferred[x, y] == Color.Red }
                })
            compose.runOnIdle {
                assertEquals(anchor, trigger.bounds)
                trigger.release(owner)
                Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeByFrame()
            val restored = host.captureToImage().toPixelMap()
            assertEquals(resting[resting.width / 8, resting.height / 2],
                restored[restored.width / 8, restored.height / 2])
            assertEquals(Color.Blue, restored[restored.width / 2, restored.height / 2])
            assertEquals(anchor, trigger.bounds)
            host.assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(40.dp)
        }
    }

    @Test fun `action area modes update material while preserving cell bounds`() {
        val config = mutableStateOf(GlassSettings(enabled = true, quality = GlassQuality.Performance, film = 1f))
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.background(Color.Red).testTag("host")) {
                    GlassActionGroup(config.value, GlassArea.ActionButtons) {
                        GlassActionGroupItem({}, Modifier.testTag("action")) { Box(Modifier.size(8.dp).background(Color.Blue)) }
                    }
                }
            }
        }
        val bounds = compose.onNodeWithTag("action").fetchSemanticsNode().boundsInRoot
        val rendered = mutableMapOf<GlassAreaMode, List<Color>>()
        GlassAreaMode.entries.forEach { mode ->
            compose.runOnIdle { config.value = config.value.copy(areaModes = mapOf(GlassArea.ActionButtons to mode)) }
            assertEquals(bounds, compose.onNodeWithTag("action").fetchSemanticsNode().boundsInRoot)
            val pixels = compose.onNodeWithTag("host").captureToImage().toPixelMap()
            rendered[mode] = (0 until pixels.height).flatMap { y -> (0 until pixels.width).map { x -> pixels[x, y] } }
            val fill = pixels[pixels.width / 4, pixels.height / 2]
            if (mode.hasMaterial) assertNotEquals(Color.Red, fill) else assertEquals(Color.Red, fill)
            assertEquals(Color.Blue, pixels[pixels.width / 2, pixels.height / 2])
        }
        assertNotEquals(rendered[GlassAreaMode.Disabled], rendered[GlassAreaMode.ContourOnly])
        assertNotEquals(rendered[GlassAreaMode.GlassOnly], rendered[GlassAreaMode.GlassAndContour])
    }

    @Test fun `returning profile elastically reforms the drawn pane while anchors stay fixed`() {
        val trigger = GlassMenuTriggerState()
        val owner = Any()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.background(Color.Red).testTag("host")) {
                    GlassActionGroup(GlassSettings(enabled = true, quality = GlassQuality.Performance, film = 1f),
                        GlassArea.Header, trailingTrigger = trigger) {
                        GlassActionGroupItem({}, Modifier.glassMenuNeighbor(trigger).testTag("search")) { }
                        GlassActionGroupItem({}, menuTrigger = trigger) { }
                    }
                }
            }
        }
        val anchor = trigger.bounds
        val groupBounds = compose.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            trigger.claim(owner); trigger.updateMenuBounds(owner, anchor); Snapshot.sendApplyNotifications()
        }
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.runOnIdle { trigger.release(owner); Snapshot.sendApplyNotifications() }
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        val prefix = compose.onNodeWithTag("host").captureToImage().toPixelMap()
        assertNotEquals("returning profile already rests on its own surface", Color.Red, prefix[prefix.width * 3 / 4, prefix.height / 2])
        repeat(2) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        val neck = compose.onNodeWithTag("host").captureToImage().toPixelMap()
        assertEquals(Color.Red, neck[neck.width / 2, neck.height / 6])
        assertNotEquals(Color.Red, neck[neck.width / 2, neck.height / 2])
        compose.mainClock.advanceTimeBy(160)
        val expanding = compose.onNodeWithTag("host").captureToImage().toPixelMap()
        assertNotEquals(Color.Red, expanding[expanding.width * 3 / 4, expanding.height / 2])
        compose.runOnIdle { assertEquals(anchor, trigger.bounds) }
        assertEquals(groupBounds, compose.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(1600)
        assertEquals(anchor, trigger.bounds)
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            assertEquals(92, glassActionGroupSurfaceWidth(92, 46, it, false))
        }
        assertEquals(46, glassActionGroupSurfaceWidth(92, 46, 1.2f, true))
    }

    @Test fun `reopening cancels the neck and reduced motion returns directly to the full capsule`() {
        val trigger = GlassMenuTriggerState()
        val owner = Any()
        var renderedTransfer = false
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        val oldScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        compose.mainClock.autoAdvance = false
        try {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            compose.setContent {
                AppearanceProvider {
                    val transfer = trigger.isTransferred
                    SideEffect { renderedTransfer = transfer }
                    Box(Modifier.background(Color.Red).testTag("host")) {
                        GlassActionGroup(GlassSettings(enabled = true, quality = GlassQuality.Performance, film = 1f),
                            GlassArea.Header, trailingTrigger = trigger) {
                            GlassActionGroupItem({}, Modifier.testTag("search")) {
                                Box(Modifier.size(20.dp).testTag("reduced.glyph"))
                            }
                            GlassActionGroupItem({}, menuTrigger = trigger) { }
                        }
                    }
                }
            }
            val anchor = trigger.bounds
            val hitbox = compose.onNodeWithTag("search").fetchSemanticsNode().boundsInRoot
            compose.runOnIdle { trigger.claim(owner); trigger.updateMenuBounds(owner, anchor) }
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertTrue("first transfer must be composed before its release", renderedTransfer)
            compose.runOnIdle { trigger.release(owner); Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeBy(160)
            compose.waitForIdle()
            assertTrue("return must be composed before reopening", !renderedTransfer)
            compose.runOnIdle {
                trigger.claim(owner)
                trigger.updateMenuBounds(owner, anchor)
                Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertTrue("reopening must transfer the source before measuring its pane", renderedTransfer)
            assertEquals(anchor, trigger.bounds)
            assertEquals(anchor, trigger.menuBounds)
            val transferred = compose.onNodeWithTag("host").captureToImage().toPixelMap()
            val centerLine = (0 until transferred.width).map { x ->
                if (transferred[x, transferred.height / 2] == Color.Red) '.' else '#'
            }.joinToString("")
            assertEquals("reopened pane: transferred=$renderedTransfer source=${trigger.bounds} menu=${trigger.menuBounds} center=$centerLine",
                Color.Red, transferred[transferred.width * 3 / 4, transferred.height / 2])
            compose.runOnIdle {
                Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
                trigger.release(owner)
            }
            compose.mainClock.advanceTimeByFrame()
            val settled = compose.onNodeWithTag("host").captureToImage().toPixelMap()
            assertNotEquals(Color.Red, settled[settled.width / 2, settled.height / 6])
            assertNotEquals(Color.Red, settled[settled.width * 3 / 4, settled.height / 2])
            assertEquals(anchor, trigger.bounds)
            assertEquals(hitbox, compose.onNodeWithTag("search").fetchSemanticsNode().boundsInRoot)
            val glyph = compose.onNodeWithTag("reduced.glyph", useUnmergedTree = true)
            val restingGlyph = glyph.fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("search").performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(160)
            assertEquals(restingGlyph, glyph.fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithTag("search").performTouchInput { up() }
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, oldScale)
        }
    }

    @Test fun `header neighbor follows continuous positions on release and interrupted rejoin`() {
        assertHeaderNeighborMotion(native = false)
    }

    @Test fun `native header neighbor shares continuous motion without forcing cell size`() {
        assertHeaderNeighborMotion(native = true)
    }

    private fun assertHeaderNeighborMotion(native: Boolean) {
        val trigger = GlassMenuTriggerState()
        val owner = Any()
        var searchX = 0f
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.size(300.dp, 80.dp), contentAlignment = Alignment.TopEnd) {
                    GlassActionGroup(GlassSettings(enabled = false), GlassArea.Header,
                        trailingTrigger = trigger, menuActionCount = 2, menuTriggerIndex = 1,
                        drawSurface = !native) {
                        if (native) {
                            GlassActionGroupNativeItem(0) {
                                Box(Modifier.size(48.dp).onGloballyPositioned { searchX = it.positionInRoot().x })
                            }
                            GlassActionGroupNativeItem(1) {
                                GlassMenuTrigger(trigger, RectangleShape) { Box(Modifier.size(48.dp)) }
                            }
                        } else {
                            GlassActionGroupItem({}, groupIndex = 0) {
                                Box(Modifier.size(20.dp).onGloballyPositioned { searchX = it.positionInRoot().x })
                            }
                            GlassActionGroupItem({}, menuTrigger = trigger, groupIndex = 1) { }
                        }
                    }
                }
            }
        }
        val source = trigger.bounds
        val rest = searchX
        compose.runOnIdle {
            trigger.claim(owner)
            trigger.updateMenuBounds(owner, Rect(source.left - source.width * 2f, source.top, source.right, source.bottom))
        }
        val samples = mutableListOf(rest)
        repeat(40) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            samples += searchX
            assertEquals(source, trigger.bounds)
        }
        assertTrue("glyph must nudge with the neighboring hit target: rest=$rest now=$searchX source=$source", searchX < rest - 1f)
        val displaced = searchX
        compose.runOnIdle { trigger.release(owner) }
        compose.waitForIdle()
        assertEquals("ownership release itself cannot relocate the search", displaced, searchX, 1f)
        repeat(8) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            samples += searchX
            assertEquals(source, trigger.bounds)
        }
        assertTrue(searchX > displaced && searchX < rest)
        assertTrue("header neighbor stays within a quarter cell of its resting position",
            rest - displaced <= source.width / 4f + 1f)
        compose.runOnIdle {
            trigger.claim(owner)
            trigger.updateMenuBounds(owner, Rect(source.left - source.width * 2f, source.top, source.right, source.bottom))
        }
        repeat(16) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            samples += searchX
            assertEquals(source, trigger.bounds)
        }
        samples.zipWithNext().forEach { (before, after) ->
            assertTrue("frame displacement must stay below half a cell: $before -> $after",
                kotlin.math.abs(after - before) < source.width / 2f)
        }
        compose.runOnIdle { trigger.release(owner) }
        compose.mainClock.advanceTimeBy(1600)
        compose.waitForIdle()
        assertEquals(rest, searchX, 1f)
    }

    @Test fun `middle globe splits glass neighbors on their own side then returns before fusion`() {
        assertMiddleNeighbors(native = false)
    }

    @Test fun `middle globe splits native neighbors without changing native controls`() {
        assertMiddleNeighbors(native = true)
    }

    @Test fun `middle globe keeps glass neighbors visible at the right screen edge`() {
        assertMiddleNeighbors(native = false, atRightEdge = true)
    }

    @Test fun `middle globe keeps native neighbors visible at the right screen edge`() {
        assertMiddleNeighbors(native = true, atRightEdge = true)
    }

    private fun assertMiddleNeighbors(native: Boolean, atRightEdge: Boolean = false) {
        val trigger = GlassMenuTriggerState()
        val owner = Any()
        val positions = FloatArray(3)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.fillMaxSize(), contentAlignment = if (atRightEdge) Alignment.TopEnd else Alignment.TopCenter) {
                    GlassActionGroup(GlassSettings(enabled = !native, quality = GlassQuality.Performance),
                        GlassArea.ActionButtons, trailingTrigger = trigger, menuActionCount = 3,
                        menuTriggerIndex = 1, drawSurface = !native) {
                        repeat(3) { index ->
                            val ink = Modifier.size(20.dp).onGloballyPositioned { positions[index] = it.positionInRoot().x }
                            if (native) GlassActionGroupNativeItem(index) {
                                if (index == 1) GlassMenuTrigger(trigger, RectangleShape) {
                                    androidx.compose.material3.IconButton(onClick = {}) { Box(ink) }
                                } else androidx.compose.material3.IconButton(onClick = {}) { Box(ink) }
                            } else GlassActionGroupItem({}, menuTrigger = trigger.takeIf { index == 1 }, groupIndex = index) {
                                Box(ink)
                            }
                        }
                    }
                }
            }
        }
        val rest = positions.toList()
        assertEquals("middle globe anchors the menu around its own center", .5f, trigger.menuAnchorFraction, 0f)
        val source = trigger.bounds
        compose.runOnIdle {
            trigger.claim(owner)
            trigger.updateMenuBounds(owner, Rect(source.left - source.width, source.top,
                source.right + source.width, source.bottom))
        }
        repeat(60) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertTrue("left neighbor only nudges, regardless of menu width",
                rest[0] - positions[0] in -1f..(source.width / 4f + 1f))
            assertTrue("right neighbor only nudges, regardless of menu width",
                positions[2] - rest[2] in -1f..(source.width / 4f + 1f))
        }
        assertTrue("left neighbor still moves slightly", positions[0] < rest[0] - 1f)
        if (atRightEdge) assertEquals("right edge must not push the final action offscreen", rest[2], positions[2], 1f)
        else assertTrue("right neighbor still moves slightly", positions[2] > rest[2] + 1f)
        assertEquals(source, trigger.bounds)
        val displaced = positions.toList()
        compose.runOnIdle { trigger.release(owner) }
        compose.waitForIdle()
        assertEquals(displaced, positions.toList())
        // The neighbors must finish returning promptly, rather than wait on an invisible spring tail.
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        rest.forEachIndexed { index, x -> assertEquals(x, positions[index], 1f) }
        assertEquals(source, trigger.bounds)
    }

    @Test fun `native group cells inherit indication and retain glyph size during press`() {
        var indications = 0
        val indication = object : IndicationNodeFactory {
            override fun equals(other: Any?) = other === this
            override fun hashCode() = System.identityHashCode(this)
            override fun create(interactionSource: InteractionSource): DelegatableNode {
                indications++
                return object : Modifier.Node(), DrawModifierNode {
                    override fun ContentDrawScope.draw() { drawContent() }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppearanceProvider {
                CompositionLocalProvider(LocalIndication provides indication) {
                    GlassActionGroupItem({}, Modifier.testTag("native.ripple"), nativeIndication = true) {
                        Box(Modifier.size(20.dp).testTag("native.ink"))
                    }
                }
            }
        }
        val glyph = compose.onNodeWithTag("native.ink", useUnmergedTree = true)
        val bounds = glyph.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("native.ripple").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        assertTrue("native cell must use its surrounding indication", indications > 0)
        assertEquals("native press must retain classic glyph geometry", bounds, glyph.fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("native.ripple").performTouchInput { up() }
    }

    @Test fun `native vertical header retains cells without drawing a capsule fill`() {
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.background(Color.Red).testTag("native.vertical")) {
                    GlassVerticalActionGroup(GlassSettings(enabled = false), GlassArea.Header) {
                        GlassActionGroupItem({}) { }
                        GlassActionGroupItem({}) { Box(Modifier.size(12.dp).background(Color.Blue)) }
                    }
                }
            }
        }
        compose.onNodeWithTag("native.vertical").assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(80.dp)
        val pixels = compose.onNodeWithTag("native.vertical").captureToImage().toPixelMap()
        assertEquals(Color.Red, pixels[pixels.width / 2, pixels.height / 4])
        assertEquals(Color.Red, pixels[pixels.width / 4, pixels.height * 3 / 4])
        assertEquals(Color.Blue, pixels[pixels.width / 2, pixels.height * 3 / 4])
    }

    @Test fun `vertical profile rejoins promptly without moving its source`() {
        val trigger = GlassMenuTriggerState()
        val owner = Any()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.background(Color.Red).testTag("vertical.return")) {
                    GlassVerticalActionGroup(
                        GlassSettings(enabled = true, quality = GlassQuality.Performance, film = 1f),
                        GlassArea.Header, trailingTrigger = trigger,
                    ) {
                        GlassActionGroupItem({}) { }
                        GlassActionGroupItem({}, menuTrigger = trigger) { }
                    }
                }
            }
        }
        val node = compose.onNodeWithTag("vertical.return")
        val rest = node.captureToImage().toPixelMap()
        val source = trigger.bounds
        compose.runOnIdle {
            trigger.claim(owner); trigger.updateMenuBounds(owner, source); Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        val open = node.captureToImage().toPixelMap()
        assertEquals(Color.Red, open[open.width / 2, open.height * 3 / 4])
        compose.runOnIdle { trigger.release(owner); Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(240)
        compose.waitForIdle()
        val closed = node.captureToImage().toPixelMap()
        assertEquals(source, trigger.bounds)
        for (y in 1 until rest.height - 1) {
            assertEquals("vertical join must have settled within 240ms",
                rest[rest.width / 2, y], closed[closed.width / 2, y])
        }
    }

    @Composable private fun AppearanceProvider(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(24.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        ), content = content)
    }
}
