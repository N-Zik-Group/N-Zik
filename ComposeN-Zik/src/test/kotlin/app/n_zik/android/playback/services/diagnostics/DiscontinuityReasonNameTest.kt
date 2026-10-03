package app.n_zik.android.playback.services.diagnostics

import androidx.media3.common.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Issue #881 (gh-881): mapping of the position-discontinuity reasons to their human-readable
 * names (spec S2) — media3 1.10.1 constants, verified on the AAR via javap.
 */
class DiscontinuityReasonNameTest {

    @Test
    fun `all known reasons map to their constant names`() {
        assertEquals("AUTO_TRANSITION", discontinuityReasonName(Player.DISCONTINUITY_REASON_AUTO_TRANSITION))
        assertEquals("SEEK", discontinuityReasonName(Player.DISCONTINUITY_REASON_SEEK))
        assertEquals("SEEK_ADJUSTMENT", discontinuityReasonName(Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT))
        assertEquals("SKIP", discontinuityReasonName(Player.DISCONTINUITY_REASON_SKIP))
        assertEquals("REMOVE", discontinuityReasonName(Player.DISCONTINUITY_REASON_REMOVE))
        assertEquals("INTERNAL", discontinuityReasonName(Player.DISCONTINUITY_REASON_INTERNAL))
        assertEquals("SILENCE_SKIP", discontinuityReasonName(Player.DISCONTINUITY_REASON_SILENCE_SKIP))
    }

    @Test
    fun `the field log reason 1 is the SEEK reason`() {
        // The legacy journal printed "reason 1" for the frozen-bar cycles — that IS a seek.
        assertEquals(1, Player.DISCONTINUITY_REASON_SEEK)
        assertEquals("SEEK", discontinuityReasonName(1))
    }

    @Test
    fun `unknown reasons are reported with their raw value`() {
        assertEquals("UNKNOWN(99)", discontinuityReasonName(99))
        assertEquals("UNKNOWN(-1)", discontinuityReasonName(-1))
    }
}
