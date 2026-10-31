package app.n_zik.android.components.player

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.BoundedCornerSize
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class PlayerGlassActionsTest {
    @get:Rule val compose = createComposeRule()
    private val settings = GlassSettings(film = 0f, rim = 0f, blur = 0f,
        vibrancy = 0f, depth = 0f, amount = 0f)

    @Test fun `single lower action moves its complete material beyond the resting canvas`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppearanceProvider(RoundedCornerShape(0.dp)) {
                val scene = rememberCanvasBackdrop { drawRect(Color.Red) }
                CompositionLocalProvider(LocalGlassContainerBackdrop provides scene) {
                    Box(Modifier.size(300.dp, 120.dp).background(Color.Black).testTag("single.toolbar.root"),
                        contentAlignment = Alignment.Center) {
                        PlayerActionToolbar(listOf(PlayerActionSlot("only") {
                            PlayerActionIcon(R.drawable.play, Color.Cyan, {}, Modifier.size(24.dp))
                        }), true, settings = settings.copy(tintStrength = 0f), landscape = true)
                    }
                }
            }
        }
        val root = compose.onNodeWithTag("single.toolbar.root")
        val surface = compose.onNodeWithTag("player.actionToolbar.material", useUnmergedTree = true)
        val original = surface.fetchSemanticsNode().boundsInRoot
        val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
        val x = (original.right - origin.x + with(compose.density) { 1.dp.toPx() }).toInt()
        val y = (original.center.y - origin.y).toInt()
        assertEquals(Color.Black, root.captureToImage().toPixelMap()[x, y])
        val action = compose.onAllNodes(hasClickAction())[0]
        action.performTouchInput { down(center); moveTo(center + Offset(100f, 0f), 200) }
        shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeBy(400)
        val held = root.captureToImage().toPixelMap()[x, y]
        assertTrue("The entire red material follows the action's spring outside its original slot", held.red > .7f)
        action.performTouchInput { up() }
    }

    @Test fun `lower toolbar individual clips follow configured ordinary corners in native and Glass`() {
        val radius = mutableStateOf(0.dp)
        val glass = mutableStateOf(false)
        compose.setContent {
            AppearanceProvider(RoundedCornerShape(BoundedCornerSize(radius.value, .15f))) {
                Box(Modifier.size(300.dp, 100.dp).background(Color.Black)) {
                    PlayerActionToolbar(listOf("first", "second").map { id ->
                        PlayerActionSlot(id, nativeHeight = 48.dp) {
                            // Paint the actual action glyph canvas up to its clip, exposing its corners.
                            PlayerActionIcon(R.drawable.play, Color.White, {},
                                Modifier.size(48.dp).background(Color.White).testTag("corner.glyph.$id"))
                        }
                    }, true, settings = settings.copy(enabled = glass.value), landscape = true)
                }
            }
        }
        for (value in listOf(0.dp, 4.dp, 50.dp)) {
            compose.runOnIdle { radius.value = value; glass.value = false }
            val native = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }
            val glyphs = listOf("first", "second").map {
                compose.onNodeWithTag("corner.glyph.$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            }
            for (enabled in listOf(false, true)) {
                compose.runOnIdle { glass.value = enabled }
                assertEquals(native, compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot })
                assertEquals(glyphs, listOf("first", "second").map {
                    compose.onNodeWithTag("corner.glyph.$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                })
                val pixels = compose.onAllNodes(hasClickAction())[0].captureToImage().toPixelMap()
                val corner = with(compose.density) { 4.dp.roundToPx() }
                assertEquals("Ordinary corners preserve the glyph canvas at radius=$value glass=$enabled",
                    Color.White, pixels[corner, corner])
                assertEquals(Color.White, pixels[pixels.width / 2, pixels.height / 2])
            }
        }
    }
    @Test fun `upper bubbles keep separate forty dp targets and native twenty four dp glyph targets`() {
        val glass = mutableStateOf(true)
        var closes = 0
        var menus = 0
        var home = 0
        compose.setContent {
            AppearanceProvider {
                Row {
                    PlayerTopAction(R.drawable.chevron_down, Color.White, 180f, { closes++ },
                        settings.copy(enabled = glass.value))
                    PlayerTopAction(R.drawable.ellipsis_vertical, Color.White, 180f, { menus++ },
                        settings.copy(enabled = glass.value))
                    PlayerTopAction(R.drawable.ic_launcher_monochrome, Color.White, 0f, { home++ },
                        settings.copy(enabled = glass.value), contentDescription = "Player home")
                }
            }
        }
        for (enabled in listOf(true, false)) {
            compose.runOnIdle { glass.value = enabled }
            compose.onAllNodes(hasClickAction()).assertCountEquals(3)
            for (i in 0..2) compose.onAllNodes(hasClickAction())[i]
                .assertWidthIsEqualTo(40.dp)
                .assertHeightIsEqualTo(40.dp)
                .performTouchInput { click() }
        }
        compose.runOnIdle { assertEquals(2, closes); assertEquals(2, menus); assertEquals(2, home) }
    }

    @Test fun `upper contour mode keeps native target while material modes use bubbles`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        compose.setContent {
            AppearanceProvider {
                PlayerTopAction(R.drawable.chevron_down, Color.White, 0f, {},
                    settings.copy(areaModes = mapOf(GlassArea.ActionButtons to mode.value)))
            }
        }
        GlassAreaMode.entries.forEach { value ->
            compose.runOnIdle { mode.value = value }
            compose.onAllNodes(hasClickAction()).assertCountEquals(1)
            compose.onAllNodes(hasClickAction())[0].assertWidthIsEqualTo(40.dp)
        }
    }

    @Test fun `visible slots update order and sparse rows without empty surfaces`() {
        val order = mutableStateOf(listOf("lyrics", "download", "menu"))
        val clicked = mutableListOf<String>()
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.width(300.dp)) {
                    PlayerActionToolbar(order.value.map { id -> PlayerActionSlot(id) {
                        PlayerActionIcon(R.drawable.play, Color.White, { clicked.add(id) }, Modifier.size(24.dp))
                    } }, true, Modifier.testTag("toolbar"), settings)
                }
            }
        }
        compose.onAllNodes(hasClickAction()).assertCountEquals(3)
        compose.onAllNodes(hasClickAction())[0].assertWidthIsEqualTo(48.dp).performTouchInput { click() }
        compose.onAllNodes(hasClickAction())[2].performTouchInput { click() }
        compose.runOnIdle { order.value = listOf("menu") }
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onAllNodes(hasClickAction())[0].assertWidthIsEqualTo(56.dp).performTouchInput { click() }
        compose.runOnIdle { order.value = emptyList() }
        compose.onNodeWithTag("toolbar").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("lyrics", "menu", "menu"), clicked) }
    }

    @Test fun `group owns one click long click and disabled target per action in both modes`() {
        val glass = mutableStateOf(true)
        var clicks = 0
        var holds = 0
        var disabled = 0
        var queue = 0
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.width(280.dp).clickable { queue++ }) {
                    PlayerActionToolbar(listOf(
                        PlayerActionSlot("lyrics") { PlayerActionIcon(R.drawable.play, Color.White,
                            { clicks++ }, Modifier.size(24.dp), onLongClick = { holds++ }) },
                        PlayerActionSlot("shuffle") { PlayerActionIcon(R.drawable.shuffle, Color.Gray,
                            { disabled++ }, Modifier.size(24.dp), enabled = false) },
                    ), true, settings = settings.copy(enabled = glass.value))
                }
            }
        }
        for (enabled in listOf(true, false)) {
            compose.runOnIdle { glass.value = enabled }
            // The first click node is the queue parent. Child presses must not reach it.
            compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(3)
            compose.onAllNodes(hasClickAction(), useUnmergedTree = true)[1].performTouchInput { click() }
            compose.onAllNodes(hasClickAction(), useUnmergedTree = true)[1].performTouchInput { longClick() }
            compose.onAllNodes(hasClickAction(), useUnmergedTree = true)[2].assertIsNotEnabled()
                .performTouchInput { click() }
        }
        compose.runOnIdle { assertEquals(2, clicks); assertEquals(2, holds); assertEquals(0, disabled); assertEquals(0, queue) }
    }

    @Test fun `glass action drag stays on its button while outside gesture can open queue`() {
        var queueDrags = 0
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.size(200.dp, 100.dp).testTag("queue.drag.surface").pointerInput(Unit) {
                    detectVerticalDragGestures { change, amount ->
                        change.consume()
                        if (amount < 0f) queueDrags++
                    }
                }) {
                    PlayerActionToolbar(listOf("a", "b").map { id -> PlayerActionSlot(id) {
                        PlayerActionIcon(R.drawable.play, Color.White, {}, Modifier.size(24.dp))
                    } }, true, settings = settings)
                }
            }
        }
        compose.onAllNodes(hasClickAction())[0].performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(0, queueDrags) }
        compose.onNodeWithTag("queue.drag.surface").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(190f, 95f), androidx.compose.ui.geometry.Offset(190f, 65f), 300)
        }
        compose.runOnIdle { assertTrue(queueDrags > 0) }
    }

    @Test fun `lower action canvas includes the space beside native glyphs in both modes`() {
        val glass = mutableStateOf(false)
        val clicked = IntArray(6)
        compose.setContent { AppearanceProvider {
            Box(Modifier.size(360.dp, 82.dp), contentAlignment = Alignment.Center) {
                PlayerActionToolbar(List(6) { index -> PlayerActionSlot("action.$index") {
                    PlayerActionIcon(R.drawable.play, Color.White, { clicked[index]++ },
                        Modifier.size(24.dp).testTag("canvas.glyph.$index"))
                } }, false, settings = settings.copy(enabled = glass.value), landscape = true)
            }
        } }
        var baseline: List<androidx.compose.ui.geometry.Rect>? = null
        for (enabled in listOf(false, true)) {
            compose.runOnIdle { glass.value = enabled }
            val glyphs = (0..5).map { compose.onNodeWithTag("canvas.glyph.$it", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot }
            if (baseline == null) baseline = glyphs else assertEquals("Glyph bounds stay native", baseline, glyphs)
            compose.onAllNodes(hasClickAction()).assertCountEquals(6)
            val surface = compose.onNodeWithTag(if (enabled) "player.actionToolbar.material" else "player.actionToolbar.native",
                useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            for (index in 0..5) {
                val target = compose.onAllNodes(hasClickAction())[index]
                    .assertHeightIsEqualTo(48.dp)
                val bounds = target.fetchSemanticsNode().boundsInRoot
                assertTrue("Every full action canvas stays inside its capsule glass=$enabled index=$index surface=$surface bounds=$bounds",
                    bounds.left >= surface.left - 1f && bounds.right <= surface.right + 1f &&
                        bounds.top >= surface.top - 1f && bounds.bottom <= surface.bottom + 1f)
                val availableWidth = minOf(with(compose.density) { 48.dp.toPx() },
                    if (index > 0) glyphs[index].center.x - glyphs[index - 1].center.x else Float.POSITIVE_INFINITY,
                    if (index < glyphs.lastIndex) glyphs[index + 1].center.x - glyphs[index].center.x else Float.POSITIVE_INFINITY)
                assertEquals("Canvas fills available space without overlapping its neighbor", availableWidth, bounds.width, 1f)
                assertEquals(glyphs[index].center.x, bounds.center.x, .5f)
                assertTrue("Canvas extends beyond the original glyph", bounds.left < glyphs[index].left)
                target.performTouchInput { click(androidx.compose.ui.geometry.Offset(2f, center.y)) }
            }
        }
        compose.runOnIdle { clicked.forEach { assertEquals("Exactly one action owns each expanded canvas", 2, it) } }
    }

    @Test fun `empty toolbar space opens the queue without a parent ripple in either mode`() {
        val glass = mutableStateOf(false)
        var opens = 0
        compose.setContent { AppearanceProvider {
            Box(Modifier.size(300.dp, 100.dp).background(Color.Black)
                .playerActionQueueTap(true) { opens++ }.testTag("queue.canvas"),
                contentAlignment = Alignment.Center) {
                PlayerActionToolbar(listOf("lyrics", "menu").map { id -> PlayerActionSlot(id) {
                    PlayerActionIcon(R.drawable.play, Color.White, {}, Modifier.size(24.dp))
                } }, true, settings = settings.copy(enabled = glass.value), landscape = true)
            }
        } }
        for (enabled in listOf(false, true)) {
            compose.runOnIdle { glass.value = enabled }
            val host = compose.onNodeWithTag("queue.canvas")
            val before = host.captureToImage().toPixelMap()
            host.performTouchInput { down(androidx.compose.ui.geometry.Offset(center.x, 2f)) }
            compose.mainClock.advanceTimeBy(200)
            val pressed = host.captureToImage().toPixelMap()
            for (x in 0 until before.width) for (y in 0 until minOf(10, before.height))
                assertEquals("Empty-space press paints no full-width queue indication glass=$enabled", before[x, y], pressed[x, y])
            host.performTouchInput { up() }
        }
        compose.runOnIdle { assertEquals(2, opens) }
    }

    @Test fun `download queues and active progress cancel once while completed icon invokes download`() {
        val state = mutableStateOf(Download.STATE_QUEUED)
        val glass = mutableStateOf(true)
        var downloads = 0
        var cancellations = 0
        compose.setContent {
            AppearanceProvider {
                PlayerActionToolbar(listOf(PlayerActionSlot("download") {
                    PlayerDownloadAction({ downloads++ }, { cancellations++ }, R.drawable.download,
                        Color.White, Modifier.size(24.dp), downloadState = state.value)
                }), true, settings = settings.copy(enabled = glass.value))
            }
        }
        for (enabled in listOf(true, false)) {
            compose.runOnIdle { glass.value = enabled }
            for (value in listOf(Download.STATE_QUEUED, Download.STATE_DOWNLOADING, Download.STATE_RESTARTING,
                Download.STATE_COMPLETED)) {
                compose.runOnIdle { state.value = value }
                compose.onAllNodes(hasClickAction()).assertCountEquals(1)
                compose.onAllNodes(hasClickAction())[0].performTouchInput { click() }
            }
        }
        compose.runOnIdle { assertEquals(2, downloads); assertEquals(6, cancellations) }
    }

    @Test @Config(qualifiers = "w800dp-h1200dp")
    fun `fifty six dp glass preserves glyphs distribution safe margins and native breathing room`() {
        val glass = mutableStateOf(false)
        val even = mutableStateOf(true)
        val width = mutableStateOf(360.dp)
        val count = mutableStateOf(6)
        val nextSongs = mutableStateOf(false)
        val downloadState = mutableStateOf(Download.STATE_COMPLETED)
        val safeInsets = mutableStateOf(WindowInsets(0.dp))
        compose.setContent {
            AppearanceProvider {
                val layout = rememberPlayerActionToolbarLayout()
                val currentSettings = settings.copy(enabled = glass.value)
                Column(Modifier.requiredWidth(width.value)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Row(Modifier.padding(safeInsets.value.asPaddingValues())
                            .fillMaxWidth(PlayerControlsWidthFraction).height(40.dp).testTag("top.row")) {}
                    }
                    Column(Modifier.fillMaxWidth()
                        .height(layout.hostHeight(if (nextSongs.value) 90.dp else 50.dp))
                        .testTag("geometry.host"), verticalArrangement = Arrangement.SpaceAround) {
                        if (nextSongs.value) Box(Modifier.fillMaxWidth().height(50.dp).testTag("next.preview"))
                        PlayerActionToolbar(List(count.value) { index -> PlayerActionSlot(
                            if (index == 5) "arrow" else index.toString(), nativeHeight = if (index == 1) 20.dp else 24.dp,
                        ) {
                            if (index == 2) PlayerDownloadAction({}, {}, R.drawable.download, Color.White,
                                Modifier.size(24.dp).testTag("glyph.$index"), downloadState = downloadState.value)
                            else PlayerActionIcon(R.drawable.play, Color.White, {},
                                Modifier.size(if (index == 1) 20.dp else 24.dp).testTag("glyph.$index"))
                        } }, even.value, Modifier.testTag("geometry"), currentSettings, layout,
                            horizontalInsets = safeInsets.value, landscape = false)
                    }
                }
            }
        }
        for (viewport in listOf(360.dp, 720.dp)) for (spaceEvenly in listOf(true, false))
            for (actionCount in listOf(1, 6)) for (state in listOf(Download.STATE_COMPLETED, Download.STATE_QUEUED))
            for (showNext in listOf(false, true)) for (inset in listOf(false, true)) {
            compose.runOnIdle {
                width.value = viewport; even.value = spaceEvenly; count.value = actionCount
                downloadState.value = state; nextSongs.value = showNext; glass.value = false
                safeInsets.value = if (inset) WindowInsets(left = 20.dp, right = 30.dp) else WindowInsets(0.dp)
            }
            val native = (0 until actionCount).map { compose.onNodeWithTag("glyph.$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
            val nativeCanvas = compose.onNodeWithTag("player.actionToolbar.native", useUnmergedTree = true)
                .assertHeightIsEqualTo(56.dp).fetchSemanticsNode().boundsInRoot
            val preview = if (showNext) compose.onNodeWithTag("next.preview").fetchSemanticsNode().boundsInRoot else null
            compose.onNodeWithTag("geometry.host").assertHeightIsEqualTo(if (showNext) 122.dp else 82.dp)
            compose.runOnIdle { glass.value = true }
            val decorated = (0 until actionCount).map { compose.onNodeWithTag("glyph.$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
            val top = compose.onNodeWithTag("top.row").fetchSemanticsNode().boundsInRoot
            val host = compose.onNodeWithTag("geometry.host").fetchSemanticsNode().boundsInRoot
            val materialNode = compose.onNodeWithTag("player.actionToolbar.material", useUnmergedTree = true)
            val material = materialNode.assertHeightIsEqualTo(56.dp).fetchSemanticsNode().boundsInRoot
            assertEquals("Both presentations share the same canvas dimensions and margins", nativeCanvas, material)
            compose.onNodeWithTag("geometry.host").assertHeightIsEqualTo(if (showNext) 122.dp else 82.dp)
            if (actionCount > 1) {
                assertEquals(top.left, material.left, .5f)
                assertEquals(top.right, material.right, .5f)
            }
            val actualRow = if (actionCount > 1) compose.onNodeWithTag("player.actionToolbar.row", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot else top
            val rowLeft = actualRow.left
            val rowRight = actualRow.right
            val slots = List(actionCount) { index -> compose.onNodeWithTag("player.actionSlot.$index", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot }
            val context = "viewport=$viewport even=$spaceEvenly count=$actionCount state=$state preview=$showNext " +
                "inset=$inset row=$actualRow canvas=$material slots=$slots"
            assertEquals("Glass preserves every native glyph rectangle $context", native, decorated)
            if (actionCount > 1) {
                slots.zipWithNext().forEach { (left, right) ->
                    assertTrue("Configured action order and nonoverlapping slots are retained $context", left.right <= right.left + 1f)
                }
                if (spaceEvenly) {
                    assertTrue("SpaceEvenly leaves space at both row ends $context",
                        slots.first().left > rowLeft && slots.last().right < rowRight)
                } else {
                    assertEquals("SpaceBetween starts at its padded row edge $context", rowLeft, slots.first().left, 1f)
                    assertEquals("SpaceBetween ends at its padded row edge $context", rowRight, slots.last().right, 1f)
                }
            }
            compose.onAllNodes(hasClickAction()).assertCountEquals(actionCount)
            val targets = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }
            targets.forEach { target ->
                assertTrue("Full interaction canvas stays inside the capsule $context target=$target",
                    target.left >= material.left - 1f && target.right <= material.right + 1f &&
                        target.top >= material.top - 1f && target.bottom <= material.bottom + 1f)
            }
            targets.zipWithNext().forEach { (left, right) ->
                assertTrue("Adjacent interaction canvases do not overlap $context", left.right <= right.left + 1f)
            }
            native.zip(decorated).forEach { (before, after) ->
                assertEquals("exact added row footprint", before.center.y, after.center.y, .5f)
                assertEquals(before.width, after.width, .5f)
                assertEquals(before.height, after.height, .5f)
            }
            if (showNext) assertEquals(preview, compose.onNodeWithTag("next.preview").fetchSemanticsNode().boundsInRoot)
            assertTrue("material top fits enlarged container", material.top >= host.top - .5f)
            assertTrue("material bottom fits enlarged container", material.bottom <= host.bottom + .5f)
        }
    }

    @Test fun `landscape panels consume safe insets once and retain twelve dp margins`() {
        val width = mutableStateOf(720.dp)
        val even = mutableStateOf(true)
        val glass = mutableStateOf(true)
        val safeInsets = WindowInsets(left = 20.dp, right = 30.dp)
        compose.setContent {
            AppearanceProvider {
                Row(Modifier.requiredWidth(width.value).windowInsetsPadding(safeInsets)) {
                    Box(Modifier.weight(1f).height(82.dp))
                    Box(Modifier.weight(1f).height(82.dp).testTag("landscape.panel")) {
                        PlayerActionToolbar(List(6) { index -> PlayerActionSlot(index.toString()) {
                            PlayerActionIcon(R.drawable.play, Color.White, {},
                                Modifier.size(24.dp).testTag("landscape.glyph.$index"))
                        } }, even.value, settings = settings.copy(enabled = glass.value),
                            horizontalInsets = safeInsets, landscape = true)
                    }
                }
            }
        }
        for (viewport in listOf(360.dp, 720.dp)) for (spaceEvenly in listOf(true, false)) {
            compose.runOnIdle { width.value = viewport; even.value = spaceEvenly; glass.value = false }
            val original = (0..5).map { compose.onNodeWithTag("landscape.glyph.$it", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot }
            compose.runOnIdle { glass.value = true }
            val panel = compose.onNodeWithTag("landscape.panel").fetchSemanticsNode().boundsInRoot
            val material = compose.onNodeWithTag("player.actionToolbar.material", useUnmergedTree = true)
                .assertHeightIsEqualTo(56.dp).fetchSemanticsNode().boundsInRoot
            val margin = with(compose.density) { 12.dp.toPx() }
            assertEquals(panel.left + margin, material.left, .5f)
            assertEquals(panel.right - margin, material.right, .5f)
            original.forEachIndexed { index, before ->
                val after = compose.onNodeWithTag("landscape.glyph.$index", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                assertEquals("native panel action center", before.center.x, after.center.x, .5f)
                assertEquals(before.width, after.width, .5f)
            }
        }
    }

    @Test fun `host height uses exact twenty dp slots for both presentations and has no empty increment`() {
        val layout = PlayerActionToolbarLayout()
        layout.reconcile(listOf(PlayerActionSlot("equalizer", 20.dp) {}))
        assertEquals(86.dp, layout.hostHeight(50.dp))
        layout.reconcile(emptyList())
        assertEquals(50.dp, layout.hostHeight(50.dp))
    }

    @Test fun `top and lower materials reflect changing background only sources`() {
        val background = mutableStateOf(Color.Red)
        compose.setContent {
            val scene = rememberCanvasBackdrop { drawRect(background.value) }
            AppearanceProvider {
                CompositionLocalProvider(LocalGlassBackdrop provides scene, LocalGlassContainerBackdrop provides scene) {
                    Column {
                        Box(Modifier.testTag("top")) { PlayerTopAction(R.drawable.chevron_down, Color.White, 0f, {}, settings) }
                        PlayerActionToolbar(listOf("a", "menu").map { id -> PlayerActionSlot(id) {
                            PlayerActionIcon(R.drawable.play, Color.White, {}, Modifier.size(24.dp))
                        } }, true, Modifier.testTag("bottom"), settings)
                    }
                }
            }
        }
        fun assertSamples(expected: Color) {
            for (tag in listOf("top", "bottom")) {
                val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                val x = pixels.width / 2
                val actual = pixels[x, pixels.height / 8]
                assertEquals(expected.red, actual.red, .08f)
                assertEquals(expected.blue, actual.blue, .08f)
            }
        }
        assertSamples(Color.Red)
        compose.runOnIdle { background.value = Color.Blue }
        assertSamples(Color.Blue)
    }

    @Composable private fun AppearanceProvider(shape: Shape = RoundedCornerShape(16.dp),
        content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalAppearance provides Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        ), content = content)
    }
}
