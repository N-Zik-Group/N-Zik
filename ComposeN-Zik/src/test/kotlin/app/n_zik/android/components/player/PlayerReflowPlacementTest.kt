package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassSettings
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlayerReflowPlacementTest {
    @get:Rule val compose = createComposeRule()

    private fun advanceFrames(count: Int) {
        repeat(count) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    @Test
    fun `option reflow slides to its final placement without composing each frame`() {
        val target = mutableStateOf(Offset(80f, 100f))
        var compositions = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val motion = rememberPlayerReflowPlacement()
            SideEffect { compositions++ }
            Box(Modifier.offset {
                val point = motion.position(IntSize(400, 800), target.value, true, 1f)
                IntOffset(point.x.roundToInt(), point.y.roundToInt())
            }.size(40.dp).testTag("reflow"))
        }
        val node = compose.onNodeWithTag("reflow")
        assertEquals(100f, node.fetchSemanticsNode().boundsInRoot.top, 0f)
        compose.runOnIdle { target.value = Offset(200f, 400f) }
        advanceFrames(5)
        val intermediate = node.fetchSemanticsNode().boundsInRoot
        assertTrue("Intermediate Y was ${intermediate.top}", intermediate.top > 100f && intermediate.top < 400f)
        assertTrue("Intermediate X was ${intermediate.left}", intermediate.left > 80f && intermediate.left < 200f)
        val duringSlide = compositions
        advanceFrames(24)
        assertEquals(400f, node.fetchSemanticsNode().boundsInRoot.top, 0f)
        assertEquals(200f, node.fetchSemanticsNode().boundsInRoot.left, 0f)
        assertEquals(duringSlide, compositions)
    }

    @Test
    fun `toolbar glyphs and hit targets follow touch scrolling without waiting for reflow animation`() {
        var clicks = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette,
                    typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape),
                LocalPlayerTransportGeometry provides remember { PlayerTransportGeometry() },
            ) {
                PlayerGlassBudgetLayout(enabled = false, reserveLowerContent = true,
                    modifier = Modifier.requiredSize(400.dp, 300.dp).playerTransportGeometryHost(),
                    header = { Box(Modifier.size(400.dp, 50.dp)) }, artwork = {},
                    information = {
                        Box(Modifier.size(400.dp, 140.dp))
                        PlayerActionToolbar(listOf("first", "second").map { id -> PlayerActionSlot(id) {
                            PlayerActionIcon(R.drawable.play, Color.White, { clicks++ },
                                Modifier.size(24.dp).testTag("scroll.glyph.$id"))
                        } }, true, settings = GlassSettings(enabled = false, jellyEnabled = false),
                            landscape = false, nativeBackground = Color.Blue)
                        Box(Modifier.size(400.dp, 220.dp))
                    })
            }
        }
        advanceFrames(24)
        val viewport = compose.onNode(hasScrollAction())
        fun toolbarCenter() = compose.onNodeWithTag("player.actionToolbar.native", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center.y
        fun assertAligned() {
            for (id in listOf("first", "second")) {
                val glyph = compose.onNodeWithTag("scroll.glyph.$id", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                assertEquals("Glyph follows its surface during the drag", toolbarCenter(), glyph.center.y, 1f)
            }
        }
        val initial = toolbarCenter()
        viewport.performTouchInput {
            down(Offset(center.x, height * .85f))
            moveTo(Offset(center.x, height * .5f), delayMillis = 32)
        }
        advanceFrames(1)
        val scrolled = toolbarCenter()
        assertTrue("Upward drag moves the toolbar immediately", scrolled < initial)
        assertAligned()
        viewport.performTouchInput { moveTo(Offset(center.x, height * .8f), delayMillis = 32) }
        advanceFrames(1)
        assertTrue("Downward drag moves the toolbar back immediately", toolbarCenter() > scrolled)
        assertAligned()
        viewport.performTouchInput { cancel() }
        compose.onNodeWithTag("scroll.glyph.first", useUnmergedTree = true).performTouchInput { click() }
        advanceFrames(1)
        compose.runOnIdle { assertEquals("Visible glyph retains its click target", 1, clicks) }
    }

    @Test
    fun `viewport changes and disabled motion immediately use the safe target`() {
        val viewport = mutableStateOf(IntSize(400, 800))
        val target = mutableStateOf(Offset(80f, 100f))
        val enabled = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val motion = rememberPlayerReflowPlacement()
            Box(Modifier.offset {
                val point = motion.position(viewport.value, target.value, enabled.value, .8f)
                IntOffset(point.x.roundToInt(), point.y.roundToInt())
            }.size(40.dp).testTag("reflow"))
        }
        val node = compose.onNodeWithTag("reflow")
        compose.runOnIdle { target.value = Offset(80f, 400f) }
        advanceFrames(4)
        assertTrue(node.fetchSemanticsNode().boundsInRoot.top in 101f..399f)
        compose.runOnIdle {
            viewport.value = IntSize(800, 400)
            target.value = Offset(300f, 140f)
        }
        advanceFrames(1)
        assertEquals(140f, node.fetchSemanticsNode().boundsInRoot.top, 0f)
        assertEquals(300f, node.fetchSemanticsNode().boundsInRoot.left, 0f)
        compose.runOnIdle {
            enabled.value = false
            target.value = Offset(20f, 240f)
        }
        advanceFrames(1)
        assertEquals(240f, node.fetchSemanticsNode().boundsInRoot.top, 0f)
        advanceFrames(24)
        assertEquals(240f, node.fetchSemanticsNode().boundsInRoot.top, 0f)
    }

    @Test
    fun `moving parent and resizing its slot preserve the glyph center during reflow`() {
        val target = mutableStateOf(IntOffset(80, 100))
        val slotSize = mutableStateOf(40.dp)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val motion = rememberPlayerReflowPlacement()
            Box(Modifier.offset { target.value }.size(slotSize.value)
                .playerReflowPlacement(motion, GlassSettings(jellyEnabled = false)),
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(20.dp).testTag("glyph"))
            }
        }
        val node = compose.onNodeWithTag("glyph")
        val initial = node.fetchSemanticsNode().boundsInRoot.center.y
        val finalCenter = 400f + node.fetchSemanticsNode().boundsInRoot.height * 2f
        compose.runOnIdle {
            target.value = IntOffset(80, 400)
            slotSize.value = 80.dp
        }
        advanceFrames(5)
        val intermediate = node.fetchSemanticsNode().boundsInRoot.center.y
        assertTrue("Intermediate center was $intermediate", intermediate > initial && intermediate < finalCenter)
        advanceFrames(24)
        assertEquals(finalCenter, node.fetchSemanticsNode().boundsInRoot.center.y, 0f)
    }
}
