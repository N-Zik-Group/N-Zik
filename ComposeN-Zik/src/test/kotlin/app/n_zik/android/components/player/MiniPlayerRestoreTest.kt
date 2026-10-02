package app.n_zik.android.components.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiniPlayerRestoreTest {

    private val collapsedBound = 120.dp

    private fun sheet(value: Dp): PlayerSheetState {
        val sheetState = mockk<PlayerSheetState>()
        every { sheetState.value } returns value
        every { sheetState.collapsedBound } returns collapsedBound
        every { sheetState.collapse(any()) } just Runs
        every { sheetState.snapTo(any()) } just Runs
        return sheetState
    }

    @Test
    fun `a dismissed sheet is brought back with a snappy animated collapse, not a snap`() {
        val sheetState = sheet(value = 0.dp)
        val spec = slot<AnimationSpec<Dp>>()
        every { sheetState.collapse(capture(spec)) } just Runs

        sheetState.showMiniplayerIfDismissed()

        assertTrue(spec.captured is TweenSpec<*>, "the pop must be a tween, not another spec type")
        assertEquals(MINIPLAYER_POP_ANIMATION_MS.toInt(), (spec.captured as TweenSpec<*>).durationMillis)
        verify(exactly = 1) { sheetState.collapse(any()) }
    }

    @Test
    fun `a sheet still sliding into the dismissed zone is brought back too`() {
        // Dismiss animation in flight: value below the collapsed bound but above the
        // dismissed bound, where isDismissed is still false and a value-based condition
        // must not no-op (stop-then-quick-resume race).
        val sheetState = sheet(value = 40.dp)

        sheetState.showMiniplayerIfDismissed()

        verify(exactly = 1) { sheetState.collapse(any()) }
    }

    @Test
    fun `a sheet that is already showing is left untouched`() {
        val sheetState = sheet(value = collapsedBound)

        sheetState.showMiniplayerIfDismissed()

        verify(exactly = 0) { sheetState.collapse(any()) }
    }

    @Test
    fun `presenting minimized from the dismissed zone pops with the animated collapse`() {
        val sheetState = sheet(value = 0.dp)

        sheetState.presentMiniplayerCollapsed()

        verify(exactly = 1) { sheetState.collapse(any()) }
        verify(exactly = 0) { sheetState.snapTo(any()) }
    }

    @Test
    fun `presenting minimized from an open player still snaps down to collapsed`() {
        val sheetState = sheet(value = 600.dp)

        sheetState.presentMiniplayerCollapsed()

        verify(exactly = 1) { sheetState.snapTo(collapsedBound) }
        verify(exactly = 0) { sheetState.collapse(any()) }
    }
}
