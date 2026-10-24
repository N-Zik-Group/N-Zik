package app.n_zik.android.core.database

import app.n_zik.android.core.database.*

import android.database.SQLException
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Event
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.models.Song
import kotlinx.coroutines.flow.Flow
import app.n_zik.android.core.database.ext.AlbumListeningStat
import app.n_zik.android.core.database.ext.ArtistListeningStat
import app.n_zik.android.core.database.ext.EventWithSong
import app.n_zik.android.core.database.ext.PlaylistListeningStat

@Dao
@RewriteQueriesToDropUnusedColumns
interface EventTable : RewindEventSource {

    @Query("SELECT COUNT(*) FROM Event")
    fun countAll(): Flow<Long>

    @Transaction
    @Query("SELECT DISTINCT * FROM Event LIMIT :limit")
    fun allWithSong( limit: Int = Int.MAX_VALUE ): Flow<List<EventWithSong>>

    /**
     * Songs listened to at least once within [from, to] (default [to]: now),
     * sorted from most to least listened (total play time), trimmed to [limit].
     *
     * @param from beginning of period in epoch millis
     * @param to end of period in epoch millis
     * @param limit trim the result to this maximum size
     */
    @Query("""
        SELECT DISTINCT S.*
        FROM Song S
        JOIN Event E ON E.songId = S.id
        WHERE E."timestamp" BETWEEN :from AND :to
        AND (S.likedAt IS NULL OR S.likedAt >= 0)
        GROUP BY E.songId 
        ORDER BY SUM(E.playtime) DESC
        LIMIT :limit
    """)
    fun findSongsMostPlayedBetween(
        from: Long,
        to: Long = System.currentTimeMillis(),
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Song>>

    /**
     * Artists whose songs were listened to at least once within [from, to]
     * (default [to]: now), sorted by total play time descending, trimmed to [limit].
     *
     * @param from beginning of period in epoch millis
     * @param to end of period in epoch millis
     * @param limit trim the result to this maximum size
     */
    @Query("""
        SELECT DISTINCT A.*
        FROM Artist A
        JOIN SongArtistMap SAM ON SAM.artistId = A.id
        JOIN Event E ON E.songId = SAM.songId
        WHERE E."timestamp" BETWEEN :from AND :to
        AND (A.dislikedAt IS NULL)
        GROUP BY A.id
        ORDER BY SUM(E.playtime) DESC
        LIMIT :limit
    """)
    fun findArtistsMostPlayedBetween(
        from: Long,
        to: Long = System.currentTimeMillis(),
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Artist>>

    /**
     * Return artists whose songs were listened to within `[from, to]`, with the windowed
     * total play time and distinct song count.
     *
     * Same contract as [RewindEventSource.findSongListeningStatsBetween]: disliked artists
     * are excluded, results are ordered by total play time, then song count, then id, and
     * trimmed to [limit] — the deck's Top Artists slide reads this query directly
     * (spec GH-275, re-review: disliked exclusion aligned with the sibling queries).
     *
     * @param from beginning of period in epoch millis
     * @param to end of period in epoch millis
     * @param limit trim the result to this maximum size
     */
    @Query("""
        SELECT A.*, SUM(E.playtime) AS playTimeMs, COUNT(DISTINCT E.songId) AS songCount
        FROM Artist A
        JOIN SongArtistMap SAM ON SAM.artistId = A.id
        JOIN (
            SELECT songId, timestamp, playtime
            FROM Event
            WHERE "timestamp" BETWEEN :from AND :to
            GROUP BY songId, timestamp, playtime
        ) E ON E.songId = SAM.songId
        WHERE (A.dislikedAt IS NULL)
        GROUP BY A.id
        ORDER BY playTimeMs DESC, songCount DESC, A.id ASC
        LIMIT :limit
    """)
    fun findArtistListeningStatsBetween(
        from: Long,
        to: Long = System.currentTimeMillis(),
        limit: Int = Int.MAX_VALUE
    ): Flow<List<ArtistListeningStat>>

    /**
     * Albums whose songs were listened to at least once within [from, to]
     * (default [to]: now), sorted by total play time descending, trimmed to [limit].
     *
     * @param from beginning of period in epoch millis
     * @param to end of period in epoch millis
     * @param limit trim the result to this maximum size
     */
    @Query("""
        SELECT DISTINCT A.*
        FROM Album A
        JOIN SongAlbumMap SAM ON SAM.albumId = A.id
        JOIN Event E ON E.songId = SAM.songId
        WHERE E."timestamp" BETWEEN :from AND :to
        AND (A.dislikedAt IS NULL)
        GROUP BY A.id
        ORDER BY SUM(E.playtime) DESC
        LIMIT :limit
    """)
    fun findAlbumsMostPlayedBetween(
        from: Long,
        to: Long = System.currentTimeMillis(),
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Album>>

    /**
     * Return albums whose songs were listened to within `[from, to]`, with the windowed
     * total play time and distinct song count.
     *
     * Same contract as [RewindEventSource.findSongListeningStatsBetween]: disliked albums
     * are excluded, results are ordered by total play time, then song count, then id, and
     * trimmed to [limit] — the deck's Top Albums slide reads this query directly
     * (spec GH-275, re-review: disliked exclusion aligned with the sibling queries).
     *
     * @param from beginning of period in epoch millis
     * @param to end of period in epoch millis
     * @param limit trim the result to this maximum size
     */
    @Query("""
        SELECT A.*, SUM(E.playtime) AS playTimeMs, COUNT(DISTINCT E.songId) AS songCount
        FROM Album A
        JOIN SongAlbumMap SAM ON SAM.albumId = A.id
        JOIN (
            SELECT songId, timestamp, playtime
            FROM Event
            WHERE "timestamp" BETWEEN :from AND :to
            GROUP BY songId, timestamp, playtime
        ) E ON E.songId = SAM.songId
        WHERE (A.dislikedAt IS NULL)
        GROUP BY A.id
        ORDER BY playTimeMs DESC, songCount DESC, A.id ASC
        LIMIT :limit
    """)
    fun findAlbumListeningStatsBetween(
        from: Long,
        to: Long = System.currentTimeMillis(),
        limit: Int = Int.MAX_VALUE
    ): Flow<List<AlbumListeningStat>>

    /**
     * Playlists whose songs were listened to at least once within [from, to]
     * (default [to]: now), as [PlaylistPreview], sorted by total play time
     * descending, trimmed to [limit].
     *
     * The generated `rewind-*` playlists are excluded (user decision, 2026-10-24,
     * same rationale as [findPlaylistListeningStatsBetween]): they hold the user's
     * most-played songs and would always rank first, making the statistics ranking
     * self-referential. The exclusion is deliberately broader than
     * `RewindPlaylists.isRewind`: any `rewind-` prefix is dropped (SQLite's LIKE is
     * case-insensitive over ASCII), covering the three generated name forms
     * (`rewind-monthly:`, `rewind-yearly:`, `rewind-alltime`) plus any user-created
     * playlist that reuses that namespace.
     *
     * @param from beginning of period in epoch millis
     * @param to end of period in epoch millis
     * @param limit trim the result to this maximum size
     */
    @Query("""
        SELECT DISTINCT P.*, COUNT(SPM.songId) AS songCount
        FROM Playlist P
        JOIN SongPlaylistMap SPM ON SPM.playlistId = P.id
        JOIN Event E ON E.songId = SPM.songId
        WHERE E."timestamp" BETWEEN :from AND :to
        AND P.name NOT LIKE 'rewind-%'
        GROUP BY P.id
        ORDER BY SUM(E.playtime) DESC
        LIMIT :limit
    """)
    fun findPlaylistMostPlayedBetweenAsPreview(
        from: Long,
        to: Long = System.currentTimeMillis(),
        limit: Int = Int.MAX_VALUE
    ): Flow<List<PlaylistPreview>>

    /**
     * Return a list of playlists with their listening totals inside the period.
     *
     * Same join as [findPlaylistMostPlayedBetweenAsPreview] but also aggregates the
     * total play time so callers can rank and display real minutes per playlist.
     *
     * The generated `rewind-*` playlists are excluded (user decision, 2026-09-24):
     * they contain the user's most-played songs and would always rank first, making
     * the Rewind deck's top-playlist story self-referential. All three generated name
     * forms share the `rewind-` prefix (`rewind-monthly:`, `rewind-yearly:`,
     * `rewind-alltime`); SQLite's LIKE is case-insensitive over ASCII, matching the
     * ignoreCase predicate of `RewindPlaylists.isRewind`.
     *
     * @param from beginning of period in epoch millis
     * @param to end of period in epoch millis
     * @param limit trim the result to this maximum size
     */
    @Query("""
        SELECT P.*, SUM(E.playtime) AS playTimeMs, COUNT(DISTINCT SPM.songId) AS songCount
        FROM Playlist P
        JOIN SongPlaylistMap SPM ON SPM.playlistId = P.id
        JOIN Event E ON E.songId = SPM.songId
        WHERE E."timestamp" BETWEEN :from AND :to
        AND P.name NOT LIKE 'rewind-%'
        GROUP BY P.id
        ORDER BY playTimeMs DESC, songCount DESC, P.id ASC
        LIMIT :limit
    """)
    fun findPlaylistListeningStatsBetween(
        from: Long,
        to: Long = System.currentTimeMillis(),
        limit: Int = Int.MAX_VALUE
    ): Flow<List<PlaylistListeningStat>>

    /**
     * Return a list of songs sorted by last played timestamp (most recently played first).
     *
     * @param limit maximum number of results
     * @return [Song]s sorted by last played date (descending)
     */
    @Query("""
        SELECT S.*
        FROM Song S
        JOIN Event E ON E.songId = S.id
        GROUP BY S.id
        ORDER BY MAX(E.timestamp) DESC
        LIMIT :limit
    """)
    fun findSongsLastPlayed(
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Song>>

    /** Inserts [event], silently skipping it when the row already exists (onConflict IGNORE). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore( event: Event )

    @Query("DELETE FROM Event")
    fun deleteAll(): Int

    /**
     * Total playtime for all songs over [from, to], summed in SQL instead of in memory.
     *
     * @return sum of playtime in ms
     */
    @Query("SELECT IFNULL(SUM(E.playtime), 0) FROM Event E WHERE E.timestamp BETWEEN :from AND :to")
    fun getTotalPlayTimeBetween(from: Long, to: Long = System.currentTimeMillis()): Flow<Long>

    /**
     * Number of distinct songs played in [from, to] — unlike
     * [findSongsMostPlayedBetween], not affected by any limit.
     *
     * @return number of distinct songs played in the period
     */
    @Query("SELECT COUNT(DISTINCT E.songId) FROM Event E WHERE E.timestamp BETWEEN :from AND :to")
    fun countDistinctSongsPlayedBetween(from: Long, to: Long = System.currentTimeMillis()): Flow<Int>

    /** Return the number of library songs listened to at least once: the distinct songs of the Song table owning at least one playback event (orphan events for songs removed from the library do not count). */
    @Query("SELECT COUNT(DISTINCT E.songId) FROM Event E JOIN Song S ON S.id = E.songId")
    suspend fun countListenedSongs(): Int

    /**
     * Total playtime of [songIds] over [from, to] in one query.
     *
     * @return sum of playtime in ms for the specified songs
     */
    @Query("SELECT IFNULL(SUM(E.playtime), 0) FROM Event E WHERE E.songId IN (:songIds) AND E.timestamp BETWEEN :from AND :to")
    fun getSongsTotalPlayTimeBetween(songIds: List<String>, from: Long, to: Long = System.currentTimeMillis()): Flow<Long>

    /**
     * Sum of playtime of [songId] over [from, to].
     *
     * @return sum of playtime in ms
     */
    @Query("SELECT IFNULL(SUM(E.playtime), 0) FROM Event E WHERE E.songId = :songId AND E.timestamp BETWEEN :from AND :to")
    fun getSongPlayTimeBetween(songId: String, from: Long, to: Long = System.currentTimeMillis()): Flow<Long>

    @Query("SELECT COUNT(*) FROM Event E WHERE E.songId = :songId")
    fun getPlayCount(songId: String): Flow<Int>

    @Query("SELECT E.songId, COUNT(*) as playCount FROM Event E GROUP BY E.songId")
    fun getAllPlayCounts(): Flow<List<PlayCountTuple>>

    @Query("""
        SELECT IFNULL(SUM(E.playtime), 0)
        FROM Event E
        JOIN SongArtistMap SAM ON SAM.songId = E.songId
        WHERE SAM.artistId = :artistId
    """)
    fun getArtistTotalPlayTime(artistId: String): Flow<Long>

    @Query("""
        SELECT COUNT(E.id)
        FROM Event E
        JOIN SongArtistMap SAM ON SAM.songId = E.songId
        WHERE SAM.artistId = :artistId
    """)
    fun getArtistPlayCount(artistId: String): Flow<Int>

    @Query("""
        SELECT IFNULL(SUM(E.playtime), 0)
        FROM Event E
        JOIN SongAlbumMap SAM ON SAM.songId = E.songId
        WHERE SAM.albumId = :albumId
    """)
    fun getAlbumTotalPlayTime(albumId: String): Flow<Long>

    @Query("""
        SELECT COUNT(E.id)
        FROM Event E
        JOIN SongAlbumMap SAM ON SAM.songId = E.songId
        WHERE SAM.albumId = :albumId
    """)
    fun getAlbumPlayCount(albumId: String): Flow<Int>

    @Query("""
        SELECT IFNULL(SUM(E.playtime), 0)
        FROM Event E
        JOIN SongPlaylistMap SPM ON SPM.songId = E.songId
        WHERE SPM.playlistId = :playlistId
    """)
    fun getPlaylistTotalPlayTime(playlistId: Long): Flow<Long>

    @Query("""
        SELECT COUNT(E.id)
        FROM Event E
        JOIN SongPlaylistMap SPM ON SPM.songId = E.songId
        WHERE SPM.playlistId = :playlistId
    """)
    fun getPlaylistPlayCount(playlistId: Long): Flow<Int>

    /** The listening totals per album (play count + total play time in ms), one row per album with events — the batch form of [getAlbumPlayCount] / [getAlbumTotalPlayTime] (the phone's album grid overlays, `HomeAlbum.kt` 723-754). */
    @Query("""
        SELECT SAM.albumId AS albumId, COUNT(E.id) AS playCount, IFNULL(SUM(E.playtime), 0) AS totalPlayTimeMs
        FROM Event E
        JOIN SongAlbumMap SAM ON SAM.songId = E.songId
        GROUP BY SAM.albumId
    """)
    fun getAlbumListeningTotals(): Flow<List<AlbumListening>>

    /** The listening totals per artist (play count + total play time in ms), one row per artist with events — the batch form of [getArtistPlayCount] / [getArtistTotalPlayTime] (the phone's artist grid overlays, `HomeArtist.kt` 687-710). */
    @Query("""
        SELECT SAM.artistId AS artistId, COUNT(E.id) AS playCount, IFNULL(SUM(E.playtime), 0) AS totalPlayTimeMs
        FROM Event E
        JOIN SongArtistMap SAM ON SAM.songId = E.songId
        GROUP BY SAM.artistId
    """)
    fun getArtistListeningTotals(): Flow<List<ArtistListening>>

    /** The listening totals per playlist (play count + total play time in ms), one row per playlist with events — the batch form of [getPlaylistPlayCount] / [getPlaylistTotalPlayTime] (the phone's playlist grid overlays, `HomeLibrary.kt` 887-919). */
    @Query("""
        SELECT SPM.playlistId AS playlistId, COUNT(E.id) AS playCount, IFNULL(SUM(E.playtime), 0) AS totalPlayTimeMs
        FROM Event E
        JOIN SongPlaylistMap SPM ON SPM.songId = E.songId
        GROUP BY SPM.playlistId
    """)
    fun getPlaylistListeningTotals(): Flow<List<PlaylistListening>>

    @Query("UPDATE Event SET songId = :newId WHERE songId = :oldId")
    fun updateSongId(oldId: String, newId: String)

    @Query("DELETE FROM Event WHERE songId IN (:songIds)")
    fun deleteBySongIds(songIds: List<String>): Int

    @Query("DELETE FROM Event WHERE songId = :songId")
    fun deleteBySongId(songId: String): Int
}

data class PlayCountTuple(
    val songId: String,
    val playCount: Int
)

/** One row of [EventTable.getAlbumListeningTotals]. */
data class AlbumListening(
    val albumId: String,
    val playCount: Int,
    val totalPlayTimeMs: Long
)

/** One row of [EventTable.getArtistListeningTotals]. */
data class ArtistListening(
    val artistId: String,
    val playCount: Int,
    val totalPlayTimeMs: Long
)

/** One row of [EventTable.getPlaylistListeningTotals]. */
data class PlaylistListening(
    val playlistId: Long,
    val playCount: Int,
    val totalPlayTimeMs: Long
)


