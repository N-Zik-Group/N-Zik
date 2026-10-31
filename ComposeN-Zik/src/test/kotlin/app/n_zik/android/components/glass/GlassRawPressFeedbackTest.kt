package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class GlassRawPressFeedbackTest {
    @get:Rule val compose = createComposeRule()

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `raw cover without native indication stays visually unchanged and retains its tap with Glass enabled`() {
        var taps = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            GlassRippleProvider(GlassSettings(enabled = true)) {
                val feedback = rememberGlassRawPressFeedback()
                Box(Modifier.size(100.dp).testTag("excluded.cover")
                    .then(feedback.modifier()).background(Color.Blue)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = { feedback.onPress(this, it) }, onTap = { taps++ })
                    })
            }
        }
        val cover = compose.onNodeWithTag("excluded.cover")
        val resting = cover.captureToImage().toPixelMap()
        cover.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(800)
        val held = cover.captureToImage().toPixelMap()
        for (y in 0 until held.height) for (x in 0 until held.width) {
            assertEquals("A native cover without ripple must not gain Glass feedback", resting[x, y], held[x, y])
        }
        cover.performTouchInput { up() }
        compose.runOnIdle { assertEquals("The original raw tap remains the sole action owner", 1, taps) }
    }

    @Test fun `existing raw tap detector tracks hold and dispatches each gesture once with global on and off`() {
        val global = mutableStateOf(true)
        var node: GlassHighlightNode? = null
        var taps = 0
        var doubleTaps = 0
        var holds = 0
        val movementConsumption = mutableMapOf(true to mutableListOf<Boolean>(), false to mutableListOf<Boolean>())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            GlassRippleProvider(GlassSettings(enabled = global.value)) {
                val supplied = LocalIndication.current
                val captured = remember(supplied) {
                    object : IndicationNodeFactory {
                        override fun equals(other: Any?) = other === this
                        override fun hashCode() = System.identityHashCode(this)
                        override fun create(interactionSource: InteractionSource): DelegatableNode =
                            (supplied as IndicationNodeFactory).create(interactionSource).also {
                                node = it as GlassHighlightNode
                            }
                    }
                }
                CompositionLocalProvider(LocalIndication provides captured) {
                    Row {
                        for (adapted in listOf(true, false)) {
                            val feedback = rememberGlassRawPressFeedback(nativeIndication = LocalIndication.current)
                            Box(Modifier.size(100.dp).testTag(if (adapted) "cover" else "native")
                                .then(if (adapted) feedback.modifier() else Modifier)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = { if (adapted) feedback.onPress(this, it) },
                                        onTap = { if (adapted) taps++ },
                                        onDoubleTap = { if (adapted) doubleTaps++ },
                                        onLongPress = { if (adapted) holds++ },
                                    )
                                }
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Final)
                                            event.changes.firstOrNull { it.pressed && it.position != it.previousPosition }
                                                ?.let { movementConsumption.getValue(adapted).add(it.isConsumed) }
                                        }
                                    }
                                })
                        }
                    }
                }
            }
        }
        val cover = compose.onNodeWithTag("cover")
        val start = Offset(20f, 20f)
        cover.performTouchInput { down(start) }
        compose.mainClock.advanceTimeByFrame()
        cover.performTouchInput { moveTo(start + Offset(3f, 2f)) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals(Offset(3f, 2f), node?.highlight?.offset)
        }
        cover.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1600)
        compose.runOnIdle {
            assertTrue(node?.highlight?.pressProgress == 0f)
            assertEquals(0, taps + doubleTaps + holds)
        }
        // The existing detector can consume movement itself (including after a long hold).
        // Replay the identical sequence without the adapter and compare observable ownership.
        val native = compose.onNodeWithTag("native")
        native.performTouchInput { down(start) }
        compose.mainClock.advanceTimeByFrame()
        native.performTouchInput { moveTo(start + Offset(3f, 2f)) }
        compose.mainClock.advanceTimeByFrame()
        native.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1600)
        compose.runOnIdle {
            assertTrue(movementConsumption.getValue(true).isNotEmpty())
            assertEquals(movementConsumption.getValue(false), movementConsumption.getValue(true))
        }
        for (enabled in listOf(true, false)) {
            compose.runOnIdle { global.value = enabled }
            repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
            if (!enabled) compose.runOnIdle { assertFalse(node?.isAttached == true) }
            cover.performTouchInput { click() }
            compose.mainClock.advanceTimeBy(600)
            cover.performTouchInput { doubleClick() }
            compose.mainClock.advanceTimeBy(600)
            cover.performTouchInput { longClick() }
            compose.mainClock.advanceTimeBy(1600)
            val expected = if (enabled) 1 else 2
            compose.runOnIdle {
                assertEquals(expected, taps)
                assertEquals(expected, doubleTaps)
                assertEquals(expected, holds)
            }
        }
    }
}
