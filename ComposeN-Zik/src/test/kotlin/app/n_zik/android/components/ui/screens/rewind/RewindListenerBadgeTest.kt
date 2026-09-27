package app.n_zik.android.components.ui.screens.rewind

import app.n_zik.android.components.ui.screens.rewind.slides.calculateListenerBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for [calculateListenerBadge]: the NZik listening index is calibrated so the
 * annual reference (~45k minutes / 14k plays / 201 days / 5.6k unique songs) lands around
 * index 70 on a full year, and the four annual thresholds are scaled by the period's length
 * so a month is judged against a month's worth of the reference (spec GH-275, patch
 * "Listener badge not scaled"). A zero-day period (empty all-time data) scores 0 without a
 * 0/0 NaN.
 */
class RewindListenerBadgeTest {

    private fun data(
        period: RewindPeriod,
        totalMinutes: Long,
        totalPlays: Int,
        daysWithMusic: Int,
        totalUniqueSongs: Int
    ): RewindData = RewindData(
        topSongs = emptyList(),
        topArtists = emptyList(),
        topAlbums = emptyList(),
        topPlaylists = emptyList(),
        stats = ListeningStats(
            totalPlays = totalPlays,
            totalMinutes = totalMinutes,
            mostActiveDay = null,
            mostActiveHour = null,
            mostActiveMonth = null,
            averageDailyMinutes = 0.0,
            firstPlayDate = null,
            lastPlayDate = null
        ),
        monthlyStats = emptyList(),
        dailyStats = emptyList(),
        hourlyStats = emptyList(),
        totalUniqueSongs = totalUniqueSongs,
        totalUniqueArtists = 0,
        totalUniqueAlbums = 0,
        totalUniquePlaylists = 0,
        period = period,
        daysWithMusic = daysWithMusic,
        periodLabel = period.label(),
        daysInPeriod = period.daysInPeriod()
    )

    @Test
    fun annualReferenceScoresIndexSeventyTierFour() {
        val badge = calculateListenerBadge(data(RewindPeriod.Year(2026), 45_000L, 14_000, 201, 5_612))
        assertEquals(70, badge.index)
        assertEquals(4, badge.tier)
    }

    @Test
    fun sameRawDataInAShortMonthScoresTheTopTier() {
        // The reference raws over a 30-day month: every ratio saturates at its cap, so the
        // index lands at the top tier — the thresholds are scaled, not absolute
        val badge = calculateListenerBadge(data(RewindPeriod.Month(2026, 4), 45_000L, 14_000, 201, 5_612))
        assertEquals(159, badge.index)
        assertEquals(8, badge.tier)
    }

    @Test
    fun emptyGlobalScoresZeroWithoutNaN() {
        val badge = calculateListenerBadge(data(RewindPeriod.Global, 0L, 0, 0, 0))
        assertEquals(0, badge.index)
        assertEquals(0, badge.tier)
    }

    @Test
    fun lightListeningStaysInTheLowTiers() {
        val badge = calculateListenerBadge(data(RewindPeriod.Year(2026), 1_000L, 300, 20, 150))
        assertTrue("expected the low end of the scale, got ${badge.index}", badge.index < 15)
        assertEquals(0, badge.tier)
    }
}
