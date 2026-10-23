package app.n_zik.android.core.database

import app.n_zik.android.core.database.*

import android.database.SQLException
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Upsert
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.enums.SongSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.models.Format
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.durationToMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.n_zik.android.core.database.ext.FormatWithSong

@Dao
@RewriteQueriesToDropUnusedColumns
interface FormatTable {

    /**
     * @return formats & songs of this table
     */
    @Query("""
        SELECT DISTINCT F.*, S.* 
        FROM Format F
        JOIN Song S ON S.id = F.songId
        WHERE totalPlayTimeMs >= :excludeHidden
        ORDER BY S.ROWID 
        LIMIT :limit
    """)
    fun allWithSongs(
        limit: Int = Int.MAX_VALUE,
        excludeHidden: Boolean = false
    ): Flow<List<FormatWithSong>>

    /**
     * Song with [songId] will have its [Format] removed
     *
     * @return number of rows affected by this operation
     */
    @Query("DELETE FROM Format WHERE songId = :songId")
    fun deleteBySongId( songId: String ): Int

    /**
     * @param songId of song to look for
     * @return [Format] that has [Format.songId] matches [songId]
     */
    @Query("SELECT DISTINCT * FROM Format WHERE songId = :songId")
    fun findBySongId( songId: String ): Flow<Format?>

    @Query("SELECT DISTINCT * FROM Format WHERE songId = :songId")
    fun findBySongIdDirect( songId: String ): Format?

    /** Inserts [format], silently skipping it when the row already exists (onConflict IGNORE). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore( format: Format )

    /** Inserts [format], replacing the existing row's columns when its primary key already exists. */
    @Upsert
    fun upsert( format: Format )

    /**
     * @return stored [Format.contentLength] of song with id [songId], `0` otherwise
     */
    @Query("""
        SELECT COALESCE(
            (
                SELECT contentLength
                FROM Format
                WHERE songId = :songId
            ),
            0
        )
    """)
    fun findContentLengthOf( songId: String ): Flow<Long>

    /**
     * Set [Format.contentLength] of song with id [songId] to [contentLength]
     *
     * @return number of rows affected by this operation
     */
    @Query("UPDATE Format SET contentLength = :contentLength WHERE songId = :songId")
    fun updateContentLengthOf( songId: String, contentLength: Long = 0L ): Int

    /**
     * Songs in [songIds] whose [Format.downloadQuality] is not [downloadQuality].
     * A NULL tracking value (download made before the column existed) counts as non-compliant.
     *
     * @return songIds that are non-compliant with [downloadQuality]
     */
    @Query("""
        SELECT songId
        FROM Format
        WHERE songId IN (:songIds)
          AND (downloadQuality IS NULL OR downloadQuality != :downloadQuality)
    """)
    fun findNonCompliantSongIds( songIds: List<String>, downloadQuality: String ): List<String>

    /**
     * @return number of songs in [songIds] that are non-compliant with [downloadQuality]
     * (NULL tracking value included)
     */
    @Query("""
        SELECT COUNT(*)
        FROM Format
        WHERE songId IN (:songIds)
          AND (downloadQuality IS NULL OR downloadQuality != :downloadQuality)
    """)
    fun countNonCompliantDownloaded( songIds: List<String>, downloadQuality: String ): Int

    /**
     * @return the subset of [songIds] that already has a [Format] row
     */
    @Query("SELECT songId FROM Format WHERE songId IN (:songIds)")
    fun findSongIdsWithFormat( songIds: List<String> ): List<String>

    /**
     * Set [Format.downloadQuality] of song with id [songId] to [downloadQuality]
     *
     * @return number of rows affected by this operation
     */
    @Query("UPDATE Format SET downloadQuality = :downloadQuality WHERE songId = :songId")
    fun updateDownloadQuality( songId: String, downloadQuality: String ): Int

