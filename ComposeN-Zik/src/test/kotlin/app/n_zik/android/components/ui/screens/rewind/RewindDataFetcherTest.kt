package app.n_zik.android.components.ui.screens.rewind

import app.it.fast4x.rimusic.models.Event
import app.n_zik.android.core.database.EventTable
import app.n_zik.android.core.database.ext.RewindYearMonthStat
import app.n_zik.android.core.database.ext.RewindYearStat
import app.n_zik.android.core.database.ext.SongListeningStat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Tests the in-memory aggregation layer of [RewindDataFetcher] — the part the ViewModel
 * tests cannot reach because they mock the fetcher: monthly zero-fill, global per-year
 * rows, most-active selections, the all-time span fallback and the home month grid
 * (spec GH-275, re-review: aggregation untested).
 *
 * The [EventTable] source is a mock returning fixed windowed rows, so every assertion
 * pins the computation, not the SQL. Event timestamps use the 15th of a month at
 * midday UTC so they land in the same calendar slot in any user timezone.
 */
class RewindDataFetcherTest {

    private val source = mockk<EventTable>()
    private val fetcher = RewindDataFetcher(source)

    /** Stub every windowed query the deck path issues, returning the given raw events. */
    private fun stubDeckSource(events: List<Event>) {
        coEvery { source.eventsBetween(any(), any()) } returns flowOf(events)
        coEvery { source.findSongListeningStatsBetween(any(), any(), any()) } returns flowOf(emptyList<SongListeningStat>())
        coEvery { source.findArtistListeningStatsBetween(any(), any(), any()) } returns flowOf(emptyList())
        coEvery { source.findAlbumListeningStatsBetween(any(), any(), any()) } returns flowOf(emptyList())
        coEvery { source.findPlaylistListeningStatsBetween(any(), any(), any()) } returns flowOf(emptyList())
        coEvery { source.countDistinctSongsPlayedBetween(any(), any()) } returns flowOf(0)
        coEvery { source.countDistinctArtistsBetween(any(), any()) } returns flowOf(0)
        coEvery { source.countDistinctAlbumsBetween(any(), any()) } returns flowOf(0)
        coEvery { source.countDistinctPlaylistsBetween(any(), any()) } returns flowOf(0)
    }

    private fun eventOn(year: Int, month: Int, day: Int, hour: Int, playTimeMs: Long): Event {
        val timestamp = ZonedDateTime.of(year, month, day, hour, 0, 0, 0, ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        return Event(songId = "s_${year}_${month}_${day}_${hour}", timestamp = timestamp, playTime = playTimeMs)
    }

    @Test
    fun yearPeriodMonthlyStatsZeroFillAllTwelveSlots() = runBlocking {
        stubDeckSource(
            listOf(
                eventOn(2025, 3, 15, 12, 120_000L),
                eventOn(2025, 3, 16, 12, 60_000L),
                eventOn(2025, 7, 15, 12, 60_000L)
            )
        )

        val data = fetcher.getRewindData(RewindPeriod.Year(2025))

        assertEquals(12, data.monthlyStats.size)
        val march = data.monthlyStats[2]
        assertEquals(2, march.plays)
        assertEquals(3L, march.minutes) // 180s
        assertEquals("January must stay at zero", 0, data.monthlyStats[0].plays)
        assertEquals(1, data.monthlyStats[6].plays) // July
    }

    @Test
    fun globalPeriodMonthlyStatsGroupPerYearAscending() = runBlocking {
        stubDeckSource(
            listOf(
                eventOn(2024, 6, 15, 12, 60_000L),
                eventOn(2024, 12, 15, 12, 60_000L),
                eventOn(2026, 1, 15, 12, 180_000L)
            )
        )

        val data = fetcher.getRewindData(RewindPeriod.Global)

        assertEquals(2, data.monthlyStats.size)
        assertEquals("2024", data.monthlyStats[0].month)
        assertEquals(2, data.monthlyStats[0].plays)
        assertEquals("2026", data.monthlyStats[1].month)
        assertEquals(3L, data.monthlyStats[1].minutes)
    }

    @Test
    fun globalPeriodStatsUseTheDataSpanForAveragesAndPickMostActiveByMinutes() = runBlocking {
        stubDeckSource(
            listOf(
                eventOn(2020, 6, 15, 12, 120_000L),
                eventOn(2020, 6, 15, 14, 60_000L),
                eventOn(2020, 6, 17, 12, 60_000L)
            )
        )

        val data = fetcher.getRewindData(RewindPeriod.Global)

        assertEquals(2, data.daysWithMusic) // two distinct calendar days
        assertEquals(3, data.daysInPeriod) // first play to last play, inclusive
        val stats = data.stats
        assertEquals(3, stats.totalPlays)
        assertEquals(4L, stats.totalMinutes) // 240s
        assertEquals(4.0 / 3.0, stats.averageDailyMinutes, 0.0001)
        assertEquals(3L, stats.mostActiveHour?.minutes) // the hour holding 180s
        assertEquals(3L, stats.mostActiveDay?.minutes)
        assertEquals(4L, stats.mostActiveMonth?.minutes)
        assertTrue("first/last play dates must be filled", stats.firstPlayDate != null && stats.lastPlayDate != null)
    }

    @Test
    fun emptyWindowReturnsAReadableEmptyPayload() = runBlocking {
        stubDeckSource(emptyList())

        val data = fetcher.getRewindData(RewindPeriod.Global)

        assertTrue(data.topSongs.isEmpty())
        assertTrue(data.monthlyStats.isEmpty())
        assertEquals(0, data.stats.totalPlays)
        assertEquals(0.0, data.stats.averageDailyMinutes, 0.0001)
        assertEquals(0, data.daysInPeriod)
        assertEquals(0, data.daysWithMusic)
    }

    @Test
    fun homeDataZeroFillsMonthGridAndSumsTheGlobalRow() = runBlocking {
        coEvery { source.rewindYearTotals() } returns flowOf(
            listOf(RewindYearStat(year = 2025, plays = 3, playTimeMs = 300_000L))
        )
        coEvery { source.rewindYearMonthTotals() } returns flowOf(
            listOf(RewindYearMonthStat(year = 2025, month = 6, plays = 2, playTimeMs = 200_000L))
        )
        coEvery { source.findSongListeningStatsBetween(any(), any(), any()) } returns flowOf(emptyList<SongListeningStat>())
        coEvery { source.findArtistListeningStatsBetween(any(), any(), any()) } returns flowOf(emptyList())
        coEvery { source.findAlbumListeningStatsBetween(any(), any(), any()) } returns flowOf(emptyList())
        coEvery { source.findPlaylistListeningStatsBetween(any(), any(), any()) } returns flowOf(emptyList())

        val home = fetcher.getRewindHomeData()

        assertEquals(1, home.years.size)
        val year = home.years.first()
        assertEquals(2025, year.year)
        assertEquals(5L, year.minutes) // 300s
        assertEquals(3, year.plays)
        assertEquals(12, year.months.size)
        assertEquals("June must carry the year-month totals", 2, year.months[5].plays)
        assertEquals(3L, year.months[5].minutes) // 200s
        assertEquals("January must stay at zero", 0, year.months[0].plays)
        assertEquals("the global row sums the year rows", 5L, home.global?.minutes)
        assertEquals(3, home.global?.plays)
    }
}
