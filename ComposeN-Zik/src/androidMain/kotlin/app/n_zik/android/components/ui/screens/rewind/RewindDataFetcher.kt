package app.n_zik.android.components.ui.screens.rewind

import app.it.fast4x.rimusic.models.*
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.ext.EventWithSong
import it.fast4x.innertube.YtMusic
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
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
    val daysWithMusic: Int
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
    
    // Get rewind data for a specific year
    suspend fun getRewindData(year: Int): RewindData {
        return try {
            // Get year boundaries
            val (yearStart, yearEnd) = getYearBoundaries(year)
            
            // Get all events for the year - use the available method from EventTable
            val allEvents = Database.eventTable.allWithSong(Int.MAX_VALUE).first()
            
            // Filter events for the specific year
            val yearlyEvents = allEvents
                .asSequence()
                .filter { eventWithSong ->
                    eventWithSong.event.timestamp in yearStart..yearEnd
                }
                .toList()
            
            if (yearlyEvents.isEmpty()) {
                return createEmptyData(year)
            }
            
            // Extract just the events
            val events = yearlyEvents.map { it.event }
            
            val topSongs = getTopSongs(yearlyEvents)
            val topArtists = getTopArtists(yearStart, yearEnd)
            val topAlbums = getTopAlbums(yearStart, yearEnd)
            val topPlaylists = getTopPlaylists(yearStart, yearEnd)
            
            // Get counts using actual database queries
            val totalUniqueSongs = Database.eventTable
                .findSongsMostPlayedBetween(yearStart, yearEnd, Int.MAX_VALUE)
                .first()
                .size
            
            val totalUniqueArtists = Database.eventTable
                .findArtistsMostPlayedBetween(yearStart, yearEnd, Int.MAX_VALUE)
                .first()
                .size
            
            val totalUniqueAlbums = Database.eventTable
                .findAlbumsMostPlayedBetween(yearStart, yearEnd, Int.MAX_VALUE)
                .first()
                .size
            
            val totalUniquePlaylists = Database.eventTable
                .findPlaylistMostPlayedBetweenAsPreview(yearStart, yearEnd, Int.MAX_VALUE)
                .first()
                .size
            
            val monthlyStats = getMonthlyStats(yearStart, yearEnd, events)
            val dailyStats = getDailyStats(yearStart, yearEnd, events)
            val hourlyStats = getHourlyStats(yearStart, yearEnd, events)
            
            val totalPlaytimeMs = events.sumOf { event -> event.playTime.coerceAtLeast(0L) }
            
            // Calculate listening stats
            val stats = calculateListeningStats(
                events, 
                totalPlaytimeMs,
                monthlyStats, 
                dailyStats, 
                hourlyStats, 
                year
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
                daysWithMusic = daysWithMusic
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
            createEmptyData(year)
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
        year: Int
    ): ListeningStats {
        val totalPlays = events.size
        val totalMinutes = totalPlaytimeMs / 60000
        
        // Get most active month
        val mostActiveMonth = monthlyStats.maxByOrNull { it.minutes }
        
        // Get most active day
        val mostActiveDay = dailyStats.maxByOrNull { it.minutes }
        
        // Get most active hour
        val mostActiveHour = hourlyStats.maxByOrNull { it.minutes }
        
        // Calculate average daily minutes
        val daysInYear = if (Instant.ofEpochMilli(getYearBoundaries(year).first)
                .atZone(ZoneId.systemDefault())
                .toLocalDate().isLeapYear) 366 else 365
        val averageDailyMinutes = if (daysInYear > 0) totalMinutes.toDouble() / daysInYear else 0.0
        
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
    
    private fun getYearBoundaries(year: Int): Pair<Long, Long> {
        val yearStart = LocalDateTime.of(year, 1, 1, 0, 0, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        
        val yearEnd = LocalDateTime.of(year, 12, 31, 23, 59, 59, 999_999_999)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        
        return Pair(yearStart, yearEnd)
    }
    
    private fun formatDate(timestamp: Long): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault()))
    }
    
    private fun createEmptyData(year: Int): RewindData {
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
            daysWithMusic = 0
        )
    }
}