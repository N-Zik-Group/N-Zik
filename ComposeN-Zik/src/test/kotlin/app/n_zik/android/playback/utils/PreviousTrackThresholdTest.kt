package app.n_zik.android.playback.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PreviousTrackThresholdTest {
    @Test
    fun `blank and zero both select the previous item while invalid values are rejected`() {
        assertEquals(0, parsePreviousTrackThreshold(""))
        assertEquals(0, parsePreviousTrackThreshold("000"))
        assertEquals(3, parsePreviousTrackThreshold(" 003 "))
        assertNull(parsePreviousTrackThreshold("-1"))
        assertNull(parsePreviousTrackThreshold("3 seconds"))
        assertNull(parsePreviousTrackThreshold("9999999999"))
    }

    @Test
    fun `restart threshold uses milliseconds without integer overflow`() {
        val threshold = requireNotNull(parsePreviousTrackThreshold("2147483647"))
        assertFalse(shouldRestartTrackOnPrevious(true, 3_000L, threshold))
        assertTrue(shouldRestartTrackOnPrevious(true, 2_147_483_647_001L, threshold))
        assertFalse(shouldRestartTrackOnPrevious(true, 3_000L, 3))
        assertTrue(shouldRestartTrackOnPrevious(true, 3_001L, 3))
        assertFalse(shouldRestartTrackOnPrevious(true, 3_001L, 0))
        assertTrue(shouldRestartTrackOnPrevious(false, 0L, 0))
    }
}
