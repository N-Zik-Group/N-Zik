package app.n_zik.android.playback.services

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The lifted RESOLUTION line level decision (issue #881, spec S4): warn strictly above
 * [RESOLUTION_SLOW_THRESHOLD_MS], debug otherwise — the slow resolution after the screen
 * off→on cycle is the prime suspect and must stand out in the exported log. Plain JVM: the
 * threshold decision carries no Android pieces.
 */
class StreamResolverResolutionOutcomeTest {

    @Test
    fun `a fast resolution stays at debug level`() {
        assertEquals(Log.DEBUG, resolutionOutcomeLevel(120))
    }

    @Test
    fun `a resolution exactly at the threshold stays at debug level`() {
        assertEquals(Log.DEBUG, resolutionOutcomeLevel(RESOLUTION_SLOW_THRESHOLD_MS))
    }

    @Test
    fun `a slow resolution escalates to warn level`() {
        assertEquals(Log.WARN, resolutionOutcomeLevel(RESOLUTION_SLOW_THRESHOLD_MS + 1))
    }

    @Test
    fun `the slow threshold is the spec 10 seconds`() {
        assertEquals(10_000L, RESOLUTION_SLOW_THRESHOLD_MS)
    }
}
