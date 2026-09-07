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
import app.n_zik.android.core.database.ext.EventWithSong

@Dao
@RewriteQueriesToDropUnusedColumns
interface EventTable {

    @Query("SELECT COUNT(*) FROM Event")
    fun countAll(): Flow<Long>

    @Transaction
    @Query("SELECT DISTINCT * FROM Event LIMIT :limit")
    fun allWithSong( limit: Int = Int.MAX_VALUE ): Flow<List<EventWithSong>>

    /**
     * Return a list of songs that were listened to by user.
     *
     * Songs must be listened at least once within [from] and [to]
     * be included in the results.
     *
     * Results are sorted from most listened to least listened to.
     *
     * By default, only [from] is required, [to] is set to current time.
     * Meaning fetch all from [from] to present.
     *
     * @param from beginning of period to query in epoch millis format
     * @param to the end of period to query in epoch millis format
     * @param limit trim result to have maximum size of this value
     *
     * @return [Song]s that were listened to at least once in period in descending order
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
     * Return a list of artists that have their songs listened to by user.
     *
     * Songs must be listened at least once within [from] and [to]
     * be included in the results.
     *
     * Results are sorted by total [Song.totalPlayTimeMs] in descending order.
     *
     * By default, only [from] is required, [to] is set to current time.
     * Meaning fetch all from [from] to present.
     *
     * @param from beginning of period to query in epoch millis format
     * @param to the end of period to query in epoch millis format
     * @param limit trim result to have maximum size of this value
     *
     * @return [Artist]s that have their songs listened to at least once in period in descending order
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
     * Return a list of albums that have their songs listened to by user.
     *
     * Songs must be listened at least once within [from] and [to]
     * be included in the results.
     *
     * Results are sorted by total [Song.totalPlayTimeMs] in descending order.
     *
     * By default, only [from] is required, [to] is set to current time.
     * Meaning fetch all from [from] to present.
     *
     * @param from beginning of period to query in epoch millis format
     * @param to the end of period to query in epoch millis format
     * @param limit trim result to have maximum size of this value
     *
     * @return [Album]s that have their songs listened to at least once in period in descending order
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
     * Return a list of playlists that have their songs were listened to by user.
     *
     * Songs must be listened at least once within [from] and [to]
     * be included in the results.
     *
     * Results are converted into [PlaylistPreview] and
     * sorted by total [Song.totalPlayTimeMs] in descending order.
     *
     * By default, only [from] is required, [to] is set to current time.
     * Meaning fetch all from [from] to present.
     *
     * @param from beginning of period to query in epoch millis format
     * @param to the end of period to query in epoch millis format
     * @param limit trim result to have maximum size of this value
     *
     * @return [PlaylistPreview] that their songs were listened to at least once in period in descending order
     */
    @Query("""
        SELECT DISTINCT P.*, COUNT(SPM.songId) AS songCount
        FROM Playlist P
        JOIN SongPlaylistMap SPM ON SPM.playlistId = P.id
        JOIN Event E ON E.songId = SPM.songId
        WHERE E."timestamp" BETWEEN :from AND :to
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

    /**
     * Attempt to write [event] into database.
     *
     * ### Standalone use
     *
     * When error occurs and [SQLException] is thrown,
     * it'll simply be ignored.
     *
     * ### Transaction use
     *
     * When error occurs and [SQLException] is thrown,
     * it'll simply be ignored and the transaction continues.
     *
     * @param event data intended to insert in to database
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore( event: Event )

    @Query("DELETE FROM Event")
    fun deleteAll(): Int

    /**
     * Return the total playtime for all songs over a given period.
     * This is much more efficient than loading all songs and summing in memory.
     * @param from start of the period (epoch millis)
     * @param to end of the period (epoch millis)
     * @return sum of playtime in ms
     */
    @Query("SELECT IFNULL(SUM(E.playtime), 0) FROM Event E WHERE E.timestamp BETWEEN :from AND :to")
    fun getTotalPlayTimeBetween(from: Long, to: Long = System.currentTimeMillis()): Flow<Long>

    /**
     * Return the total playtime for specific songs over a given period.
     * This is much more efficient than querying each song individually.
     * @param songIds list of song IDs to calculate playtime for
     * @param from start of the period (epoch millis)
     * @param to end of the period (epoch millis)
     * @return sum of playtime in ms for the specified songs
     */
    @Query("SELECT IFNULL(SUM(E.playtime), 0) FROM Event E WHERE E.songId IN (:songIds) AND E.timestamp BETWEEN :from AND :to")
    fun getSongsTotalPlayTimeBetween(songIds: List<String>, from: Long, to: Long = System.currentTimeMillis()): Flow<Long>

    /**
     * return the sum of playtime for a given song over a given period.
     * @param songId song id
     * @param from start of the period (epoch millis)
     * @param to end of the period (epoch millis)
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


