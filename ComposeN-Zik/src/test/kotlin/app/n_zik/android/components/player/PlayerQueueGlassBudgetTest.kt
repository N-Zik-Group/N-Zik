package app.n_zik.android.components.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerQueueGlassBudgetTest {
    @Test fun `queue visibility limits player optics from opening until closing finishes`() {
        assertFalse(playerQueueUnderlayCovered(inheritedCovered = false, queueVisible = false))
        // The owner stays visible through opening, partial expansion, full expansion and closing.
        assertTrue(playerQueueUnderlayCovered(inheritedCovered = false, queueVisible = true))
        assertFalse(playerQueueUnderlayCovered(inheritedCovered = false, queueVisible = false))
    }

    @Test fun `global modal coverage survives queue opening and closure`() {
        for (queueVisible in listOf(false, true, false)) {
            assertTrue(playerQueueUnderlayCovered(inheritedCovered = true, queueVisible = queueVisible))
        }
    }
}
