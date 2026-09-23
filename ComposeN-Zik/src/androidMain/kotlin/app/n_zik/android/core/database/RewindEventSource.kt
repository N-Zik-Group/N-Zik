package app.n_zik.android.core.database

import androidx.room.Query
import app.it.fast4x.rimusic.models.Event
import app.n_zik.android.core.database.ext.RewindYearMonthStat
import app.n_zik.android.core.database.ext.RewindYearStat
import app.n_zik.android.core.database.ext.SongListeningStat
import kotlinx.coroutines.flow.Flow

/**
 * Rewind-specific, read-only, windowed queries over the [Event] play journal.
 *
 * Every query is bounded by an inclusive `[from, to]` timestamp range (epoch millis),
 * so the same DAO surface serves the monthly, yearly and all-time (global) periods
 * with bounded SQL (see spec GH-275, patch "Data loading"). [eventsBetween] is the
 * one materializing query: the fetcher aggregates its windowed rows in memory, so the
 * all-time deck pulls the whole journal at once — an accepted trade-off documented on
 * the RewindDataFetcher class.
 *
 * [EventTable] implements this interface; the extra surface keeps the rewind
 * queries testable and discoverable in one place.
 */
interface RewindEventSource {

    /**
     * Return every raw play event whose timestamp falls inside `[from, to]`.
     *
     * Used by the Rewind fetcher as the single windowed scan from which the
     * monthly / daily / hourly breakdowns, calendar days and first/last play
     * are aggregated in memory (on the DATA dispatcher).
     *
     * @param from start of the window (epoch millis, inclusive)
     * @param to end of the window (epoch millis, inclusive)
     */
    @Query("SELECT * FROM Event WHERE \"timestamp\" BETWEEN :from AND :to")
    fun eventsBetween(from: Long, to: Long): Flow<List<Event>>

    /**
     * Return each distinct song played inside `[from, to]` with its windowed
     * play count and total play time.
     *
     * Songs are ranked by play count, then total play time, then id — the same
     * order the deck ranking uses — so results are deterministic.
     * Disliked songs (mirrored in [app.it.fast4x.rimusic.models.Song.likedAt])
     * are excluded, matching the existing top-song lists.
     *
     * @param from start of the window (epoch millis, inclusive)
     * @param to end of the window (epoch millis, inclusive)
     * @param limit trim the result to at most this many songs
     * @return [SongListeningStat]s for every distinct song played in the window
     */
    @Query("""
        SELECT S.*, COUNT(E.id) AS playCountTotal, IFNULL(SUM(E.playtime), 0) AS playTimeMs
        FROM Song S
        JOIN Event E ON E.songId = S.id
        WHERE E."timestamp" BETWEEN :from AND :to
        AND (S.likedAt IS NULL OR S.likedAt >= 0)
        GROUP BY S.id
        ORDER BY playCountTotal DESC, playTimeMs DESC, S.id ASC
        LIMIT :limit
    """)
    fun findSongListeningStatsBetween(
        from: Long,
        to: Long,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<SongListeningStat>>

    /**
     * Return the number of distinct artists with at least one play inside
     * `[from, to]`. Disliked artists are excluded, matching
     * [EventTable.findArtistsMostPlayedBetween].
     */
    @Query("""
        SELECT COUNT(DISTINCT A.id)
        FROM Artist A
        JOIN SongArtistMap SAM ON SAM.artistId = A.id
        JOIN Event E ON E.songId = SAM.songId
        WHERE E."timestamp" BETWEEN :from AND :to
        AND A.dislikedAt IS NULL
    """)
    fun countDistinctArtistsBetween(from: Long, to: Long): Flow<Int>

    /**
     * Return the number of distinct albums with at least one play inside
     * `[from, to]`. Disliked albums are excluded, matching
     * [EventTable.findAlbumsMostPlayedBetween].
     */
    @Query("""
        SELECT COUNT(DISTINCT A.id)
        FROM Album A
        JOIN SongAlbumMap SAM ON SAM.albumId = A.id
        JOIN Event E ON E.songId = SAM.songId
        WHERE E."timestamp" BETWEEN :from AND :to
        AND A.dislikedAt IS NULL
    """)
    fun countDistinctAlbumsBetween(from: Long, to: Long): Flow<Int>

    /**
     * Return the number of distinct playlists with at least one play inside
     * `[from, to]`. No disliked filter: playlists have none, matching
     * [EventTable.findPlaylistListeningStatsBetween].
     */
    @Query("""
        SELECT COUNT(DISTINCT P.id)
        FROM Playlist P
        JOIN SongPlaylistMap SPM ON SPM.playlistId = P.id
        JOIN Event E ON E.songId = SPM.songId
        WHERE E."timestamp" BETWEEN :from AND :to
    """)
    fun countDistinctPlaylistsBetween(from: Long, to: Long): Flow<Int>

    /**
     * Return the play totals grouped by calendar year (local timezone),
     * most recent year first. Years without plays are omitted.
     *
     * Feeds the "Your years" home section (one row per year with data).
     */
    @Query("""
        SELECT CAST(strftime('%Y', "timestamp" / 1000, 'unixepoch', 'localtime') AS INTEGER) AS year,
               COUNT(*) AS plays,
               IFNULL(SUM(playtime), 0) AS playTimeMs
        FROM Event
        GROUP BY year
        HAVING plays > 0
        ORDER BY year DESC
    """)
    fun rewindYearTotals(): Flow<List<RewindYearStat>>

    /**
     * Return the play totals grouped by calendar year AND month (local
     * timezone), most recent first. Year-months without plays are omitted.
     *
     * Feeds the home 12-slot month grid (empty months are filled in by the
     * fetcher with zero plays).
     */
    @Query("""
        SELECT CAST(strftime('%Y', "timestamp" / 1000, 'unixepoch', 'localtime') AS INTEGER) AS year,
               CAST(strftime('%m', "timestamp" / 1000, 'unixepoch', 'localtime') AS INTEGER) AS month,
               COUNT(*) AS plays,
               IFNULL(SUM(playtime), 0) AS playTimeMs
        FROM Event
        GROUP BY year, month
        HAVING plays > 0
        ORDER BY year DESC, month DESC
    """)
    fun rewindYearMonthTotals(): Flow<List<RewindYearMonthStat>>
}
