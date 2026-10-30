package app.n_zik.android.components.glass

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class GlassHighlightTrackingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `native hold tracks consumed movement without owning gestures and cancel stays authoritative`() {
        val source = MutableInteractionSource()
        lateinit var node: GlassHighlightNode
        var press: PressInteraction.Press? = null
        var movementConsumed = false
        var clicks = 0
        var holds = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LaunchedEffect(source) {
                source.interactions.collect { if (it is PressInteraction.Press) press = it }
            }
            GlassRippleProvider(GlassSettings(enabled = true)) {
                val providedIndication = LocalIndication.current as IndicationNodeFactory
                val indication = object : IndicationNodeFactory {
                    override fun equals(other: Any?) = other === this
                    override fun hashCode() = System.identityHashCode(this)
                    override fun create(interactionSource: InteractionSource): DelegatableNode =
                        providedIndication.create(interactionSource).also { node = it as GlassHighlightNode }
                }
                Box(Modifier.padding(start = 16.dp, top = 8.dp)) {
                    Box(
                        Modifier.size(160.dp, 64.dp).testTag("row")
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Final)
                                        event.changes.firstOrNull {
                                            it.pressed && it.position != it.previousPosition
                                        }?.let { movementConsumed = it.isConsumed }
                                    }
                                }
                            }
                            .combinedClickable(
                                interactionSource = source, indication = indication,
                                onClick = { clicks++ }, onLongClick = { holds++ },
                            )
                    )
                }
            }
        }
        val row = compose.onNodeWithTag("row")
        row.performTouchInput { down(Offset(20f, 20f)) }
        // Native clickables may defer Press emission on Android's main looper.
        shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(Offset(20f, 20f), requireNotNull(press).pressPosition)
            assertEquals(Offset.Zero, node.highlight?.offset)
        }
        row.performTouchInput { moveTo(Offset(22f, 20f)) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse("The indication must not consume movement", movementConsumed)
            assertEquals(Offset(2f, 0f), node.highlight?.offset)
        }

        compose.mainClock.advanceTimeBy(800)
        row.performTouchInput {
            advanceEventTime(800)
            moveTo(Offset(120f, 40f))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals(1, holds)
            assertTrue("The native long click owns movement", movementConsumed)
            assertEquals(Offset(100f, 20f), node.highlight?.offset)
            assertTrue(source.tryEmit(PressInteraction.Cancel(requireNotNull(press))))
        }
        compose.mainClock.advanceTimeBy(1600)
        row.performTouchInput { moveTo(Offset(80f, 30f)) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals("Cancelled movement must not revive the highlight", 0f, requireNotNull(node.highlight).pressProgress)
            assertEquals(Offset.Zero, node.highlight?.offset)
        }
        row.performTouchInput { up() }
        compose.runOnIdle { assertEquals(0, clicks); assertEquals(1, holds) }
    }
}
