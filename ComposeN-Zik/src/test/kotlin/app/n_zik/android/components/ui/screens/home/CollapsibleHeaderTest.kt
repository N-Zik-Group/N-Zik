package app.n_zik.android.components.ui.screens.home

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollapsibleHeaderTest {

    @Test
    fun `title alpha is fully visible when the header is expanded`() {
        assertEquals(1f, collapsibleTitleAlpha(offset = 0f, headerHeight = 100))
    }

    @Test
    fun `title alpha is halfway faded at the middle of the fade range`() {
        assertEquals(0.5, collapsibleTitleAlpha(offset = -15f, headerHeight = 100).toDouble(), 1e-4)
    }

    @Test
    fun `title alpha reaches 0 at the end of the fade range and stays there`() {
        assertEquals(0.0, collapsibleTitleAlpha(offset = -30f, headerHeight = 100).toDouble(), 1e-4)
        assertEquals(0f, collapsibleTitleAlpha(offset = -100f, headerHeight = 100))
    }

    @Test
    fun `title alpha is fully visible when the header height is not measured yet`() {
        assertEquals(1f, collapsibleTitleAlpha(offset = 0f, headerHeight = 0))
        assertEquals(1f, collapsibleTitleAlpha(offset = -30f, headerHeight = 0))
    }

    @Test
    fun `title is fully visible when it sits below the status bar`() {
        assertEquals(1f, titleStatusBarAlpha(titleTop = 100f, statusBarTop = 50))
        assertEquals(1f, titleStatusBarAlpha(titleTop = 50f, statusBarTop = 50))
        assertEquals(1f, titleStatusBarAlpha(titleTop = Float.POSITIVE_INFINITY, statusBarTop = 50))
    }

    @Test
    fun `title fades while sliding under the status bar`() {
        assertEquals(0.5, titleStatusBarAlpha(titleTop = 25f, statusBarTop = 50).toDouble(), 1e-4)
    }

    @Test
    fun `title is hidden when the root bar is gone and it fills the status bar area`() {
        assertEquals(0f, titleStatusBarAlpha(titleTop = 0f, statusBarTop = 50))
        assertEquals(0f, titleStatusBarAlpha(titleTop = -80f, statusBarTop = 50))
    }

    @Test
    fun `title stays visible when there is no status bar inset`() {
        assertEquals(1f, titleStatusBarAlpha(titleTop = 0f, statusBarTop = 0))
    }

    @Test
    fun `fling ending above the midpoint snaps to expanded`() {
        assertEquals(0f, collapsibleHeaderSnapTarget(offset = -30f, headerHeight = 100))
    }

    @Test
    fun `fling ending below the midpoint snaps to collapsed`() {
        assertEquals(-100f, collapsibleHeaderSnapTarget(offset = -70f, headerHeight = 100))
    }

    @Test
    fun `fling ending exactly on the midpoint snaps to expanded`() {
        assertEquals(0f, collapsibleHeaderSnapTarget(offset = -50f, headerHeight = 100))
    }

    @Test
    fun `fling ending exactly on a bound returns that bound`() {
        assertEquals(0f, collapsibleHeaderSnapTarget(offset = 0f, headerHeight = 100))
        assertEquals(-100f, collapsibleHeaderSnapTarget(offset = -100f, headerHeight = 100))
    }

    @Test
    fun `unmeasured height returns zero`() {
        assertEquals(0f, collapsibleHeaderSnapTarget(offset = -30f, headerHeight = 0))
        assertEquals(0f, collapsibleHeaderSnapTarget(offset = 0f, headerHeight = 0))
    }

    @Test
    fun `fling above the midpoint animates to expanded`() = runWithFrames {
        val headerOffset = mutableFloatStateOf(-30f)
        val snapJob = mutableStateOf<Job?>(null)
        val connection = collapsibleHeaderConnection(headerHeight = 100, headerOffset = headerOffset, scope = backgroundScope, snapJob = snapJob, enabled = true)

        connection.onPostFling(Velocity.Zero, Velocity(0f, -100f))

        advanceTimeBy(500)
        assertEquals(0f, headerOffset.floatValue)
    }

    @Test
    fun `fling below the midpoint animates to collapsed`() = runWithFrames {
        val headerOffset = mutableFloatStateOf(-70f)
        val snapJob = mutableStateOf<Job?>(null)
        val connection = collapsibleHeaderConnection(headerHeight = 100, headerOffset = headerOffset, scope = backgroundScope, snapJob = snapJob, enabled = true)

        connection.onPostFling(Velocity.Zero, Velocity(0f, 100f))

        advanceTimeBy(500)
        assertEquals(-100f, headerOffset.floatValue)
    }

    @Test
    fun `fling while already on a bound starts no animation`() = runTest {
        val headerOffset = mutableFloatStateOf(0f)
        val snapJob = mutableStateOf<Job?>(null)
        val connection = collapsibleHeaderConnection(headerHeight = 100, headerOffset = headerOffset, scope = backgroundScope, snapJob = snapJob, enabled = true)

        connection.onPostFling(Velocity.Zero, Velocity(0f, -100f))

        advanceUntilIdle()
        assertEquals(0f, headerOffset.floatValue)
        assertNull(snapJob.value)
    }

    @Test
    fun `touch delta cancels a running snap`() = runWithFrames {
        val headerOffset = mutableFloatStateOf(-70f)
        val snapJob = mutableStateOf<Job?>(null)
        val connection = collapsibleHeaderConnection(headerHeight = 100, headerOffset = headerOffset, scope = backgroundScope, snapJob = snapJob, enabled = true)

        connection.onPostFling(Velocity.Zero, Velocity(0f, -100f))
        advanceTimeBy(50)
        assertTrue(headerOffset.floatValue < -70f, "snap should have started moving before the touch")

        val consumed = connection.onPreScroll(Offset(0f, 20f), NestedScrollSource.UserInput)

        assertEquals(Offset.Zero, consumed)
        val offsetAfterTouch = headerOffset.floatValue
        advanceTimeBy(500)
        assertEquals(offsetAfterTouch, headerOffset.floatValue, "cancelled snap must not keep animating")
        assertNull(snapJob.value)
    }

    @Test
    fun `fling with unmeasured height is a no-op`() = runTest {
        val headerOffset = mutableFloatStateOf(-30f)
        val snapJob = mutableStateOf<Job?>(null)
        val connection = collapsibleHeaderConnection(headerHeight = 0, headerOffset = headerOffset, scope = backgroundScope, snapJob = snapJob, enabled = true)

        connection.onPostFling(Velocity.Zero, Velocity(0f, -100f))

        advanceUntilIdle()
        assertEquals(-30f, headerOffset.floatValue)
        assertNull(snapJob.value)
    }

    @Test
    fun `cancelling the snap job stops a running animation`() = runWithFrames {
        val headerOffset = mutableFloatStateOf(-30f)
        val snapJob = mutableStateOf<Job?>(null)
        val connection = collapsibleHeaderConnection(headerHeight = 100, headerOffset = headerOffset, scope = backgroundScope, snapJob = snapJob, enabled = true)

        connection.onPostFling(Velocity.Zero, Velocity(0f, 100f))
        advanceTimeBy(50)
        assertTrue(headerOffset.floatValue < 0f, "snap should have started moving before the cancel")

        snapJob.value?.cancel()
        advanceTimeBy(500)
        val frozen = headerOffset.floatValue
        advanceTimeBy(500)
        assertEquals(frozen, headerOffset.floatValue, "cancelled job must not keep animating")
    }

    @Test
    fun `drag keeps following the finger and clamps at the bounds`() = runTest {
        val headerOffset = mutableFloatStateOf(0f)
        val snapJob = mutableStateOf<Job?>(null)
        val connection = collapsibleHeaderConnection(headerHeight = 100, headerOffset = headerOffset, scope = backgroundScope, snapJob = snapJob, enabled = true)

        val consumed = connection.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)

        assertEquals(Offset.Zero, consumed)
        assertEquals(-40f, headerOffset.floatValue)

        connection.onPreScroll(Offset(0f, -90f), NestedScrollSource.UserInput)
        assertEquals(-100f, headerOffset.floatValue)

        connection.onPreScroll(Offset(0f, -90f), NestedScrollSource.UserInput)
        assertEquals(-100f, headerOffset.floatValue)
    }

    @Test
    fun `pre-scroll movement is reverted when the content consumes nothing`() = runTest {
        val headerOffset = mutableFloatStateOf(-20f)
        val snapJob = mutableStateOf<Job?>(null)
        val connection = collapsibleHeaderConnection(headerHeight = 100, headerOffset = headerOffset, scope = backgroundScope, snapJob = snapJob, enabled = true)

        connection.onPreScroll(Offset(0f, -30f), NestedScrollSource.UserInput)
        assertEquals(-50f, headerOffset.floatValue)

        // Content is at an edge and consumes nothing (e.g. pull-to-refresh pull)
        connection.onPostScroll(Offset.Zero, Offset(0f, -30f), NestedScrollSource.UserInput)
        assertEquals(-20f, headerOffset.floatValue, "header must stay glued to unmoved content")
    }

    @Test
    fun `expansion is kept when the content is stuck at its top edge`() = runTest {
        val headerOffset = mutableFloatStateOf(-70f)
        val snapJob = mutableStateOf<Job?>(null)
        val connection = collapsibleHeaderConnection(headerHeight = 100, headerOffset = headerOffset, scope = backgroundScope, snapJob = snapJob, enabled = true)

        connection.onPreScroll(Offset(0f, 30f), NestedScrollSource.UserInput)
        // List already at its top: consumes nothing
        connection.onPostScroll(Offset.Zero, Offset(0f, 30f), NestedScrollSource.UserInput)

        assertEquals(-40f, headerOffset.floatValue, "a shut header must reopen when the list is at its top")
    }

    @Test
    fun `pre-scroll movement is kept when the content consumes the delta`() = runTest {
        val headerOffset = mutableFloatStateOf(0f)
        val snapJob = mutableStateOf<Job?>(null)
        val connection = collapsibleHeaderConnection(headerHeight = 100, headerOffset = headerOffset, scope = backgroundScope, snapJob = snapJob, enabled = true)

        connection.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)
        connection.onPostScroll(Offset(0f, -40f), Offset.Zero, NestedScrollSource.UserInput)
        assertEquals(-40f, headerOffset.floatValue)
    }

    @Test
    fun `clamped pre-scroll movement is reverted by the amount actually applied`() = runTest {
        val headerOffset = mutableFloatStateOf(0f)
        val snapJob = mutableStateOf<Job?>(null)
        val connection = collapsibleHeaderConnection(headerHeight = 100, headerOffset = headerOffset, scope = backgroundScope, snapJob = snapJob, enabled = true)

        // Positive delta at the expanded bound applies nothing
        connection.onPreScroll(Offset(0f, 10f), NestedScrollSource.UserInput)
        assertEquals(0f, headerOffset.floatValue)

        connection.onPostScroll(Offset.Zero, Offset(0f, 10f), NestedScrollSource.UserInput)
        assertEquals(0f, headerOffset.floatValue, "revert must not push the header past its bound")
    }

    @Test
    fun `disabled connection ignores scroll and fling`() = runTest {
        val headerOffset = mutableFloatStateOf(-30f)
        val snapJob = mutableStateOf<Job?>(null)
        val connection = collapsibleHeaderConnection(headerHeight = 100, headerOffset = headerOffset, scope = backgroundScope, snapJob = snapJob, enabled = false)

        val consumed = connection.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)

        assertEquals(Offset.Zero, consumed)
        assertEquals(-30f, headerOffset.floatValue, "disabled header must not follow the scroll")

        connection.onPostFling(Velocity.Zero, Velocity(0f, 100f))
        advanceUntilIdle()
        assertEquals(-30f, headerOffset.floatValue, "disabled header must not snap")
        assertNull(snapJob.value)
    }

    companion object {
        /** A [MonotonicFrameClock] driven by the test scheduler: `Animatable` needs one to run its animation frames, and a plain `runTest` context has none. */
        private class TestMonotonicFrameClock(
            private val scheduler: TestCoroutineScheduler
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

        /** Runs a test body with a frame clock so snap animations can complete. */
        private fun runWithFrames(testBody: suspend TestScope.() -> Unit) {
            val scheduler = TestCoroutineScheduler()
            runTest(context = scheduler + TestMonotonicFrameClock(scheduler)) {
                testBody()
            }
        }
    }
}
