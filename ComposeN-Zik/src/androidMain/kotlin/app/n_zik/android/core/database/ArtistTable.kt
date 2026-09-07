package app.n_zik.android.core.database

import android.database.SQLException
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Update
import androidx.room.Upsert
import app.it.fast4x.rimusic.enums.ArtistSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

@Dao
@RewriteQueriesToDropUnusedColumns
interface ArtistTable {

    /**
     * @return all artists from this table that are followed by user
     */
    @Query("""
        SELECT DISTINCT * 
        FROM Artist
        WHERE bookmarkedAt IS NOT NULL
        ORDER BY ROWID 
        LIMIT :limit
    """)
    fun allFollowing( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    /**
     * @return artists that have their songs mapped to at least 1 playlist
     */
    @Query("""
        SELECT DISTINCT A.*
        FROM Artist A
        JOIN SongArtistMap sam ON sam.artistId = A.id
        JOIN Song S ON S.id = sam.songId
        WHERE S.totalPlayTimeMs >= 1 OR S.likedAt IS NOT NULL OR S.id LIKE 'local:%'
        ORDER BY A.ROWID
        LIMIT :limit
    """)
    fun allInLibrary( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    /**
     * @return all songs of following artists
     */
    @Query("""
        SELECT DISTINCT S.*
        FROM SongArtistMap sam
        JOIN Artist A ON A.id = sam.artistId
        JOIN Song S ON S.id = sam.songId
        WHERE A.bookmarkedAt IS NOT NULL
        ORDER BY S.ROWID
        LIMIT :limit
    """)
    fun allSongsInFollowing( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return all songs of artists in library (not just followed)
     */
    @Query("""
        SELECT DISTINCT S.*
        FROM SongArtistMap sam
        JOIN Artist A ON A.id = sam.artistId
        JOIN Song S ON S.id = sam.songId
        WHERE A.id IN (
            SELECT DISTINCT A2.id
            FROM Artist A2
            JOIN SongArtistMap sam2 ON sam2.artistId = A2.id
            JOIN Song S2 ON S2.id = sam2.songId
            WHERE S2.totalPlayTimeMs >= 1 OR S2.likedAt IS NOT NULL OR S2.id LIKE 'local:%'
        )
        ORDER BY S.ROWID
        LIMIT :limit
    """)
    fun allSongsInLibrary( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @param artistId of artist to look for
     * @return [Artist] that has [Artist.id] matches [artistId]
     */
    @Query("SELECT DISTINCT * FROM Artist WHERE id = :artistId")
    fun findById( artistId: String ): Flow<Artist?>

    @Query("SELECT DISTINCT * FROM Artist WHERE id = :artistId")
    fun findByIdDirect( artistId: String ): Artist?

    @Query("""
        SELECT DISTINCT Artist.*
        FROM SongArtistMap
        JOIN Artist ON id = artistId
        WHERE songId = :songId
    """)
    fun findBySongId( songId: String ): Flow<List<Artist>>

    @Query("SELECT DISTINCT Artist.* FROM SongArtistMap JOIN Artist ON id = artistId WHERE songId = :songId")
    fun findBySongIdDirect( songId: String ): List<Artist>

    /**
     * @param name artist name to search for (case-insensitive)
     * @return first [Artist] that matches the name
     */
    @Query("SELECT DISTINCT * FROM Artist WHERE name = :name COLLATE NOCASE LIMIT 1")
    fun findByName( name: String ): Flow<Artist?>

    @Query("SELECT DISTINCT * FROM Artist WHERE name = :name COLLATE NOCASE LIMIT 1")
    fun findByNameDirect( name: String ): Artist?

    /**
     * @return whether [Artist] with id [artistId] is followed by user,
     * if artist doesn't exist, return default value - `false`
     */
    @Query("""
        SELECT COALESCE(
            (
                SELECT 1 
                FROM Artist 
                WHERE id = :artistId 
                AND bookmarkedAt IS NOT NULL 
            ),
            0
        )
    """)
    fun isFollowing( artistId: String ): Flow<Boolean>

    /**
     * Attempt to write [artist] into database.
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
     * @param artist data intended to insert in to database
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore( artist: Artist )

    /**
     * Attempt to write the list of [Artist] to database.
     *
     * If record exist (determined by its primary key),
     * it'll simply be ignored and the transaction continues.
     *
     * @param artists list of [Artist] to insert to database
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore( artists: List<Artist> )

    /**
     * Attempt to write [artist] into database.
     *
     * If [artist] exist (determined by its primary key),
     * existing record's columns will be replaced
     * by provided [artist]' data.
     *
     * @param artist data intended to insert in to database
     */
    @Upsert
    fun upsert( artist: Artist )

    /**
     * Attempt to write the list of [Artist] to database.
     *
     * If record exist (determined by its primary key),
     * existing record's columns will be replaced
     * by provided data.
     *
     * @param artists list of [Artist] to insert to database
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun upsert( artists: List<Artist> )

    /**
     * Attempt to replace a record's data with provided [artist].
     *
     * ### Standalone use
     *
     * When error occurs and [SQLException] is thrown,
     * the process is cancel and passes exception to caller.
     *
     * ### Transaction use
     *
     * When error occurs and [SQLException] is thrown,
     * **the entire transaction rolls back** and passes exception to caller.
     *
     *
     * @param artist intended to update
     *
     * @throws SQLException when there's a conflict
     */
    @Update
    @Throws(SQLException::class)
    fun update( artist: Artist )

    /**
     * There are 2 possible actions.
     *
     * ### If artist IS followed
     *
     * This will remove [Artist.bookmarkedAt] timestamp (replace with NULL)
     *
     * ## If artist IS NOT followed
     *
     * It will assign [Artist.bookmarkedAt] with current time in millis
     *
     * @param artistId artist identifier to update its [Artist.bookmarkedAt]
     *
     * @return number of artists updated by this operation
     */
    @Query("""
        UPDATE Artist
        SET bookmarkedAt = CASE
            WHEN bookmarkedAt IS NULL THEN strftime('%s', 'now') * 1000
            ELSE NULL
        END,
        lastFetch = CASE
            WHEN bookmarkedAt IS NULL THEN NULL
            ELSE lastFetch
        END
        WHERE id = :artistId
    """)
    fun toggleFollow( artistId: String ): Int

    /**
     * Rotate like state for an artist: neutral → followed → disliked → neutral
     * - neutral (NULL) → followed (bookmarkedAt = timestamp) = "follow"
     * - followed (bookmarkedAt > 0) → disliked (dislikedAt = timestamp) = "dislike"
     * - disliked (dislikedAt > 0) → neutral (NULL) = "remove dislike"
     *
     * @param artistId of artist to be updated
     * @return number of rows affected
     */
    @Query("""
        UPDATE Artist
        SET
            bookmarkedAt = CASE
                WHEN bookmarkedAt IS NULL AND dislikedAt IS NULL THEN strftime('%s', 'now') * 1000
                WHEN bookmarkedAt IS NOT NULL THEN NULL
                ELSE bookmarkedAt
            END,
            dislikedAt = CASE
                WHEN bookmarkedAt IS NOT NULL THEN strftime('%s', 'now') * 1000
                WHEN dislikedAt IS NOT NULL THEN NULL
                ELSE dislikedAt
            END
        WHERE id = :artistId
    """)
    fun rotateLikeState( artistId: String ): Int

    /**
     * @return like state of artist: true = followed, false = disliked, null = neutral
     */
    @Query("""
        SELECT CASE
            WHEN dislikedAt IS NOT NULL THEN 0
            WHEN bookmarkedAt IS NOT NULL THEN 1
            ELSE NULL
        END
        FROM Artist
        WHERE id = :artistId
    """)
    fun likeState( artistId: String ): Flow<Boolean?>

    /**
     * Toggle dislike state for an artist.
     *
     * - If neutral (null) -> set to disliked (current timestamp)
     * - If disliked -> set to neutral (null)
     *
     * @param artistId of artist to be updated
     * @return number of rows affected
     */
    @Query("""
        UPDATE Artist
        SET dislikedAt =
            CASE
                WHEN dislikedAt IS NULL THEN strftime('%s', 'now') * 1000
                ELSE NULL
            END
        WHERE id = :artistId
    """)
    fun toggleDislike( artistId: String ): Int

    /**
     * @return whether [Artist] with id [artistId] is disliked
     */
    @Query("""
        SELECT COALESCE(
            (
                SELECT 1
                FROM Artist
                WHERE id = :artistId
                AND dislikedAt IS NOT NULL
            ),
            0
        )
    """)
    fun isDisliked( artistId: String ): Flow<Boolean>

    /**
     * @return all disliked artists
     */
    @Query("""
        SELECT DISTINCT *
        FROM Artist
        WHERE dislikedAt IS NOT NULL
        ORDER BY dislikedAt DESC
        LIMIT :limit
    """)
    fun allDisliked( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    @Query("UPDATE Artist SET position = :position WHERE id = :artistId")
    fun updatePosition( artistId: String, position: Int ): Int

    /**
     * Delete all artists that have no songs mapped to them
     * (orphaned artists after songs were deleted)
     *
     * @return number of rows affected by this operation
     */
    @Query("""
        DELETE FROM Artist 
        WHERE id NOT IN (
            SELECT DISTINCT artistId
            FROM SongArtistMap
        )
        AND bookmarkedAt IS NULL
    """)
    fun deleteOrphaned(): Int

    @Query("DELETE FROM Artist WHERE id = :id")
    fun deleteById(id: String): Int

    //<editor-fold defaultstate="collapsed" desc="Sort all">
    @Query("""
        SELECT DISTINCT * 
        FROM Artist
        WHERE bookmarkedAt IS NOT NULL
        ORDER BY 
            CASE WHEN position = -1 THEN 1 ELSE 0 END ASC,
            CASE WHEN position = -1 THEN ROWID ELSE position END ASC
        LIMIT :limit
    """)
    fun sortFollowingByPosition( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>
    fun sortFollowingByName( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>> =
        allFollowing( limit ).map { list ->
            list.sortedBy( Artist::cleanName )
        }

    @Query("""
        SELECT DISTINCT A.*
        FROM Artist A
        JOIN SongArtistMap SAM ON SAM.artistId = A.id
        JOIN Event E ON E.songId = SAM.songId
        WHERE A.bookmarkedAt IS NOT NULL
        GROUP BY A.id
        ORDER BY SUM(E.playtime) ASC
        LIMIT :limit
    """)
    fun sortFollowingByListeningTime( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    @Query("""
        SELECT DISTINCT A.*
        FROM Artist A
        JOIN SongArtistMap SAM ON SAM.artistId = A.id
        JOIN Event E ON E.songId = SAM.songId
        WHERE A.bookmarkedAt IS NOT NULL
        GROUP BY A.id
        ORDER BY COUNT(E.id) ASC
        LIMIT :limit
    """)
    fun sortFollowingByPlayCount( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    /**
     * Fetch all following artists and sort
     * them according to [sortBy] and [sortOrder].
     *
     * [sortBy] sorts all based on each artist's property
     * such as [ArtistSortBy.Name], [ArtistSortBy.DateAdded], etc.
     * While [sortOrder] arranges order of sorted songs
     * to follow alphabetical order A to Z, or numerical order 0 to 9, etc.
     *
     * @param sortBy which artist's property is used to sort
     * @param sortOrder what order should results be in
     * @param limit stop query once number of results reaches this number
     *
     * @return a **SORTED** list of [Artist]'s that are continuously
     * updated to reflect changes within the database - wrapped by [Flow]
     *
     * @see ArtistSortBy
     * @see SortOrder
     */
    fun sortFollowing(
        sortBy: ArtistSortBy,
        sortOrder: SortOrder,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Artist>> = when( sortBy ) {
        ArtistSortBy.Name       -> sortFollowingByName()
        ArtistSortBy.DateAdded  -> allFollowing()
        ArtistSortBy.PlayCount -> sortFollowingByPlayCount( limit )
        ArtistSortBy.ListeningTime -> sortFollowingByListeningTime( limit )
        ArtistSortBy.Custom    -> sortFollowingByPosition( limit )
    }.map( sortOrder::applyTo ).take( limit )
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Sort artists in library">
    @Query("""
        SELECT DISTINCT A.*
        FROM Artist A
        JOIN SongArtistMap sam ON sam.artistId = A.id
        JOIN Song S ON S.id = sam.songId
        WHERE S.totalPlayTimeMs >= 1 OR S.likedAt IS NOT NULL
        ORDER BY 
            CASE WHEN A.position = -1 THEN A.ROWID ELSE A.position END ASC
        LIMIT :limit
    """)
    fun sortInLibraryByPosition( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    fun sortInLibraryByName( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>> =
        allInLibrary( limit ).map { list ->
            list.sortedBy( Artist::cleanName )
        }

    @Query("""
        SELECT DISTINCT A.*
        FROM Artist A
        JOIN SongArtistMap sam ON sam.artistId = A.id
        JOIN Song S ON S.id = sam.songId
        JOIN Event E ON E.songId = sam.songId
        WHERE S.totalPlayTimeMs >= 1 OR S.likedAt IS NOT NULL
        GROUP BY A.id
        ORDER BY SUM(E.playtime) ASC
        LIMIT :limit
    """)
    fun sortInLibraryByListeningTime( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    @Query("""
        SELECT DISTINCT A.*
        FROM Artist A
        JOIN SongArtistMap sam ON sam.artistId = A.id
        JOIN Song S ON S.id = sam.songId
        JOIN Event E ON E.songId = sam.songId
        WHERE S.totalPlayTimeMs >= 1 OR S.likedAt IS NOT NULL
        GROUP BY A.id
        ORDER BY COUNT(E.id) ASC
        LIMIT :limit
    """)
    fun sortInLibraryByPlayCount( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    /**
     * Fetch all artists that have their songs mapped to
     * at least 1 playlist in library and sort
     * them according to [sortBy] and [sortOrder].
     *
     * [sortBy] sorts all based on each artist's property
     * such as [ArtistSortBy.Name], [ArtistSortBy.DateAdded], etc.
     * While [sortOrder] arranges order of sorted songs
     * to follow alphabetical order A to Z, or numerical order 0 to 9, etc.
     *
     * @param sortBy which artist's property is used to sort
     * @param sortOrder what order should results be in
     * @param limit stop query once number of results reaches this number
     *
     * @return a **SORTED** list of [Artist]'s that are continuously
     * updated to reflect changes within the database - wrapped by [Flow]
     *
     * @see ArtistSortBy
     * @see SortOrder
     */
    fun sortInLibrary(
        sortBy: ArtistSortBy,
        sortOrder: SortOrder,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Artist>> = when( sortBy ) {
        ArtistSortBy.Name       -> sortInLibraryByName()
        ArtistSortBy.DateAdded  -> allInLibrary()     // Already sorted by ROWID
        ArtistSortBy.PlayCount -> sortInLibraryByPlayCount( limit )
        ArtistSortBy.ListeningTime -> sortInLibraryByListeningTime( limit )
        ArtistSortBy.Custom    -> sortInLibraryByPosition( limit )
    }.map( sortOrder::applyTo ).take( limit )
    //</editor-fold>
}

