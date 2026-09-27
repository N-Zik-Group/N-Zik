// Ported from Metrolist (GPL-3.0, https://github.com/MetrolistGroup/Metrolist):
// app/src/test/kotlin/com/metrolist/music/listentogether/PlaybackSyncTest.kt
// Adapted to JUnit 5 and the app.n_zik.android.listentogether package.
package app.n_zik.android.listentogether

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackSyncTest {
    @Test
    fun heartbeatDriftDoesNotInterruptActivePlayback() {
        listOf(0L, 50L, 750L, 1_999L, 2_000L).forEach { positionDifferenceMs ->
            assertFalse(shouldSeekDuringActivePlayback(positionDifferenceMs, playbackReady = true))
        }
        assertFalse(shouldSeekDuringActivePlayback(10_000L, playbackReady = false))
        assertTrue(shouldSeekDuringActivePlayback(2_001L, playbackReady = true))
    }
}
