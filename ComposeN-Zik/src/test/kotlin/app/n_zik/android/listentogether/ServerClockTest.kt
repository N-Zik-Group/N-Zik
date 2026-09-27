// Ported from Metrolist (GPL-3.0, https://github.com/MetrolistGroup/Metrolist):
// app/src/test/kotlin/com/metrolist/music/listentogether/ServerClockTest.kt
// Adapted to JUnit 5 and the app.n_zik.android.listentogether package.
package app.n_zik.android.listentogether

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerClockTest {
    @Test
    fun `maps server wall time onto local monotonic time`() {
        var elapsedRealtime = 1_000L
        val clock = ServerClock { elapsedRealtime }

        assertNull(clock.now())
        elapsedRealtime = 1_120L
        assertTrue(
            clock.recordPong(
                clientTime = 1_000L,
                serverReceiveTime = 10_000L,
                serverSendTime = 10_010L,
            ),
        )

        assertEquals(10_065L, clock.now())
        assertEquals(565L, clock.positionAt(500L, 10_000L, isPlaying = true))

        elapsedRealtime += 100L
        assertEquals(10_165L, clock.now())
        assertEquals(665L, clock.positionAt(500L, 10_000L, isPlaying = true))
        assertEquals(500L, clock.positionAt(500L, 10_000L, isPlaying = false))
    }

    @Test
    fun `rejects invalid and stale samples`() {
        var elapsedRealtime = 100_000L
        val clock = ServerClock { elapsedRealtime }

        assertFalse(clock.recordPong(0L, 1_000L, 1_001L))
        assertFalse(clock.recordPong(100_001L, 1_000L, 1_001L))
        assertFalse(clock.recordPong(100_000L, 1_001L, 1_000L))
        elapsedRealtime = 200_001L
        assertFalse(clock.recordPong(100_000L, 1_000L, 1_001L))
        assertNull(clock.now())
    }

    @Test
    fun `reset removes the server clock mapping`() {
        var elapsedRealtime = 1_100L
        val clock = ServerClock { elapsedRealtime }
        clock.recordPong(1_000L, 10_000L, 10_000L)

        clock.reset()
        elapsedRealtime += 100L

        assertNull(clock.now())
        assertEquals(500L, clock.positionAt(500L, 10_000L, isPlaying = true))
    }
}
