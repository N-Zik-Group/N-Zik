package app.n_zik.android.updater.services

import app.n_zik.android.updater.models.CheckUpdateState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The updater settings gate (spec-lyrics-copy-loop-and-updater-miniplayer,
 * follow-up 2026-10-26): builds with auto-update keep the stored settings, builds
 * without auto-update (debug/foss) force auto-check off and beta off, so stale
 * preferences from an updater build cannot leak into a manual check or the UI.
 */
class EffectiveUpdaterSettingsTest {

    @Test
    fun `an updater build keeps the stored settings`() {
        val (state, beta) = Updater.effectiveUpdaterSettings(
            storedState = CheckUpdateState.Ask,
            storedBeta = true,
            autoUpdateEnabled = true
        )
        assertEquals(CheckUpdateState.Ask, state)
        assertTrue(beta)
    }

    @Test
    fun `an updater build keeps a stored disabled state`() {
        val (state, beta) = Updater.effectiveUpdaterSettings(
            storedState = CheckUpdateState.Disabled,
            storedBeta = false,
            autoUpdateEnabled = true
        )
        assertEquals(CheckUpdateState.Disabled, state)
        assertFalse(beta)
    }

    @Test
    fun `a build without auto-update forces auto-check and beta off`() {
        val (state, beta) = Updater.effectiveUpdaterSettings(
            storedState = CheckUpdateState.Enabled,
            storedBeta = true,
            autoUpdateEnabled = false
        )
        assertEquals(CheckUpdateState.Disabled, state)
        assertFalse(beta)
    }

    @Test
    fun `a build without auto-update forces off even for the ask state`() {
        val (state, beta) = Updater.effectiveUpdaterSettings(
            storedState = CheckUpdateState.Ask,
            storedBeta = true,
            autoUpdateEnabled = false
        )
        assertEquals(CheckUpdateState.Disabled, state)
        assertFalse(beta)
    }
}
