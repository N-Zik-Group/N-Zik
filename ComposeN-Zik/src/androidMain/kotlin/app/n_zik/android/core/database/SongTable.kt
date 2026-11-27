package app.n_zik.android.core.database

import android.database.SQLException
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.Transaction
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.enums.SongSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Song
import app.n_zik.android.models.SongAlbumMap
import app.n_zik.android.models.SongArtistMap
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.utils.durationToMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

data class SongArtistCredit(val songId: String, val artistId: String, val name: String?)

data class PlaybackTrackRow(
    @Embedded val song: Song,
    @Relation(
        parentColumn = "id", entityColumn = "id",
        associateBy = Junction(SongArtistMap::class, parentColumn = "songId", entityColumn = "artistId"),
    ) val artists: List<Artist>,
    @Relation(
        parentColumn = "id", entityColumn = "id",
        associateBy = Junction(SongAlbumMap::class, parentColumn = "songId", entityColumn = "albumId"),
    ) val albums: List<Album>,
)

@Dao
@RewriteQueriesToDropUnusedColumns
interface SongTable {

    /**
     * @return all records from this table
     */
    @Query("""
        SELECT DISTINCT * 
        FROM Song 
        WHERE CASE WHEN :dislikedOnly THEN likedAt < 0 ELSE totalPlayTimeMs >= :excludeHidden END
        ORDER BY ROWID 
        LIMIT :limit
    """)
    fun all(
        limit: Int = Int.MAX_VALUE,
        excludeHidden: Boolean = false,
        dislikedOnly: Boolean = false,
    ): Flow<List<Song>>

    /**
     * @return the total number of rows in this table
     */
    @Query("SELECT COUNT(*) FROM Song")
    suspend fun countAll(): Int

