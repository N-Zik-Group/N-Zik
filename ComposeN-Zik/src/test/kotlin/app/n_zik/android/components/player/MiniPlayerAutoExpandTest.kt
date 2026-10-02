package app.n_zik.android.components.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.unit.Dp
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiniPlayerAutoExpandTest {

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `collapses to the mini-player right away with the snappy spec, then expands after the delay`() = runTest {
        val sheetState = mockk<PlayerSheetState>()
        val spec = slot<AnimationSpec<Dp>>()
        every { sheetState.collapse(capture(spec)) } just Runs
        every { sheetState.expandSoft() } just Runs

        presentMiniplayerThenExpand(sheetState)

        assertTrue(spec.captured is TweenSpec<*>, "the pop must be a tween, not another spec type")
        assertEquals(MINIPLAYER_POP_ANIMATION_MS.toInt(), (spec.captured as TweenSpec<*>).durationMillis)
        verify(exactly = 1) { sheetState.collapse(any()) }
        verify(exactly = 0) { sheetState.expandSoft() }

        advanceUntilIdle()

        verify(exactly = 1) { sheetState.expandSoft() }

        verifyOrder {
            sheetState.collapse(any())
            sheetState.expandSoft()
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `invokes onPresent exactly once, before the collapse`() = runTest {
        val sheetState = mockk<PlayerSheetState>()
        every { sheetState.collapse(any()) } just Runs
        every { sheetState.expandSoft() } just Runs
        val onPresent = mockk<() -> Unit>(relaxed = true)

        presentMiniplayerThenExpand(sheetState, onPresent = onPresent)

        verify(exactly = 1) { sheetState.collapse(any()) }
        verify(exactly = 0) { sheetState.expandSoft() }

        advanceUntilIdle()

        verify(exactly = 1) { sheetState.expandSoft() }

        verifyOrder {
            onPresent()
            sheetState.collapse(any())
            sheetState.expandSoft()
        }
    }
}
