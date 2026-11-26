package app.n_zik.android.playback.services

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackBoundaryPauseTest {
    @Test
    fun `crossfade and guest lock take precedence over the local gap`() {
        assertEquals(5000L, trackBoundaryPauseMs(5000L, crossfadeEnabled = false, guestLocked = false))
        assertEquals(0L, trackBoundaryPauseMs(5000L, crossfadeEnabled = true, guestLocked = false))
        assertEquals(0L, trackBoundaryPauseMs(5000L, crossfadeEnabled = false, guestLocked = true))
    }

    @Test
    fun `resume runs once when the gap expires`() = runTest {
        val pause = TrackBoundaryPause(this)
        var resumes = 0
        pause.schedule(5000L) { resumes++ }

        advanceTimeBy(5000L)
        assertEquals(0, resumes)
        runCurrent()
        assertEquals(1, resumes)
        advanceTimeBy(5000L)
        assertEquals(1, resumes)
    }

    @Test
    fun `cancellation drops a pending resume and a new boundary replaces an old one`() = runTest {
        val pause = TrackBoundaryPause(this)
        val resumed = mutableListOf<String>()
        pause.schedule(5000L) { resumed.add("cancelled") }
        runCurrent()
        pause.cancel()
        pause.schedule(5000L) { resumed.add("replaced") }
        advanceTimeBy(2000L)
        pause.schedule(5000L) { resumed.add("current") }
        advanceTimeBy(5000L)
        runCurrent()
        assertEquals(listOf("current"), resumed)
    }
}