    /**
     * @return all records that have [Song.id] start with [LOCAL_KEY_PREFIX]
     */
    @Query("""
        SELECT DISTINCT * 
        FROM Song 
        WHERE id LIKE '$LOCAL_KEY_PREFIX%'
        LIMIT :limit
    """)
    fun allOnDevice( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    @Query("""
        SELECT DISTINCT * 
        FROM Song 
        WHERE likedAt IS NOT NULL AND likedAt > 0
        ORDER BY ROWID
        LIMIT :limit
    """)
    fun allFavorites( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    @Query("""
        SELECT DISTINCT * 
        FROM Song 
        WHERE likedAt IS NOT NULL AND likedAt < 0
        ORDER BY ROWID
        LIMIT :limit
    """)
    fun allDisliked( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return all disliked song IDs
     */
    @Query("""
        SELECT DISTINCT id
        FROM Song
        WHERE likedAt IS NOT NULL AND likedAt < 0
    """)
    suspend fun getAllDislikedIds(): List<String>

    /**
     * @return all song IDs from disliked artists
     */
    @Query("""
        SELECT DISTINCT S.id
        FROM Song S
        JOIN SongArtistMap SAM ON SAM.songId = S.id
        JOIN Artist A ON A.id = SAM.artistId
        WHERE A.dislikedAt IS NOT NULL
    """)
    suspend fun getSongsByDislikedArtists(): List<String>

    /**
     * @return all song IDs from disliked albums
     */
    @Query("""
        SELECT DISTINCT S.id
        FROM Song S
        JOIN SongAlbumMap SAM ON SAM.songId = S.id
        JOIN Album AL ON AL.id = SAM.albumId
        WHERE AL.dislikedAt IS NOT NULL
    """)
    suspend fun getSongsByDislikedAlbums(): List<String>

    /**
     * Delete all songs with [Song.totalPlayTimeMs] equal to `0`
     *
     * @return number of rows affected by this operation
     */
    @Query("DELETE FROM Song WHERE totalPlayTimeMs = 0")
    suspend fun clearHiddenSongs(): Int

    /**
     * @param songId of album to look for
     *
     * @return [Song] that has [Song.id] matches [songId]
     */
    @Query("SELECT DISTINCT * FROM Song WHERE id = :songId")
    fun findById( songId: String ): Flow<Song?>

    @Query("SELECT DISTINCT * FROM Song WHERE id = :songId")
    suspend fun findByIdDirect( songId: String ): Song?

    /** The songs of [songIds] (any order, unknown ids skipped): the PC bridge's batched queue resolution. */
    @Query("SELECT DISTINCT * FROM Song WHERE id IN (:songIds)")
    suspend fun findByIdsDirect( songIds: List<String> ): List<Song>

    /** Observes metadata for a bounded batch of menu song identities. */
    @Query("SELECT DISTINCT * FROM Song WHERE id IN (:songIds)")
    fun findByIds(songIds: List<String>): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id IN (:songIds)")
    fun observePlaybackTracks(songIds: List<String>): Flow<List<PlaybackTrackRow>>

    /** Observes artist credits for a batch of exact track IDs. */
    @Query("""
        SELECT M.songId, M.artistId, A.name
        FROM SongArtistMap M JOIN Artist A ON A.id = M.artistId
        WHERE M.songId IN (:songIds)
        ORDER BY M.songId, A.ROWID
    """)
    fun findArtistCredits(songIds: List<String>): Flow<List<SongArtistCredit>>

    @Query("SELECT COUNT(*) FROM Song WHERE id = :songId")
    suspend fun countById( songId: String ): Int

    /** Case-insensitive containment of [searchTerm] in [Song.title] or [Song.artistsText] (I.E.: `name` matches `1name_to`). */
    @Query("""
        SELECT DISTINCT * 
        FROM Song 
        WHERE title LIKE '%' || :searchTerm || '%' COLLATE NOCASE
        OR artistsText LIKE '%' || :searchTerm || '%' COLLATE NOCASE
    """)
    fun findAllTitleArtistContains( searchTerm: String ): Flow<List<Song>>

    /** Literal title or artist filtering for the in-app local search field. */
    @Query("""
        SELECT DISTINCT * FROM Song
        WHERE instr(lower(title), lower(:searchTerm)) > 0
        OR instr(lower(artistsText), lower(:searchTerm)) > 0
    """)
    fun findAllTitleArtistContainsLiteral(searchTerm: String): Flow<List<Song>>

    /** Case-insensitive match of [artistName] against [Song.artistsText], with [MODIFIED_PREFIX] stripped before comparison. */
    @Query("""
        SELECT DISTINCT * 
        FROM Song 
        WHERE trim(
            CASE 
                WHEN artistsText LIKE '$MODIFIED_PREFIX%' THEN SUBSTR(artistsText, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE artistsText
            END
        ) COLLATE NOCASE = trim(:artistName) COLLATE NOCASE
    """)
    fun findAllByArtist( artistName: String ): Flow<List<Song>>

    /** Inserts [song], silently skipping it when the row already exists (onConflict IGNORE). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore( song: Song )

    /** Replaces the row with [song] (onConflict REPLACE); returns the number of rows affected. */
    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateReplace( song: Song )

    @Query("UPDATE Song SET id = :newId WHERE id = :oldId")
    suspend fun updateId(oldId: String, newId: String): Int

    @Query("UPDATE Song SET durationText = :duration WHERE id = :id AND durationText IS :expected")
    suspend fun updateDurationIfUnchanged(id: String, expected: String?, duration: String)

    /** Inserts [song], replacing the existing row's columns when its primary key already exists. */
    @Upsert
    suspend fun upsert( song: Song )

    /** Inserts [songs], replacing each existing row's columns when its primary key already exists. */
    @Upsert
    suspend fun upsert( songs: List<Song> )

    /** Deletes [song]. */
    @Delete
    suspend fun delete( song: Song )

    /** Whether any row has id [songId]. */
    @Query("SELECT COUNT(*) > 0 FROM Song WHERE id = :songId")
    fun exists( songId: String ): Flow<Boolean>

    /** Whether [Song.likedAt] is set for [songId]; `false` when the row doesn't exist. */
    @Query("""
        SELECT COALESCE(
            (
                SELECT likedAt IS NOT NULL AND likedAt > 0
                FROM Song 
                WHERE id = :songId
            )
            , 0
        ) 
    """)
    fun isLiked( songId: String ): Flow<Boolean>

    @Query("SELECT COALESCE(likedAt IS NOT NULL AND likedAt > 0, 0) FROM Song WHERE id = :songId")
    suspend fun isLikedDirect( songId: String ): Boolean

    @Query("SELECT id FROM Song WHERE id IN (:songIds) AND likedAt > 0")
    suspend fun findLikedIdsDirect( songIds: List<String> ): List<String>

    /** Like tri-state of [songId]: `true` = liked, `false` = disliked, `null` = unset (neutral). */
    @Query("""
        SELECT 
            CASE 
                WHEN likedAt > 0 THEN 1
                WHEN likedAt = -1 THEN 0
                ELSE NULL 
            END 
        FROM Song 
        WHERE id = :songId
    """)
    fun likeState( songId: String ): Flow<Boolean?>

    /** Like states of [songIds] in one query (the rows convert to a Map). */
    @Query("""
        SELECT id as songId,
            CASE 
                WHEN likedAt > 0 THEN 1
                WHEN likedAt = -1 THEN 0
                ELSE NULL 
            END as likeState
        FROM Song 
        WHERE id IN (:songIds)
    """)
    fun getLikeStatesForSongs( songIds: List<String> ): Flow<List<SongLikeState>>

    /**
     * Rotates [Song.likedAt]: neutral (NULL) → liked (timestamp) → disliked (-1)
     * → neutral (NULL). Returns the number of rows affected.
     */
    @Query("""
        UPDATE Song  
        SET likedAt = 
            CASE  
                WHEN likedAt IS NULL THEN strftime('%s', 'now') * 1000
                WHEN likedAt > 0 THEN -1
                ELSE NULL
            END  
        WHERE id = :songId
    """)
    suspend fun rotateLikeState( songId: String ): Int

    @Query("UPDATE Song SET likedAt = NULL WHERE likedAt = -1")
    suspend fun clearAllDisliked(): Int

    /**
     * Toggles dislike: neutral (NULL) or liked (timestamp) → disliked (-1),
     * disliked (-1) → neutral (NULL). Returns the number of rows affected.
     */
    @Query("""
        UPDATE Song
        SET likedAt = 
            CASE
                WHEN likedAt = -1 THEN NULL
                WHEN likedAt IS NULL THEN -1
                ELSE -1
            END
        WHERE id = :songId
    """)
    suspend fun toggleDislike( songId: String ): Int

    /** Toggles like: liked (non-null) → `null`, not liked → current time. Returns the number of rows affected. */
    @Query("""
        UPDATE Song
        SET likedAt = 
            CASE 
                WHEN likedAt IS NOT NULL THEN NULL
                ELSE strftime('%s', 'now') * 1000
            END
        WHERE id = :songId
    """)
    suspend fun toggleLike( songId: String ): Int

    /** Sets [Song.likedAt] from [likeState] (`true` = like, `false` = dislike, `null` = neutral). */
    @Query("""
        UPDATE Song
        SET likedAt = 
            CASE
                WHEN :likeState = 0 THEN -1
                WHEN :likeState = 1 THEN strftime('%s', 'now') * 1000 
                ELSE :likeState 
            END
        WHERE id = :songId
    """)
    suspend fun likeState( songId: String, likeState: Boolean? ): Int

    /**
     * Set [Song.likedAt] to a specific timestamp value.
     *
     * @param songId of song to be updated
     * @param likedAt timestamp to set (in milliseconds)
     * @return number of rows affected
     */
    @Query("UPDATE Song SET likedAt = :likedAt WHERE id = :songId")
    suspend fun likeState( songId: String, likedAt: Long ): Int

    /**
     * Get the raw [Song.likedAt] value for a song.
     *
     * @param songId of song to query
     * @return the raw likedAt value (Long?)
     */
    @Query("SELECT likedAt FROM Song WHERE id = :songId")
    suspend fun getLikedAt(songId: String): Long?

    /**
     * @param songId identifier of [Song]
     * @param title new name of this song
     *
     * @return number of albums affected by this operation
     */
    @Query("UPDATE Song SET title = :title WHERE id = :songId")
    suspend fun updateTitle( songId: String, title: String ): Int

    @Query("UPDATE Song SET thumbnailUrl = :thumbnailUrl WHERE id = :songId")
    suspend fun updateCover( songId: String, thumbnailUrl: String ): Int

    @Query("""
        UPDATE Song
        SET thumbnailUrl = :thumbnailUrl
        WHERE id IN (
            SELECT songId 
            FROM SongAlbumMap 
            WHERE albumId = :albumId
        )
    """)
    suspend fun updateCoverForAlbum( albumId: String, thumbnailUrl: String ): Int

    @Query("UPDATE Song SET position = :position WHERE id = :songId")
    suspend fun updatePosition( songId: String, position: Int ): Int

    /**
     * @param songId identifier of [Song]
     * @param artistsText artists to display
     *
     * @return number of albums affected by this operation
     */
    @Query("UPDATE Song SET artistsText = :artistsText WHERE id = :songId")
    suspend fun updateArtists( songId: String, artistsText: String ): Int

    /** Set [Song.totalPlayTimeMs] to [value] (hard set when [isIncrement] is `false`, otherwise sum with the existing value). @param songId song to update @return number of rows affected */
    @Query(
        """
        UPDATE Song 
        SET totalPlayTimeMs = 
            CASE
                WHEN :isIncrement = 0 THEN :value
                ELSE totalPlayTimeMs + :value
            END
        WHERE id = :songId
    """
    )
    suspend fun updateTotalPlayTime( songId: String, value: Long, isIncrement: Boolean = false ): Int

    //<editor-fold defaultstate="collapsed" desc="Sort all">
    @Query("""
        SELECT DISTINCT * 
        FROM Song 
        WHERE CASE WHEN :dislikedOnly THEN likedAt < 0 ELSE totalPlayTimeMs >= :excludeHidden END
        ORDER BY 
            CASE WHEN position = -1 THEN 1 ELSE 0 END ASC,
            CASE WHEN position = -1 THEN ROWID ELSE position END ASC
        LIMIT :limit
    """)
    fun sortAllByPosition(limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false, dislikedOnly: Boolean = false): Flow<List<Song>>

    fun sortAllByPlayTime(limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false, dislikedOnly: Boolean = false): Flow<List<Song>> =
        all(limit, excludeHidden, dislikedOnly).map { list ->
            list.sortedBy( Song::totalPlayTimeMs )
        }

    fun sortAllByRelativePlayTime(limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false, dislikedOnly: Boolean = false): Flow<List<Song>> =
        all(limit, excludeHidden, dislikedOnly).map { list ->
            list.sortedBy( Song::relativePlayTime )
        }

    @Query("""
        SELECT DISTINCT S.id, S.title, S.artistsText, S.durationText, S.thumbnailUrl, S.likedAt, S.totalPlayTimeMs, S.position, S.isYoutubeSong, COUNT(E.songId) as playCount
        FROM Song S
        LEFT JOIN Event E ON E.songId = S.id
        WHERE CASE WHEN :dislikedOnly THEN S.likedAt < 0 ELSE S.totalPlayTimeMs >= :excludeHidden END
        GROUP BY S.id
        ORDER BY playCount ASC
        LIMIT :limit
    """)
    fun sortAllByPlayCount(limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false, dislikedOnly: Boolean = false): Flow<List<Song>>

    fun sortAllByTitle(limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false, dislikedOnly: Boolean = false): Flow<List<Song>> =
        all(limit, excludeHidden, dislikedOnly).map { list ->
            list.sortedBy( Song::cleanTitle )
        }

    @Query("""
        SELECT DISTINCT S.* 
        FROM Song S 
        LEFT JOIN Event E ON E.songId = S.id 
        WHERE CASE WHEN :dislikedOnly THEN S.likedAt < 0 ELSE S.totalPlayTimeMs >= :excludeHidden END
        ORDER BY E.timestamp ASC
        LIMIT :limit
    """)
    fun sortAllByDatePlayed(limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false, dislikedOnly: Boolean = false): Flow<List<Song>>

    fun sortAllByLikedAt(limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false, dislikedOnly: Boolean = false): Flow<List<Song>> =
        all(limit, excludeHidden, dislikedOnly).map { list ->
            list.sortedBy( Song::likedAt )
        }

    fun sortAllByArtist(limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false, dislikedOnly: Boolean = false): Flow<List<Song>> =
        all(limit, excludeHidden, dislikedOnly).map { list ->
            list.sortedBy( Song::cleanArtistsText )
        }

    fun sortAllByDuration(limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false, dislikedOnly: Boolean = false): Flow<List<Song>> =
        all(limit, excludeHidden, dislikedOnly).map { list ->
            list.sortedBy {
                durationToMillis( it.durationText ?: "0:0" )
            }
        }

    @Query("""
        SELECT DISTINCT S.*
        FROM Song S
        LEFT JOIN SongAlbumMap sam ON sam.songId = S.id
        LEFT JOIN Album A ON A.id = sam.albumId
        WHERE CASE WHEN :dislikedOnly THEN S.likedAt < 0 ELSE S.totalPlayTimeMs >= :excludeHidden END
        ORDER BY 
            CASE 
                WHEN A.title LIKE '$MODIFIED_PREFIX%' THEN SUBSTR(A.title, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE A.title
            END
        LIMIT :limit
    """)
    fun sortAllByAlbumName(limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false, dislikedOnly: Boolean = false): Flow<List<Song>>

    /**
     * All songs sorted by [sortBy] (a [SongSortBy] property) and [sortOrder]
     * (alphabetical / numerical direction); [excludeHidden] drops user-hidden
     * songs (their `totalPlayTimeMs` is -1).
     *
     * @param sortBy which song property to sort by
     * @param sortOrder the sort direction
     * @param excludeHidden whether to exclude hidden songs
     * @param dislikedOnly use the disliked collection, including hidden songs, as the source
     */
    fun sortAll(
        sortBy: SongSortBy,
        sortOrder: SortOrder,
        limit: Int = Int.MAX_VALUE,
        excludeHidden: Boolean = false,
        dislikedOnly: Boolean = false,
    ): Flow<List<Song>> = when( sortBy ){
        SongSortBy.PlayTime         -> sortAllByPlayTime(limit, excludeHidden, dislikedOnly)
        SongSortBy.RelativePlayTime -> sortAllByRelativePlayTime(limit, excludeHidden, dislikedOnly)
        SongSortBy.PlayCount        -> sortAllByPlayCount(limit, excludeHidden, dislikedOnly)
        SongSortBy.Title            -> sortAllByTitle(limit, excludeHidden, dislikedOnly)
        SongSortBy.DateAdded        -> all(limit, excludeHidden, dislikedOnly) // Already sorted by ROWID
        SongSortBy.DatePlayed       -> sortAllByDatePlayed(limit, excludeHidden, dislikedOnly)
        SongSortBy.DateLiked        -> sortAllByLikedAt(limit, excludeHidden, dislikedOnly)
        SongSortBy.Artist           -> sortAllByArtist(limit, excludeHidden, dislikedOnly)
        SongSortBy.Duration         -> sortAllByDuration(limit, excludeHidden, dislikedOnly)
        SongSortBy.AlbumName        -> sortAllByAlbumName(limit, excludeHidden, dislikedOnly)
        SongSortBy.Custom           -> sortAllByPosition(limit, excludeHidden, dislikedOnly)
        SongSortBy.OnDevice, SongSortBy.Downloaded       -> sortAllByTitle(limit, excludeHidden, dislikedOnly) // Handled in-memory
    }.map( sortOrder::applyTo )
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Sort favorites">
    @Query("""
        SELECT DISTINCT * 
        FROM Song 
        WHERE likedAt IS NOT NULL AND likedAt > 0
        ORDER BY 
            CASE WHEN position = -1 THEN 1 ELSE 0 END ASC,
            CASE WHEN position = -1 THEN ROWID ELSE position END ASC
        LIMIT :limit
    """)
    fun sortFavoritesByPosition( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    fun sortFavoritesByArtist( limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allFavorites( limit ).map { list ->
            list.sortedBy( Song::cleanArtistsText )
        }

    fun sortFavoritesByPlayTime( limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allFavorites( limit ).map { list ->
            list.sortedBy( Song::totalPlayTimeMs )
        }

    fun sortFavoritesByRelativePlayTime( limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allFavorites( limit ).map { list ->
            list.sortedBy( Song::relativePlayTime )
        }

    @Query("""
        SELECT DISTINCT S.id, S.title, S.artistsText, S.durationText, S.thumbnailUrl, S.likedAt, S.totalPlayTimeMs, S.position, S.isYoutubeSong, COUNT(E.songId) as playCount
        FROM Song S
        LEFT JOIN Event E ON E.songId = S.id
        WHERE S.likedAt IS NOT NULL AND S.likedAt > 0
        GROUP BY S.id
        ORDER BY playCount ASC
        LIMIT :limit
    """)
    fun sortFavoritesByPlayCount( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    fun sortFavoritesByTitle( limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allFavorites( limit ).map { list ->
            list.sortedBy( Song::cleanTitle )
        }

    fun sortFavoritesByLikedAt( limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allFavorites( limit ).map { list ->
            list.sortedBy( Song::likedAt )
        }

    @Query("""
        SELECT DISTINCT S.* 
        FROM Song S 
        LEFT JOIN Event E ON E.songId = S.id 
        WHERE likedAt IS NOT NULL AND likedAt > 0
        ORDER BY E.timestamp ASC
        LIMIT :limit
    """)
    fun sortFavoritesByDatePlayed( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    fun sortFavoritesByDuration( limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allFavorites( limit ).map { list ->
            list.sortedBy {
                durationToMillis( it.durationText ?: "0:0" )
            }
        }

    @Query("""
        SELECT DISTINCT S.*
        FROM Song S
        LEFT JOIN SongAlbumMap sam ON sam.songId = S.id
        LEFT JOIN Album A ON A.id = sam.albumId
        WHERE likedAt IS NOT NULL AND likedAt > 0
        ORDER BY 
            CASE 
                WHEN A.title LIKE '$MODIFIED_PREFIX%' THEN SUBSTR(A.title, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE A.title
            END
        LIMIT :limit
    """)
    fun sortFavoritesByAlbumName( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * All favorite songs sorted by [sortBy] (a [SongSortBy] property) and
     * [sortOrder] (alphabetical / numerical direction), trimmed to [limit].
     *
     * @param sortBy which song property to sort by
     * @param sortOrder the sort direction
     * @param limit stop the query once the result count reaches this number
     */
    fun sortFavorites(
        sortBy: SongSortBy,
        sortOrder: SortOrder,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Song>> = when( sortBy ) {
        SongSortBy.PlayTime         -> sortFavoritesByPlayTime()
        SongSortBy.RelativePlayTime -> sortFavoritesByRelativePlayTime()
        SongSortBy.PlayCount        -> sortFavoritesByPlayCount()
        SongSortBy.Title            -> sortFavoritesByTitle()
        SongSortBy.DateAdded        -> allFavorites()      // Already sorted by ROWID
        SongSortBy.DatePlayed       -> sortFavoritesByDatePlayed()
        SongSortBy.DateLiked        -> sortFavoritesByLikedAt()
        SongSortBy.Artist           -> sortFavoritesByArtist()
        SongSortBy.Duration         -> sortFavoritesByDuration()
        SongSortBy.AlbumName        -> sortFavoritesByAlbumName()
        SongSortBy.Custom           -> sortFavoritesByPosition( limit )
        SongSortBy.OnDevice, SongSortBy.Downloaded       -> sortFavoritesByTitle() // Handled in-memory
    }.map( sortOrder::applyTo ).take( limit )
    //</editor-fold>

    @Transaction
    @Query("""
        SELECT S.*
        FROM Song S
        JOIN SongArtistMap ON S.id = SongArtistMap.songId
        WHERE SongArtistMap.artistId = :artistId
        ORDER BY S.ROWID DESC
    """)
    fun artistSongs(artistId: String): Flow<List<Song>>

    @Query("DELETE FROM Song WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>): Int

    @Query("DELETE FROM Song")
    suspend fun deleteAll(): Int

    @Query("UPDATE Song SET likedAt = NULL WHERE id IN (:ids)")
    suspend fun unlikeByIds(ids: List<String>): Int

    @Query("UPDATE Song SET likedAt = NULL WHERE likedAt IS NOT NULL AND likedAt > 0")
    suspend fun unlikeAll(): Int

    @Query("UPDATE Song SET totalPlayTimeMs = 0 WHERE id IN (:ids)")
    suspend fun resetPlayTimeByIds(ids: List<String>): Int
}

