package app.n_zik.android.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.ui.screens.player.PlayerSheetState
import app.n_zik.android.ui.screens.player.collapsedAnchor
import app.n_zik.android.ui.screens.player.dismissedAnchor
import app.n_zik.android.ui.screens.player.expandedAnchor
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerSheetStateAnchorTest {

    private val dismissedBound = 0.dp
    private val collapsedBound = 120.dp
    private val expandedBound = 800.dp

    private fun sheetState(scope: TestScope, anchors: MutableList<Int>) = PlayerSheetState(
        draggableState = DraggableState { },
        coroutineScope = scope,
        animatable = Animatable(dismissedBound, Dp.VectorConverter).also {
            it.updateBounds(dismissedBound, expandedBound)
        },
        onAnchorChanged = { anchors += it },
        collapsedBound = collapsedBound,
    )

    @Test
    fun `snapTo the collapsed bound remembers the collapsed anchor`() = runTest {
        val anchors = mutableListOf<Int>()
        val state = sheetState(this, anchors)

        state.snapTo(collapsedBound)

        assertEquals(listOf(collapsedAnchor), anchors)
    }

    @Test
    fun `snapTo the expanded bound remembers the expanded anchor`() = runTest {
        val anchors = mutableListOf<Int>()
        val state = sheetState(this, anchors)

        state.snapTo(expandedBound)

        assertEquals(listOf(expandedAnchor), anchors)
    }

    @Test
    fun `snapTo the dismissed bound remembers the dismissed anchor`() = runTest {
        val anchors = mutableListOf<Int>()
        val state = sheetState(this, anchors)

        state.snapTo(dismissedBound)

        assertEquals(listOf(dismissedAnchor), anchors)
    }

    @Test
    fun `snapTo a value between anchors keeps the last anchor`() = runTest {
        val anchors = mutableListOf<Int>()
        val state = sheetState(this, anchors)

        state.snapTo(300.dp)

        assertEquals(emptyList<Int>(), anchors)
    }

    @Test
    fun `snapToAndWait remembers the anchor too`() = runTest {
        val anchors = mutableListOf<Int>()
        val state = sheetState(this, anchors)

        state.snapToAndWait(collapsedBound)

        assertEquals(listOf(collapsedAnchor), anchors)
        assertEquals(collapsedBound, state.value)
    }
}
