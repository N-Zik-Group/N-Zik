package app.n_zik.android.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamInfoUpsertDecisionTest {

    @Test
    fun `regular stream always runs the metadata flow`() {
        assertTrue(
            ListenTogetherPlayerBridge.shouldRunStreamInfoUpsert(
                isListenTogether = false,
                listenTogetherHistoryEnabled = false,
            )
        )
        assertTrue(
            ListenTogetherPlayerBridge.shouldRunStreamInfoUpsert(
                isListenTogether = false,
                listenTogetherHistoryEnabled = true,
            )
        )
    }

    @Test
    fun `listen together stream runs the flow when the history option is on`() {
        assertTrue(
            ListenTogetherPlayerBridge.shouldRunStreamInfoUpsert(
                isListenTogether = true,
                listenTogetherHistoryEnabled = true,
            )
        )
    }

    @Test
    fun `listen together stream skips the flow when the history option is off`() {
        assertFalse(
            ListenTogetherPlayerBridge.shouldRunStreamInfoUpsert(
                isListenTogether = true,
                listenTogetherHistoryEnabled = false,
            )
        )
    }

    @Test
    fun `flow decision aligns with the local history decision`() {
        // The same user choice gates both: the track enters the library
        // (verified upsert) exactly when it is also recorded in the history.
        for (enabled in listOf(false, true)) {
            val upsert = ListenTogetherPlayerBridge.shouldRunStreamInfoUpsert(
                isListenTogether = true,
                listenTogetherHistoryEnabled = enabled,
            )
            val history = ListenTogetherPlayerBridge.shouldRecordLocalHistory(
                isListenTogetherItem = true,
                pauseListenHistory = false,
                listenTogetherHistoryEnabled = enabled,
            )
            assertEquals(upsert, history)
        }
    }
}
