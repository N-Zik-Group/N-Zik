package app.n_zik.android.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShimmerPhaseTest {
    @Test
    fun `the sweep phase comes from the frame clock and repeats without a per item restart`() {
        assertEquals(0f, shimmerPhaseAt(0))
        assertEquals(.25f, shimmerPhaseAt(400_000_000L))
        assertEquals(.75f, shimmerPhaseAt(1_200_000_000L))
        assertEquals(0f, shimmerPhaseAt(1_600_000_000L))
        assertEquals(.25f, shimmerPhaseAt(2_000_000_000L))
    }
}
