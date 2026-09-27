package app.n_zik.android.utils.debug

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CrashLogAutoWipeTest {

    @TempDir
    lateinit var logsDir: File

    @Test
    fun `crashLogFile points to the crash log inside the logs dir`() {
        assertEquals(File(logsDir, "N-Zik_crash_log.txt"), crashLogFile(logsDir))
    }

    @Test
    fun `a new crash since the previous boot restarts the streak without wiping`() {
        val decision = crashLogWipeDecision(
            currentCrashCount = 2,
            lastSeenCrashCount = 1,
            cleanBootStreak = 1,
        )

        assertFalse(decision.wipe)
        assertEquals(0, decision.cleanBootStreak)
        assertEquals(2, decision.lastSeenCrashCount)
    }

    @Test
    fun `the first clean boot after a crash does not wipe`() {
        val decision = crashLogWipeDecision(
            currentCrashCount = 1,
            lastSeenCrashCount = 1,
            cleanBootStreak = 0,
        )

        assertFalse(decision.wipe)
        assertEquals(1, decision.cleanBootStreak)
        assertEquals(1, decision.lastSeenCrashCount)
    }

    @Test
    fun `the second consecutive clean boot wipes and resets the state`() {
        val decision = crashLogWipeDecision(
            currentCrashCount = 1,
            lastSeenCrashCount = 1,
            cleanBootStreak = 1,
        )

        assertTrue(decision.wipe)
        assertEquals(0, decision.cleanBootStreak)
        assertEquals(0, decision.lastSeenCrashCount)
    }

    @Test
    fun `a streak below the threshold keeps growing without wiping`() {
        val decision = crashLogWipeDecision(
            currentCrashCount = 3,
            lastSeenCrashCount = 3,
            cleanBootStreak = 0,
        )

        assertFalse(decision.wipe)
        assertEquals(1, decision.cleanBootStreak)
        assertEquals(3, decision.lastSeenCrashCount)
    }

    @Test
    fun `no crash ever - the streak still counts boots (wiping an absent file is a no-op)`() {
        val first = crashLogWipeDecision(0, 0, 0)
        assertFalse(first.wipe)
        assertEquals(1, first.cleanBootStreak)

        val second = crashLogWipeDecision(0, 0, 1)
        assertTrue(second.wipe)
        assertEquals(0, second.lastSeenCrashCount)
    }

    @Test
    fun `a manually cleared log (count back to zero) never counts as a new crash`() {
        val decision = crashLogWipeDecision(
            currentCrashCount = 0,
            lastSeenCrashCount = 1,
            cleanBootStreak = 0,
        )

        assertFalse(decision.wipe)
        assertEquals(1, decision.cleanBootStreak)
        assertEquals(0, decision.lastSeenCrashCount)
    }

    @Test
    fun `the threshold is configurable`() {
        val decision = crashLogWipeDecision(
            currentCrashCount = 1,
            lastSeenCrashCount = 1,
            cleanBootStreak = 4,
            requiredCleanBoots = 5,
        )

        assertTrue(decision.wipe)
    }
}
