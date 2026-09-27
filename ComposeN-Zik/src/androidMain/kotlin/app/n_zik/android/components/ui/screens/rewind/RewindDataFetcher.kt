package app.n_zik.android.components.ui.screens.rewind

import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Event
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.core.database.EventTable
import app.n_zik.android.core.database.ext.RewindYearMonthStat
import app.n_zik.android.core.database.ext.RewindYearStat
import app.n_zik.android.core.database.ext.SongListeningStat
import app.n_zik.android.utils.coroutines.NzikDispatchers
import it.fast4x.innertube.YtMusic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.DayOfWeek
import java.time.Instant
import java.time.Month
import java.time.Year
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Data classes for rewind stats
data class TopSong(
    val song: Song,
    val minutes: Long,
    val playCount: Int
)

data class TopArtist(
    val artist: Artist,
    val minutes: Long,
    val songCount: Int
)

data class TopAlbum(
    val album: Album,
    val minutes: Long,
    val songCount: Int
)

data class TopPlaylist(
    val playlist: PlaylistPreview,
    val minutes: Long,
    val songCount: Int
)

data class MonthlyStat(
    val month: String,
    val minutes: Long,
    val plays: Int
)

data class CalendarDayStat(
    val day: Int,
    val minutes: Long,
    val plays: Int
)

data class DailyStat(
    val dayOfWeek: String,
    val minutes: Long,
    val plays: Int
)

data class HourlyStat(
    val hour: String,
    val minutes: Long,
    val plays: Int
)

data class ListeningStats(
    val totalPlays: Int,
    val totalMinutes: Long,
    val mostActiveDay: DailyStat?,
    val mostActiveHour: HourlyStat?,
    val mostActiveMonth: MonthlyStat?,
    val averageDailyMinutes: Double,
    val firstPlayDate: String?,
    val lastPlayDate: String?
)

data class RewindData(
    // Top items with minutes
    val topSongs: List<TopSong>,
    val topArtists: List<TopArtist>,
    val topAlbums: List<TopAlbum>,
    val topPlaylists: List<TopPlaylist>,

    // Listening stats
    val stats: ListeningStats,

    // Monthly breakdown
    val monthlyStats: List<MonthlyStat>,

    // Daily breakdown
    val dailyStats: List<DailyStat>,

    // Hourly breakdown
    val hourlyStats: List<HourlyStat>,

    // Counts
    val totalUniqueSongs: Int,
    val totalUniqueArtists: Int,
    val totalUniqueAlbums: Int,
    val totalUniquePlaylists: Int,

    // Period info
    val period: RewindPeriod,
    val daysWithMusic: Int,

    // Period label for the kickers: "2026" for the annual deck, "JANV. 2026" for a month
    val periodLabel: String,
    // Calendar days in the period: drives the listening-days ratio and the average
    val daysInPeriod: Int,
    // One entry per calendar day of the month (month deck only): the day-by-day chart
    val calendarDayStats: List<CalendarDayStat> = emptyList()
)

/**
 * The most-played items of one rewind window (a month, a year or the all-time
 * period) for the home page collage: the top song, top artist and top album
 * artwork URLs, plus the top playlist itself (its cover is resolved by the UI).
 * A category is null when the window has no plays or the item has no artwork.
 */
data class TopArtworks(
    val song: String?,
    val artist: String?,
    val album: String?,
    val playlist: PlaylistPreview?
)

/**
 * One year of the rewind home page: totals plus the 12-month breakdown (months without events
 * stay at zero plays so the grid can grey them out) and each month's top items collage.
 */
data class RewindHomeYear(
    val year: Int,
    val minutes: Long,
    val plays: Int,
    val months: List<MonthlyStat>,
    // One collage set per calendar month (12, calendar order)
    val monthTopArtworks: List<TopArtworks>,
    // The year's own top items, shown in the year row
    val topArtworks: TopArtworks
)

/**
 * The all-time ("ALL TIME") row of the rewind home page: lifetime totals plus
 * the lifetime top items collage. Null when the history has no plays at all.
 */
data class RewindHomeGlobal(
    val minutes: Long,
    val plays: Int,
    val topArtworks: TopArtworks
)

