package app.n_zik.android.components.ui.screens.rewind

import app.it.fast4x.rimusic.models.*
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.ext.EventWithSong
import it.fast4x.innertube.YtMusic
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.Year
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

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
    
    // Year info
    val year: Int,
    val daysWithMusic: Int,

    // Period label for the kickers: "2026" for the annual deck, "JANV. 2026" for a month
    val periodLabel: String,
    // Calendar days in the period: drives the listening-days ratio and the average
    val daysInPeriod: Int,
    // One entry per calendar day of the month (month deck only): the day-by-day chart
    val calendarDayStats: List<CalendarDayStat> = emptyList()
)

/**
 * The most-played items of one rewind period (a month, or the year itself) for the home page
 * collage: the top song, top artist and top album artwork URLs, plus the top playlist itself
 * (its cover is resolved by the UI). A category is null when the period has no plays or the
 * item has no artwork.
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

// Data fetcher class
object RewindDataFetcher {

    suspend fun latestAvailableYear(): Int {
        val latestTimestamp = Database.eventTable.latestTimestamp()
            ?: return LocalDate.now().year
        return Instant.ofEpochMilli(latestTimestamp)
            .atZone(ZoneId.systemDefault())
            .year
    }
    
    // Get rewind data for a specific year, or one month of that year (monthly deck)
    suspend fun getRewindData(year: Int, month: Int? = null): RewindData {
        return try {
            // Get period boundaries (the whole year, or the single month when [month] is set)
            val (periodStart, periodEnd) = rewindPeriodBoundaries(year, month)
            
            // Get all events for the period - use the available method from EventTable
            val allEvents = Database.eventTable.allWithSong(Int.MAX_VALUE).first()
            
            // Filter events for the specific period
            val periodEvents = allEvents
                .asSequence()
                .filter { eventWithSong ->
                    eventWithSong.event.timestamp in periodStart..periodEnd
                }
                .toList()
            
            if (periodEvents.isEmpty()) {
                return createEmptyData(year, month)
            }
            
            // Extract just the events
            val events = periodEvents.map { it.event }
            
            val topSongs = getTopSongs(periodEvents)
            val topArtists = getTopArtists(periodStart, periodEnd)
            val topAlbums = getTopAlbums(periodStart, periodEnd)
            val topPlaylists = getTopPlaylists(periodStart, periodEnd)
            
            // Get counts using actual database queries
            val totalUniqueSongs = Database.eventTable
                .findSongsMostPlayedBetween(periodStart, periodEnd, Int.MAX_VALUE)
                .first()
                .size
            
            val totalUniqueArtists = Database.eventTable
                .findArtistsMostPlayedBetween(periodStart, periodEnd, Int.MAX_VALUE)
                .first()
                .size
            
            val totalUniqueAlbums = Database.eventTable
                .findAlbumsMostPlayedBetween(periodStart, periodEnd, Int.MAX_VALUE)
                .first()
                .size
            
            val totalUniquePlaylists = Database.eventTable
                .findPlaylistMostPlayedBetweenAsPreview(periodStart, periodEnd, Int.MAX_VALUE)
                .first()
                .size
            
            val monthlyStats = getMonthlyStats(periodStart, periodEnd, events)
            val dailyStats = getDailyStats(periodStart, periodEnd, events)
            val hourlyStats = getHourlyStats(periodStart, periodEnd, events)
            // The day-by-day chart only makes sense inside a single month
            val calendarDays = if (month != null) calendarDayStats(year, month, events) else emptyList()
            
            val totalPlaytimeMs = events.sumOf { event -> event.playTime.coerceAtLeast(0L) }
            
            // Calculate listening stats
            val stats = calculateListeningStats(
                events, 
                totalPlaytimeMs,
                monthlyStats, 
                dailyStats, 
                hourlyStats, 
                rewindDaysInPeriod(year, month)
            )
            
            // Count days with music
            val daysWithMusic = events
                .map { event ->
                    Instant.ofEpochMilli(event.timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                }
                .distinct()
                .size
            
            RewindData(
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
                year = year,
                daysWithMusic = daysWithMusic,
                periodLabel = rewindPeriodLabel(year, month),
                daysInPeriod = rewindDaysInPeriod(year, month),
                calendarDayStats = calendarDays
            )
            
        } catch (e: Exception) {
            Timber.tag("RewindData").e(e, "Failed to load rewind data for %d", year)
            createEmptyData(year, month)
        }
    }
    
    private fun getTopSongs(yearlyEvents: List<EventWithSong>): List<TopSong> = yearlyEvents
        .groupBy { it.song.id }
        .map { (_, songEvents) ->
            val first = songEvents.first()
            TopSong(
                song = first.song,
                minutes = songEvents.sumOf { it.event.playTime.coerceAtLeast(0L) } / 60_000L,
                playCount = songEvents.size
            )
        }
        .sortedWith(compareByDescending<TopSong> { it.playCount }
            .thenByDescending { it.minutes }
            .thenBy { it.song.id })
        .take(10)

    private suspend fun getTopArtists(
        yearStart: Long,
        yearEnd: Long
    ): List<TopArtist> {
        val stats = Database.eventTable
            .findArtistListeningStatsBetween(yearStart, yearEnd, 10)
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
        yearStart: Long,
        yearEnd: Long
    ): List<TopAlbum> = Database.eventTable
        .findAlbumListeningStatsBetween(yearStart, yearEnd, 10)
        .first()
        .map { stat ->
            TopAlbum(
                album = stat.album,
                minutes = stat.playTimeMs.coerceAtLeast(0L) / 60_000L,
                songCount = stat.songCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            )
        }
    
    private suspend fun getTopPlaylists(yearStart: Long, yearEnd: Long): List<TopPlaylist> =
        Database.eventTable
            .findPlaylistListeningStatsBetween(yearStart, yearEnd, 10)
            .first()
            .map { stat ->
                TopPlaylist(
                    playlist = stat.playlist,
                    minutes = stat.playTimeMs.coerceAtLeast(0L) / 60_000L,
                    songCount = stat.playlist.songCount
                )
            }
    
    private suspend fun getMonthlyStats(
        yearStart: Long,
        yearEnd: Long,
        events: List<Event>
    ): List<MonthlyStat> {
        val zone = ZoneId.systemDefault()
        val grouped = events.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).monthValue }
        return (1..12).map { month ->
            val monthEvents = grouped[month].orEmpty()
            MonthlyStat(
                month = java.time.Month.of(month).getDisplayName(
                    java.time.format.TextStyle.SHORT,
                    Locale.getDefault()
                ),
                minutes = monthEvents.sumOf { it.playTime.coerceAtLeast(0L) } / 60_000L,
                plays = monthEvents.size
            )
        }
    }

    private fun getDailyStats(
        yearStart: Long,
        yearEnd: Long,
        events: List<Event>
    ): List<DailyStat> {
        val zone = ZoneId.systemDefault()
        val grouped = events.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).dayOfWeek }
        return java.time.DayOfWeek.values().map { day ->
            val dayEvents = grouped[day].orEmpty()
            DailyStat(
                dayOfWeek = day.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()),
                minutes = dayEvents.sumOf { it.playTime.coerceAtLeast(0L) } / 60_000L,
                plays = dayEvents.size
            )
        }
    }

    private fun getHourlyStats(
        yearStart: Long,
        yearEnd: Long,
        events: List<Event>
    ): List<HourlyStat> {
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
        
        // Average daily minutes over the period (a year, or a single month for the monthly deck)
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
     * Everything the rewind home page needs in a single pass: one summary per year that has
     * events (newest first), each with its 12-month breakdown. Same source as the deck fetcher
     * (all events with songs, grouped in memory) so the home page and the deck cannot disagree.
     */
    suspend fun getRewindHomeData(): List<RewindHomeYear> {
        return try {
            val zone = ZoneId.systemDefault()
            val events = Database.eventTable
                .allWithSong(Int.MAX_VALUE)
                .first()
            events
                .groupBy { Instant.ofEpochMilli(it.event.timestamp).atZone(zone).year }
                .toList()
                .sortedByDescending { (year, _) -> year }
                .map { (year, yearEvents) ->
                    val (start, end) = rewindPeriodBoundaries(year, null)
                    RewindHomeYear(
                        year = year,
                        minutes = yearEvents.sumOf { it.event.playTime.coerceAtLeast(0L) } / 60_000L,
                        plays = yearEvents.size,
                        months = getMonthlyStats(start, end, yearEvents.map { it.event }),
                        monthTopArtworks = monthTopArtworks(year, yearEvents, zone),
                        topArtworks = yearTopArtworks(year, yearEvents, zone)
                    )
                }
        } catch (e: Exception) {
            Timber.tag("RewindData").e(e, "Failed to load rewind home data")
            emptyList()
        }
    }

    /**
     * Each month's collage set for [year]: the top song is ranked in memory from [yearEvents];
     * the top artist, album and playlist come from single-row listening stats queries (months
     * without plays are all null).
     */
    private suspend fun monthTopArtworks(
        year: Int,
        yearEvents: List<EventWithSong>,
        zone: ZoneId
    ): List<TopArtworks> {
        val eventsByMonth = yearEvents.groupBy {
            Instant.ofEpochMilli(it.event.timestamp).atZone(zone).monthValue
        }
        return (1..12).map { month ->
            val monthEvents = eventsByMonth[month].orEmpty()
            if (monthEvents.isEmpty()) {
                TopArtworks(null, null, null, null)
            } else {
                val (monthStart, monthEnd) = rewindPeriodBoundaries(year, month)
                TopArtworks(
                    song = topSongThumbnails(monthEvents, limit = 1, zone).firstOrNull(),
                    artist = Database.eventTable
                        .findArtistListeningStatsBetween(monthStart, monthEnd, 1)
                        .first()
                        .firstOrNull()
                        ?.artist
                        ?.thumbnailUrl,
                    album = Database.eventTable
                        .findAlbumListeningStatsBetween(monthStart, monthEnd, 1)
                        .first()
                        .firstOrNull()
                        ?.album
                        ?.thumbnailUrl,
                    playlist = Database.eventTable
                        .findPlaylistListeningStatsBetween(monthStart, monthEnd, 1)
                        .first()
                        .firstOrNull()
                        ?.playlist
                )
            }
        }
    }

    /**
     * The year's own collage set: the top song is ranked in memory from [yearEvents]; the top
     * artist, album and playlist come from single-row listening stats queries over the whole
     * year (all null when the year has no plays).
     */
    private suspend fun yearTopArtworks(
        year: Int,
        yearEvents: List<EventWithSong>,
        zone: ZoneId
    ): TopArtworks {
        if (yearEvents.isEmpty()) return TopArtworks(null, null, null, null)
        val (yearStart, yearEnd) = rewindPeriodBoundaries(year, null)
        return TopArtworks(
            song = topSongThumbnails(yearEvents, limit = 1, zone).firstOrNull(),
            artist = Database.eventTable
                .findArtistListeningStatsBetween(yearStart, yearEnd, 1)
                .first()
                .firstOrNull()
                ?.artist
                ?.thumbnailUrl,
            album = Database.eventTable
                .findAlbumListeningStatsBetween(yearStart, yearEnd, 1)
                .first()
                .firstOrNull()
                ?.album
                ?.thumbnailUrl,
            playlist = Database.eventTable
                .findPlaylistListeningStatsBetween(yearStart, yearEnd, 1)
                .first()
                .firstOrNull()
                ?.playlist
        )
    }
    
    private fun createEmptyData(year: Int, month: Int? = null): RewindData {
        return RewindData(
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
            year = year,
            daysWithMusic = 0,
            periodLabel = rewindPeriodLabel(year, month),
            daysInPeriod = rewindDaysInPeriod(year, month)
        )
    }
}

