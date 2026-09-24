package app.n_zik.android.core.rewind

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure tests locking the [RewindPlaylists] naming/visibility contract (spec "Retrait du
 * mécanisme legacy « monthly playlists » + catégorie « Rewind playlists »"): the prefix
 * predicates (valid / unknown / case) and the Month/Year/All row behavior across the 4
 * creation-toggle combinations (row present/absent, content per filter — TOGGLE_CHIP).
 */
class RewindPlaylistsTest {

    private val monthlyName = "rewind-monthly:202508"
    private val yearlyName = "rewind-yearly:2025"

    @Test
    fun `isMonthly matches only the monthly prefix`() {
        assertTrue(RewindPlaylists.isMonthly(monthlyName))
        assertFalse(RewindPlaylists.isMonthly(yearlyName))
        assertFalse(RewindPlaylists.isMonthly("my playlist"))
        assertFalse(RewindPlaylists.isMonthly("monthly:202508"))
        assertFalse(RewindPlaylists.isMonthly("rewind-monthly"))
    }

    @Test
    fun `isMonthly is case-insensitive`() {
        assertTrue(RewindPlaylists.isMonthly("REWIND-MONTHLY:202508"))
        assertTrue(RewindPlaylists.isMonthly("Rewind-Monthly:202508"))
    }

    @Test
    fun `isYearly matches only the yearly prefix`() {
        assertTrue(RewindPlaylists.isYearly(yearlyName))
        assertFalse(RewindPlaylists.isYearly(monthlyName))
        assertFalse(RewindPlaylists.isYearly("2025"))
        assertFalse(RewindPlaylists.isYearly("rewind-yearly"))
    }

    @Test
    fun `isYearly is case-insensitive`() {
        assertTrue(RewindPlaylists.isYearly("REWIND-YEARLY:2025"))
        assertTrue(RewindPlaylists.isYearly("Rewind-Yearly:2025"))
    }

    @Test
    fun `isRewind covers both generated types and nothing else`() {
        assertTrue(RewindPlaylists.isRewind(monthlyName))
        assertTrue(RewindPlaylists.isRewind(yearlyName))
        assertFalse(RewindPlaylists.isRewind("my playlist"))
        assertFalse(RewindPlaylists.isRewind("monthly:202508"))
    }

    @Test
    fun `the row is visible only when both creation toggles are on`() {
        assertTrue(RewindPlaylists.rowVisible(true, true))
        assertFalse(RewindPlaylists.rowVisible(true, false))
        assertFalse(RewindPlaylists.rowVisible(false, true))
        assertFalse(RewindPlaylists.rowVisible(false, false))
    }

    @Test
    fun `both toggles on filters by the selected chip`() {
        // Month -> monthly only
        assertTrue(RewindPlaylists.isShown(monthlyName, true, true, RewindPlaylists.Filter.Month))
        assertFalse(RewindPlaylists.isShown(yearlyName, true, true, RewindPlaylists.Filter.Month))

        // Year -> yearly only
        assertFalse(RewindPlaylists.isShown(monthlyName, true, true, RewindPlaylists.Filter.Year))
        assertTrue(RewindPlaylists.isShown(yearlyName, true, true, RewindPlaylists.Filter.Year))

        // All -> both types
        assertTrue(RewindPlaylists.isShown(monthlyName, true, true, RewindPlaylists.Filter.All))
        assertTrue(RewindPlaylists.isShown(yearlyName, true, true, RewindPlaylists.Filter.All))

        // Other playlists never appear in the category
        assertFalse(RewindPlaylists.isShown("my playlist", true, true, RewindPlaylists.Filter.All))
    }

    @Test
    fun `only monthly on shows the monthly type directly`() {
        assertTrue(RewindPlaylists.isShown(monthlyName, true, false, RewindPlaylists.Filter.Month))
        assertFalse(RewindPlaylists.isShown(yearlyName, true, false, RewindPlaylists.Filter.Month))
        // The selected chip is irrelevant when the row is absent
        assertFalse(RewindPlaylists.isShown(yearlyName, true, false, RewindPlaylists.Filter.All))
    }

    @Test
    fun `only yearly on shows the yearly type directly`() {
        assertTrue(RewindPlaylists.isShown(yearlyName, false, true, RewindPlaylists.Filter.Year))
        assertFalse(RewindPlaylists.isShown(monthlyName, false, true, RewindPlaylists.Filter.Year))
        assertFalse(RewindPlaylists.isShown(monthlyName, false, true, RewindPlaylists.Filter.All))
    }

    @Test
    fun `both toggles off shows nothing`() {
        assertFalse(RewindPlaylists.isShown(monthlyName, false, false, RewindPlaylists.Filter.Month))
        assertFalse(RewindPlaylists.isShown(yearlyName, false, false, RewindPlaylists.Filter.Year))
        assertFalse(RewindPlaylists.isShown(monthlyName, false, false, RewindPlaylists.Filter.All))
    }
}
