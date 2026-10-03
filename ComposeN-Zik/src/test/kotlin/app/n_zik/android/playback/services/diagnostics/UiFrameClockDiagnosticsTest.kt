package app.n_zik.android.playback.services.diagnostics

import androidx.lifecycle.Lifecycle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Issue #881 (gh-881), Phase 3.1: the first-frame probe runs only on the events after which
 * Compose's frame clock must be running again.
 */
class UiFrameClockDiagnosticsTest {

    @Test
    fun `probes the first frame after ON_START and ON_RESUME only`() {
        val probed = Lifecycle.Event.entries.filter(::shouldProbeFirstFrame)

        assertEquals(listOf(Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME), probed)
    }
}
