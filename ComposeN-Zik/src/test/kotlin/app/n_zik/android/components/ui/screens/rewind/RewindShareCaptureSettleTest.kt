package app.n_zik.android.components.ui.screens.rewind

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the Rewind export capture settle time ([RewindPageSettleMs]): the deck plays
 * itself while its one-shot animations are cut, so each page settles to its final state the
 * instant it is activated. The settle must still be long enough to cover the page jump, the
 * recomposition and a couple of clean frames, but short enough to keep the 16-page pass quick.
 */
class RewindShareCaptureSettleTest {

    @Test
    fun settleCoversJumpRecompositionAndCleanFrames() {
        // Below 250 ms the page could still be mid-recomposition when its frame is copied;
        // above 3 s the pass would stall without buying anything (reveals are cut).
        assertTrue(RewindPageSettleMs in 250L..3_000L)
    }

    @Test
    fun fullCapturePassStaysQuick() {
        // 16 pages × settle must stay under 30 s, before copy/compress overhead.
        assertTrue(RewindPageSettleMs * 16 < 30_000L)
    }
}
