package app.n_zik.android.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.it.fast4x.rimusic.ui.screens.player.dismissedAnchor
import app.n_zik.android.components.flingWouldDismiss
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the [flingWouldDismiss] mirror against the real [PlayerSheetState.performFling] dismiss
 * decision (review finding: the mirror is a hand-rolled copy of the thresholds — 250 / -100 /
 * the 0.80f pulled-position band — and nothing verified it did not diverge silently, which
 * would turn a locked guest's genuine dismiss attempt into a silent spring-back without the
 * "The host controls playback" toast, or fire the toast on a gesture that would not dismiss).
 *
 * Observation point: every performFling branch calls its anchor callback synchronously before
 * its first animation frame (dismiss → [dismissedAnchor], collapse → collapsed, expand →
 * expanded), so the dismiss decision is readable from the first chunk of the launched coroutine.
 * The [TestMonotonicFrameClock] (repo pattern, see CollapsibleHeaderTest) gives the animation
 * frames a frame clock to suspend on instead of failing in a JVM without a Choreographer.
 */
class FlingWouldDismissMirrorTest {

    private val dismissedBound = 0.dp
    private val collapsedBound = 120.dp
    private val expandedBound = 800.dp

    /** Velocity grid straddling the `> 250` and `< -100` thresholds. */
    private val velocities = listOf(300f, 251f, 250f, 249f, -150f, -101f, -100f, -99f, -50f, 0f)

    /**
     * Position grid straddling the pulled-position band: l1 = dismissed + 0.80 * (collapsed -
     * dismissed) = 96.dp, l2 = collapsed + (expanded - collapsed) / 2 = 460.dp.
     */
    private val positions =
        listOf(0.dp, 50.dp, 96.dp, 96.5f.dp, 100.dp, 110.dp, 120.dp, 120.5f.dp, 200.dp, 460.dp, 800.dp)

    private fun sheetState(scope: CoroutineScope, value: Dp, anchors: MutableList<Int>) =
        PlayerSheetState(
            draggableState = DraggableState { },
            coroutineScope = scope,
            animatable = Animatable(value, Dp.VectorConverter).also {
                it.updateBounds(dismissedBound, expandedBound)
            },
            onAnchorChanged = { anchors += it },
            collapsedBound = collapsedBound,
        )

    @Test
    fun `flingWouldDismiss agrees with performFling around every threshold`() {
        val scheduler = TestCoroutineScheduler()
        runTest(context = scheduler + TestMonotonicFrameClock(scheduler)) {
            for (velocity in velocities) {
                for (position in positions) {
                    val anchors = mutableListOf<Int>()
                    val state = sheetState(this, position, anchors)

                    state.performFling(velocity) { }
                    // First chunk only: the branch's anchor decision, before the frame wait.
                    runCurrent()

                    val realDismiss = anchors.firstOrNull() == dismissedAnchor
                    assertEquals(
                        realDismiss,
                        flingWouldDismiss(state, velocity),
                        "mirror diverged from performFling at velocity=$velocity value=$position",
                    )
                }
            }
        }
    }

    private class TestMonotonicFrameClock(
        private val scheduler: TestCoroutineScheduler,
    ) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            delay(FRAME_INTERVAL_MS)
            return onFrame(scheduler.currentTime * NANOS_PER_MS)
        }

        private companion object {
            const val FRAME_INTERVAL_MS = 16L
            const val NANOS_PER_MS = 1_000_000L
        }
    }
}