/**
 * Everything the "My Rewind" home page needs: one row per year that has events
 * (newest first) plus the all-time row.
 */
data class RewindHomeData(
    val years: List<RewindHomeYear>,
    val global: RewindHomeGlobal?
)

/**
 * Loads Rewind data from the [EventTable] play journal. Ranking, discovery counts and
 * the year/month home totals come from windowed SQL; the month / day / hour breakdowns
 * aggregate the windowed event rows in memory — bounded by the period for year/month
 * decks, and by the entire play journal for the all-time deck (accepted trade-off: the
 * journal of a music app stays far below the OOM line, and all the work runs on the
 * DATA dispatcher so the UI thread never blocks — spec GH-275, patch "Data loading",
 * re-review: KDoc corrected to match the actual load path).
 *
 * Both entry points never throw for data errors: they log and return an empty
 * payload, so a screen can always render a readable empty state.
 */
internal class RewindDataFetcher(
    private val source: EventTable
) {

    /** Everything the deck needs for [period]. */
    suspend fun getRewindData(period: RewindPeriod): RewindData =
        withContext(NzikDispatchers.DATA) {
            try {
                loadRewindData(period)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("RewindData").e(e, "Failed to load rewind data for %s", period.fileNameToken())
                emptyRewindData(period)
            }
        }

    private suspend fun loadRewindData(period: RewindPeriod): RewindData {
        val (periodStart, periodEnd) = period.boundaries()

        // The single windowed scan that drives every in-memory breakdown:
        // monthly / daily / hourly, calendar days, days with music, first/last play.
        val events = source.eventsBetween(periodStart, periodEnd).first()

        val topSongs = rankTopSongs(
            source.findSongListeningStatsBetween(periodStart, periodEnd).first()
        )
        val topArtists = getTopArtists(periodStart, periodEnd)
        val topAlbums = getTopAlbums(periodStart, periodEnd)
        val topPlaylists = getTopPlaylists(periodStart, periodEnd)

        // Canonical distinct counts (windowed SQL, no in-memory dedup)
        val totalUniqueSongs = source.countDistinctSongsPlayedBetween(periodStart, periodEnd).first()
        val totalUniqueArtists = source.countDistinctArtistsBetween(periodStart, periodEnd).first()
        val totalUniqueAlbums = source.countDistinctAlbumsBetween(periodStart, periodEnd).first()
        val totalUniquePlaylists = source.countDistinctPlaylistsBetween(periodStart, periodEnd).first()

        val monthlyStats = getMonthlyStats(period, events)
        val dailyStats = getDailyStats(events)
        val hourlyStats = getHourlyStats(events)
        // The day-by-day chart only makes sense inside a single month
        val calendarDays = when (period) {
            is RewindPeriod.Month -> calendarDayStats(period.year, period.month, events)
            else -> emptyList()
        }

        val totalPlaytimeMs = events.sumOf { it.playTime.coerceAtLeast(0L) }

        // A global window has no fixed length: fall back to the data's actual span
        val daysInPeriod = period.daysInPeriod().takeIf { it > 0 } ?: allTimeDaysInPeriod(events)

        // Calculate listening stats
        val stats = calculateListeningStats(
            events,
            totalPlaytimeMs,
            monthlyStats,
            dailyStats,
            hourlyStats,
            daysInPeriod
        )

        // Count days with music
        val zone = ZoneId.systemDefault()
        val daysWithMusic = events
            .map { event ->
                Instant.ofEpochMilli(event.timestamp)
                    .atZone(zone)
                    .toLocalDate()
            }
            .distinct()
            .size

        return RewindData(
            topSongs = topSongs,
            topArtists = topArtists,
            topAlbums = topAlbums,
            topPlaylists = topPlaylists,
            stats = stats,
            monthlyStats = monthlyStats,
            dailyStats = dailyStats,
            hourlyStats = hourlyStats,
            totalUniqueSongs = totalUniqueSongs,
            totalUniqueArtists = totalUniqueArtists,
            totalUniqueAlbums = totalUniqueAlbums,
            totalUniquePlaylists = totalUniquePlaylists,
            period = period,
            daysWithMusic = daysWithMusic,
            periodLabel = period.label(),
            daysInPeriod = daysInPeriod,
            calendarDayStats = calendarDays
        )
    }

    private suspend fun getTopArtists(
        windowStart: Long,
        windowEnd: Long
    ): List<TopArtist> {
        val stats = source
            .findArtistListeningStatsBetween(windowStart, windowEnd, 10)
            .first()

        return coroutineScope {
            stats.map { stat ->
                async {
                    val artist = if (
                        stat.artist.thumbnailUrl.isNullOrBlank() &&
                        stat.artist.id.startsWith("UC")
                    ) {
                        val resolvedThumbnail = runCatching {
                            YtMusic.getArtistPage(stat.artist.id)
                                .getOrNull()
                                ?.artist
                                ?.thumbnail
                                ?.url
                        }.getOrNull()
                        stat.artist.copy(
                            thumbnailUrl = resolvedThumbnail ?: stat.artist.thumbnailUrl
                        )
                    } else {
                        stat.artist
                    }
                    TopArtist(
                        artist = artist,
                        minutes = stat.playTimeMs.coerceAtLeast(0L) / 60_000L,
                        songCount = stat.songCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    )
                }
            }.awaitAll()
        }
    }

    private suspend fun getTopAlbums(
        windowStart: Long,
        windowEnd: Long
    ): List<TopAlbum> = source
        .findAlbumListeningStatsBetween(windowStart, windowEnd, 10)
        .first()
        .map { stat ->
            TopAlbum(
                album = stat.album,
                minutes = stat.playTimeMs.coerceAtLeast(0L) / 60_000L,
                songCount = stat.songCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            )
        }

    private suspend fun getTopPlaylists(windowStart: Long, windowEnd: Long): List<TopPlaylist> =
        source
            .findPlaylistListeningStatsBetween(windowStart, windowEnd, 10)
            .first()
            .map { stat ->
                TopPlaylist(
                    playlist = stat.playlist,
                    minutes = stat.playTimeMs.coerceAtLeast(0L) / 60_000L,
                    songCount = stat.playlist.songCount
                )
            }

    private fun getMonthlyStats(period: RewindPeriod, events: List<Event>): List<MonthlyStat> {
        val zone = ZoneId.systemDefault()
        return when (period) {
            // Global: one row per year (ascending), the year itself as the label —
            // the deck renders it as a per-year chart on the years slide
            is RewindPeriod.Global -> events
                .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).year }
                .toList()
                .sortedBy { (year, _) -> year }
                .map { (year, yearEvents) ->
                    MonthlyStat(
                        month = year.toString(),
                        minutes = yearEvents.sumOf { it.playTime.coerceAtLeast(0L) } / 60_000L,
                        plays = yearEvents.size
                    )
                }
            else -> {
                val grouped = events.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).monthValue }
                (1..12).map { month ->
                    val monthEvents = grouped[month].orEmpty()
                    MonthlyStat(
                        month = Month.of(month).getDisplayName(
                            TextStyle.SHORT,
                            Locale.getDefault()
                        ),
                        minutes = monthEvents.sumOf { it.playTime.coerceAtLeast(0L) } / 60_000L,
                        plays = monthEvents.size
                    )
                }
            }
        }
    }

    private fun getDailyStats(events: List<Event>): List<DailyStat> {
        val zone = ZoneId.systemDefault()
        val grouped = events.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).dayOfWeek }
        return DayOfWeek.values().map { day ->
            val dayEvents = grouped[day].orEmpty()
            DailyStat(
                dayOfWeek = day.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                minutes = dayEvents.sumOf { it.playTime.coerceAtLeast(0L) } / 60_000L,
                plays = dayEvents.size
            )
        }
    }

    private fun getHourlyStats(events: List<Event>): List<HourlyStat> {
        val zone = ZoneId.systemDefault()
        val grouped = events.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).hour }
        return (0..23).map { hour ->
            val hourEvents = grouped[hour].orEmpty()
            HourlyStat(
                hour = String.format(Locale.ROOT, "%02d:00", hour),
                minutes = hourEvents.sumOf { it.playTime.coerceAtLeast(0L) } / 60_000L,
                plays = hourEvents.size
            )
        }
    }

    private fun calculateListeningStats(
        events: List<Event>,
        totalPlaytimeMs: Long,
        monthlyStats: List<MonthlyStat>,
        dailyStats: List<DailyStat>,
        hourlyStats: List<HourlyStat>,
        daysInPeriod: Int
    ): ListeningStats {
        val totalPlays = events.size
        val totalMinutes = totalPlaytimeMs / 60000

        // Get most active month
        val mostActiveMonth = monthlyStats.maxByOrNull { it.minutes }

        // Get most active day
        val mostActiveDay = dailyStats.maxByOrNull { it.minutes }

        // Get most active hour
        val mostActiveHour = hourlyStats.maxByOrNull { it.minutes }

        // Average daily minutes over the period (a year, a month, or the data's span for global)
        val averageDailyMinutes = if (daysInPeriod > 0) totalMinutes.toDouble() / daysInPeriod else 0.0

        // Get first and last play dates
        val firstPlayDate = events.minByOrNull { it.timestamp }?.timestamp
        val lastPlayDate = events.maxByOrNull { it.timestamp }?.timestamp

        return ListeningStats(
            totalPlays = totalPlays,
            totalMinutes = totalMinutes,
            mostActiveDay = mostActiveDay,
            mostActiveHour = mostActiveHour,
            mostActiveMonth = mostActiveMonth,
            averageDailyMinutes = averageDailyMinutes,
            firstPlayDate = firstPlayDate?.let { formatDate(it) },
            lastPlayDate = lastPlayDate?.let { formatDate(it) }
        )
    }

    private fun formatDate(timestamp: Long): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault()))
    }

    /**
     * Everything the "My Rewind" home page needs in a single pass, windowed SQL only:
     * one row per year that has events (newest first), each with its 12-month grid and
     * per-month tops, plus the all-time row. Same source as the deck fetcher, so the
     * home page and the deck cannot disagree.
     */
    suspend fun getRewindHomeData(): RewindHomeData =
        withContext(NzikDispatchers.DATA) {
            try {
                val yearRows = source.rewindYearTotals().first()
                val monthsByYear = source
                    .rewindYearMonthTotals()
                    .first()
                    .groupBy { it.year }

                val years = yearRows.map { row ->
                    val window = RewindPeriod.Year(row.year).boundaries()
                    val grid = monthGrid(row.year, monthsByYear[row.year].orEmpty())
                    RewindHomeYear(
                        year = row.year,
                        minutes = row.playTimeMs / 60_000L,
                        plays = row.plays,
                        months = grid,
                        monthTopArtworks = monthTopArtworks(row.year, grid),
                        topArtworks = windowTopArtworks(window)
                    )
                }

                RewindHomeData(years = years, global = buildHomeGlobal(yearRows))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("RewindData").e(e, "Failed to load rewind home data")
                RewindHomeData(years = emptyList(), global = null)
            }
        }

    /**
     * The 12-slot month grid of [year] (calendar order): months without plays stay
     * at zero so the grid can grey them out.
     */
    private fun monthGrid(year: Int, rows: List<RewindYearMonthStat>): List<MonthlyStat> {
        val byMonth = rows.associate { it.month to it }
        return (1..12).map { slot ->
            val row = byMonth[slot]
            MonthlyStat(
                month = Month.of(slot).getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                minutes = (row?.playTimeMs ?: 0L) / 60_000L,
                plays = row?.plays ?: 0
            )
        }
    }

    /**
     * Each month's collage set of [year]'s grid: windowed top queries only for the
     * months with plays (empty months stay all-null).
     */
    private suspend fun monthTopArtworks(year: Int, grid: List<MonthlyStat>): List<TopArtworks> =
        grid.mapIndexed { slot, stat ->
            if (stat.plays <= 0) {
                TopArtworks(null, null, null, null)
            } else {
                windowTopArtworks(RewindPeriod.Month(year, slot + 1).boundaries())
            }
        }

    /**
     * The most-played items of one window: the deck's top-song ranking for the song
     * plus the limit-1 listening stats queries for the other categories.
     */
    private suspend fun windowTopArtworks(window: Pair<Long, Long>): TopArtworks {
        val (start, end) = window
        return TopArtworks(
            song = rankTopSongs(
                source.findSongListeningStatsBetween(start, end).first()
            ).firstOrNull()?.song?.thumbnailUrl,
            artist = source
                .findArtistListeningStatsBetween(start, end, 1)
                .first()
                .firstOrNull()
                ?.artist
                ?.thumbnailUrl,
            album = source
                .findAlbumListeningStatsBetween(start, end, 1)
                .first()
                .firstOrNull()
                ?.album
                ?.thumbnailUrl,
            playlist = source
                .findPlaylistListeningStatsBetween(start, end, 1)
                .first()
                .firstOrNull()
                ?.playlist
        )
    }

    /**
     * The all-time home row from the year rows already fetched (minutes = sum of raw
     * ms / 60000, plays summed) plus the windowed tops over `[0, now]`. Null when no
     * year has data.
     */
    private suspend fun buildHomeGlobal(yearRows: List<RewindYearStat>): RewindHomeGlobal? {
        if (yearRows.isEmpty()) return null
        return RewindHomeGlobal(
            minutes = yearRows.sumOf { it.playTimeMs } / 60_000L,
            plays = yearRows.sumOf { it.plays },
            topArtworks = windowTopArtworks(RewindPeriod.Global.boundaries())
        )
    }
}

