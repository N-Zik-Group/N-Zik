package app.n_zik.android.listentogether

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRecordingDecisionTest {

    @Test
    fun `local track is recorded by default`() {
        assertTrue(
            ListenTogetherPlayerBridge.shouldRecordLocalHistory(
                isListenTogetherItem = false,
                pauseListenHistory = false,
                listenTogetherHistoryEnabled = false,
            )
        )
    }

    @Test
    fun `local track is not recorded when history is paused`() {
        assertFalse(
            ListenTogetherPlayerBridge.shouldRecordLocalHistory(
                isListenTogetherItem = false,
                pauseListenHistory = true,
                listenTogetherHistoryEnabled = true,
            )
        )
    }

    @Test
    fun `listen together track is skipped when the option is off`() {
        assertFalse(
            ListenTogetherPlayerBridge.shouldRecordLocalHistory(
                isListenTogetherItem = true,
                pauseListenHistory = false,
                listenTogetherHistoryEnabled = false,
            )
        )
    }

    @Test
    fun `listen together track is recorded when the option is on`() {
        assertTrue(
            ListenTogetherPlayerBridge.shouldRecordLocalHistory(
                isListenTogetherItem = true,
                pauseListenHistory = false,
                listenTogetherHistoryEnabled = true,
            )
        )
    }

    @Test
    fun `pause switch wins over the listen together option`() {
        assertFalse(
            ListenTogetherPlayerBridge.shouldRecordLocalHistory(
                isListenTogetherItem = true,
                pauseListenHistory = true,
                listenTogetherHistoryEnabled = true,
            )
        )
    }
}
