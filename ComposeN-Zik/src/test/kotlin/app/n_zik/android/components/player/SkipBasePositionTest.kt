package app.n_zik.android.components.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Issue #881 (gh-881), Phase 3.1: base position of the skip buttons, read at tap time.
 */
class SkipBasePositionTest {

    @Test
    fun `uses the live player position when nothing is held`() {
        // Field log 2026-10-04: the composed position froze at 53 795 ms (previous track)
        // while the player played on — the base must come from the player, not the UI.
        assertEquals(12_000L, skipBasePosition(null, null, 0, livePlayerPositionMs = 12_000))
    }

    @Test
    fun `an in-progress drag wins over everything`() {
        assertEquals(70_000L, skipBasePosition(70_000, 90_000, 100, 10_000))
    }

    @Test
    fun `consecutive taps chain from a target the player has not committed yet`() {
        // +5 s tapped at 60 s → target 65 s; the player still reports 60.2 s 150 ms later.
        assertEquals(65_000L, skipBasePosition(null, 65_000, 150, 60_200))
    }

    @Test
    fun `a converged target falls back to the live position`() {
        assertEquals(65_300L, skipBasePosition(null, 65_000, 800, 65_300))
    }

    @Test
    fun `a stale target past the safety timeout falls back to the live position`() {
        // A target held forever (release effect never ran on a frozen UI) must not keep
        // being the base: the 2026-10-04 symptom of every tap landing on the same value.
        assertEquals(
            140_000L,
            skipBasePosition(null, 58_795, PENDING_SEEK_RELEASE_TIMEOUT_MS, 140_000),
        )
    }
}
