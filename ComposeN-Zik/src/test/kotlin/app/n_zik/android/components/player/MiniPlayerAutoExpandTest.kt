package app.n_zik.android.components.player

import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class MiniPlayerAutoExpandTest {

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `snaps to collapsed immediately then expands after the delay`() = runTest {
        val sheetState = mockk<PlayerSheetState>()
        val collapsedBound = 120.dp
        every { sheetState.collapsedBound } returns collapsedBound
        every { sheetState.snapTo(any()) } just Runs
        every { sheetState.expandSoft() } just Runs

        presentMiniplayerThenExpand(sheetState)

        verify(exactly = 1) { sheetState.snapTo(collapsedBound) }
        verify(exactly = 0) { sheetState.expandSoft() }

        advanceUntilIdle()

        verify(exactly = 1) { sheetState.expandSoft() }

        verifyOrder {
            sheetState.snapTo(collapsedBound)
            sheetState.expandSoft()
        }
    }
}