/**
 * Ranks songs by play count, then minutes listened, then song id. The single
 * shared comparator of the deck (top 10, top 5, deep cuts, finale) and the home
 * collages, so the home's #1 can never disagree with the deck's #1 on a tie
 * (spec GH-275, patch "Top-song tie-break"). No truncation: callers take what
 * they need.
 */
internal fun rankTopSongs(stats: List<SongListeningStat>): List<TopSong> =
    stats
        .map { stat ->
            TopSong(
                song = stat.song,
                minutes = stat.playTimeMs.coerceAtLeast(0L) / 60_000L,
                playCount = stat.playCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            )
        }
        .sortedWith(
            compareByDescending<TopSong> { it.playCount }
                .thenByDescending { it.minutes }
                .thenBy { it.song.id }
        )

/**
 * Calendar days spanned by an all-time event set (0 for empty): first play to
 * last play, inclusive. Drives the global period, whose fixed [RewindPeriod.daysInPeriod]
 * is 0.
 */
internal fun allTimeDaysInPeriod(events: List<Event>): Int {
    if (events.isEmpty()) return 0
    val first = events.minOf { it.timestamp }
    val last = events.maxOf { it.timestamp }
    return ((last - first) / 86_400_000L + 1).toInt()
}

/**
 * Empty [RewindData] for [period]: a readable empty state, never a blank screen
 * (spec GH-275, PERIODE_VIDE).
 */