    //<editor-fold defaultstate="collapsed" desc="Sort all with songs">
    fun sortAllWithSongsByPlayTime( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>> =
        allWithSongs( limit, excludeHidden ).map { list ->
            list.sortedBy { it.song.totalPlayTimeMs }
        }

    fun sortAllWithSongsByRelativePlayTime( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>> =
        allWithSongs( limit, excludeHidden ).map { list ->
            list.sortedBy { it.song.relativePlayTime() }
        }

    @Query("""
        SELECT DISTINCT F.*, S.id, S.title, S.artistsText, S.durationText, S.thumbnailUrl, S.likedAt, S.totalPlayTimeMs, S.position, S.isYoutubeSong, COUNT(E.songId) as playCount
        FROM Format F
        JOIN Song S ON S.id = F.songId
        LEFT JOIN Event E ON E.songId = F.songId
        WHERE S.totalPlayTimeMs >= :excludeHidden
        GROUP BY F.songId
        ORDER BY playCount ASC
        LIMIT :limit
    """)
    fun sortAllWithSongsByPlayCount( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>>

    fun sortAllWithSongsByTitle( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>> =
        allWithSongs( limit, excludeHidden ).map { list ->
            list.sortedBy { it.song.cleanTitle() }
        }

    @Query("""
        SELECT DISTINCT F.*, S.*
        FROM Format F
        JOIN Song S ON S.id = F.songId
        LEFT JOIN Event E ON E.songId = F.songId 
        WHERE totalPlayTimeMs >= :excludeHidden
        ORDER BY E.timestamp ASC
        LIMIT :limit
    """)
    fun sortAllWithSongsByDatePlayed( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>>

    fun sortAllWithSongsByLikedAt( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>> =
        allWithSongs( limit, excludeHidden ).map { list ->
            list.sortedBy { it.song.likedAt }
        }

    fun sortAllWithSongsByArtist( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>> =
        allWithSongs( limit, excludeHidden ).map { list ->
            list.sortedBy { it.song.cleanArtistsText() }
        }

    fun sortAllWithSongsByDuration( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>> =
        allWithSongs( limit, excludeHidden ).map { list ->
            list.sortedBy {
                durationToMillis( it.song.durationText ?: "0:0" )
            }
        }

    @Query("""
        SELECT DISTINCT F.*, S.*
        FROM Format F
        JOIN Song S ON S.id = F.songId
        LEFT JOIN SongAlbumMap sam ON sam.songId = S.id
        LEFT JOIN Album A ON A.id = sam.albumId
        WHERE totalPlayTimeMs >= :excludeHidden
        ORDER BY 
            CASE 
                WHEN A.title LIKE '$MODIFIED_PREFIX%' THEN SUBSTR(A.title, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE A.title
            END
        LIMIT :limit
    """)
    fun sortAllWithSongsByAlbumName( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>>

    @Query("""
        SELECT DISTINCT F.*, S.*
        FROM Format F
        JOIN Song S ON S.id = F.songId
        WHERE totalPlayTimeMs >= :excludeHidden
          ORDER BY 
              CASE WHEN S.position = -1 THEN 1 ELSE 0 END ASC,
              CASE WHEN S.position = -1 THEN F.ROWID ELSE S.position END ASC
        LIMIT :limit
    """)
    fun sortAllWithSongsByPosition( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>>

    /**
     * Dispatches [sortBy]/[sortOrder] to the prebuilt queries below — most sorts run in SQL,
     * a few (Custom, Downloaded) finish in memory or fall back to ROWID.
     *
     * Songs hidden by the user carry a negative [Song.totalPlayTimeMs];
     * excludeHidden = true drops them from the results.
     *
     * @see SongSortBy
     * @see SortOrder
     */
    fun sortAllWithSongs(
        sortBy: SongSortBy,
        sortOrder: SortOrder,
        limit: Int = Int.MAX_VALUE,
        excludeHidden: Boolean = false
    ): Flow<List<FormatWithSong>> = when( sortBy ){
        SongSortBy.PlayTime         -> sortAllWithSongsByPlayTime( limit, excludeHidden )
        SongSortBy.RelativePlayTime -> sortAllWithSongsByRelativePlayTime( limit, excludeHidden )
        SongSortBy.PlayCount        -> sortAllWithSongsByPlayCount( limit, excludeHidden )
        SongSortBy.Title            -> sortAllWithSongsByTitle( limit, excludeHidden )
        SongSortBy.DateAdded        -> allWithSongs( limit, excludeHidden )      // Already sorted by ROWID
        SongSortBy.DatePlayed       -> sortAllWithSongsByDatePlayed( limit, excludeHidden )
        SongSortBy.DateLiked        -> sortAllWithSongsByLikedAt( limit, excludeHidden )
        SongSortBy.Artist           -> sortAllWithSongsByArtist( limit, excludeHidden )
        SongSortBy.Duration         -> sortAllWithSongsByDuration( limit, excludeHidden )
        SongSortBy.AlbumName        -> sortAllWithSongsByAlbumName( limit, excludeHidden )
        SongSortBy.Custom           -> sortAllWithSongsByPosition( limit, excludeHidden )
        SongSortBy.Downloaded       -> sortAllWithSongsByTitle( limit, excludeHidden ) // Handled in-memory
    }.map( sortOrder::applyTo )
    //</editor-fold>
}