/**
 * Boundaries (epoch millis, [start, end]) of a rewind period: the whole [year] when [month] is
 * null, otherwise that single month. The end is the last millisecond of the period, matching
 * the deck's inclusive timestamp filtering.
 */
internal fun rewindPeriodBoundaries(year: Int, month: Int?): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val start = if (month == null) {
        LocalDateTime.of(year, 1, 1, 0, 0, 0)
    } else {
        LocalDate.of(year, month, 1).atStartOfDay()
    }
    val end = if (month == null) {
        LocalDateTime.of(year, 12, 31, 23, 59, 59, 999_999_999)
    } else {
        LocalDate.of(year, month, 1).plusMonths(1).minusDays(1).atTime(LocalTime.MAX)
    }
    return start.atZone(zone).toInstant().toEpochMilli() to end.atZone(zone).toInstant().toEpochMilli()
}

/**
 * Calendar days in a rewind period: the year, or the month of that year (monthly deck).
 * Drives the listening-days slide ratio and the average daily minutes.
 */
internal fun rewindDaysInPeriod(year: Int, month: Int?): Int =
    if (month == null) Year.of(year).length() else Year.of(year).atMonth(month).lengthOfMonth()

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
    return (1..rewindDaysInPeriod(year, month)).map { day ->
        val dayEvents = grouped[day].orEmpty()
        CalendarDayStat(
            day = day,
            minutes = dayEvents.sumOf { it.playTime.coerceAtLeast(0L) } / 60_000L,
            plays = dayEvents.size
        )
    }
}

/**
 * Artwork of the [limit] most-played songs of [events] (null where a song has no artwork),
 * in deterministic order: play count descending, then song id. Fewer entries when there are
 * fewer distinct songs.
 */
internal fun topSongThumbnails(
    events: List<EventWithSong>,
    limit: Int = 4,
    zone: ZoneId = ZoneId.systemDefault()
): List<String?> {
    return events
        .groupBy { it.song.id }
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String, List<EventWithSong>>> { it.value.size }
                .thenBy { it.key }
        )
        .take(limit)
        .map { it.value.first().song.thumbnailUrl }
}

/**
 * Kicker label of a rewind period: "2026" for a year, "JANV. 2026" for a month — the localized
 * short month name uppercased to match the deck's brand style (a dot is appended when the
 * locale does not provide one, so "JAN." in English stays "JANV." in French).
 */
internal fun rewindPeriodLabel(year: Int, month: Int?): String {
    if (month == null) return year.toString()
    val short = Month.of(month)
        .getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
        .uppercase(Locale.getDefault())
    val withDot = if (short.endsWith('.')) short else "$short."
    return "$withDot $year"
}