internal fun emptyRewindData(period: RewindPeriod): RewindData = RewindData(
    topSongs = emptyList(),
    topArtists = emptyList(),
    topAlbums = emptyList(),
    topPlaylists = emptyList(),
    stats = ListeningStats(
        totalPlays = 0,
        totalMinutes = 0,
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
    totalUniqueSongs = 0,
    totalUniqueArtists = 0,
    totalUniqueAlbums = 0,
    totalUniquePlaylists = 0,
    period = period,
    daysWithMusic = 0,
    periodLabel = period.label(),
    daysInPeriod = period.daysInPeriod()
)

/**
 * One entry per calendar day of [month] of [year] (days without plays stay at zero), in day
 * order: the day-by-day chart of a monthly deck.
 */
internal fun calendarDayStats(
    year: Int,
    month: Int,
    events: List<Event>,
    zone: ZoneId = ZoneId.systemDefault()
): List<CalendarDayStat> {
    val grouped = events.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).dayOfMonth }
    return (1..Year.of(year).atMonth(month).lengthOfMonth()).map { day ->
        val dayEvents = grouped[day].orEmpty()
        CalendarDayStat(
            day = day,
            minutes = dayEvents.sumOf { it.playTime.coerceAtLeast(0L) } / 60_000L,
            plays = dayEvents.size
        )
    }
}
