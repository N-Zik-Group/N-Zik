package app.n_zik.android.components.player

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Issue #881 (gh-881), Phase 3 Fix E: release decision for the held (optimistic) seek position.
 */
class PendingSeekScrubTest {

    @Test
    fun `holds while the player position is far from the target`() {
        // Backward tap: player still at 120 s, target 90 s, just 200 ms held.
        assertFalse(shouldReleasePendingSeekPosition(90_000, 120_000, 200))
        // Forward tap beyond the buffered window: player at 30 s, target 180 s.
        assertFalse(shouldReleasePendingSeekPosition(180_000, 30_000, 3_000))
    }

    @Test
    fun `releases once the player converges within the tolerance`() {
        // Committed at the nearest sample (480 ms off the tapped target) — inside the 500 ms
        // tolerance.
        assertTrue(shouldReleasePendingSeekPosition(90_000, 89_520, 400))
        assertTrue(shouldReleasePendingSeekPosition(90_000, 90_400, 900))
        // Exact match.
        assertTrue(shouldReleasePendingSeekPosition(180_000, 180_000, 100))
    }

    @Test
    fun `keeps holding right at the tolerance boundary plus one ms`() {
        assertFalse(
            shouldReleasePendingSeekPosition(
                90_000,
                90_000 + PENDING_SEEK_SETTLE_TOLERANCE_MS + 1,
                100,
            ),
        )
    }

    @Test
    fun `releases on the safety timeout even if the player never commits`() {
        assertTrue(
            shouldReleasePendingSeekPosition(180_000, 30_000, PENDING_SEEK_RELEASE_TIMEOUT_MS),
        )
        assertTrue(
            shouldReleasePendingSeekPosition(180_000, 30_000, PENDING_SEEK_RELEASE_TIMEOUT_MS + 1),
        )
        // Just before the timeout the decision still follows the position.
        assertFalse(
            shouldReleasePendingSeekPosition(180_000, 30_000, PENDING_SEEK_RELEASE_TIMEOUT_MS - 1),
        )
    }

    @Test
    fun `a tap near the current position releases immediately`() {
        // Tapping within the tolerance of where the player already is is indistinguishable —
        // no point holding the bar.
        assertTrue(shouldReleasePendingSeekPosition(100_300, 100_000, 0))
    }
}
