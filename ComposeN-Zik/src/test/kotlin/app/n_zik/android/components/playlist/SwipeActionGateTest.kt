package app.n_zik.android.components.playlist

import app.n_zik.android.components.ui.SwipeActionGate
import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeActionGateTest {
    @Test fun `one pointer action remains latched after pointer release until actual settling finishes`() {
        val gate = SwipeActionGate()
        var actions = 0
        gate.beginGesture()
        repeat(8) { gate.run { actions++ } }
        assertEquals(1, actions)
        gate.endGesture()
        repeat(8) { gate.run { actions++ } }
        assertEquals(1, actions)
        gate.finishSettling()
        gate.run { actions++ }
        gate.run { actions++ }
        assertEquals(3, actions)
        gate.beginGesture()
        repeat(8) { gate.run { actions++ } }
        assertEquals(4, actions)
    }
}
