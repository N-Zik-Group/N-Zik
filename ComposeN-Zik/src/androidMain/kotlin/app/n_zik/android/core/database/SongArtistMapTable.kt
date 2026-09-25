package app.n_zik.android.core.database

import android.database.SQLException
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongArtistMap
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface SongArtistMapTable {

    /**
     * Attempt to write [songArtistMap] into database.
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
     * @param songArtistMap data intended to insert in to database
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore( songArtistMap: SongArtistMap )

    /**
     * Attempt to write list of [SongArtistMap] into database.
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
     * @param songArtistMaps data intended to insert in to database
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore( songArtistMaps: List<SongArtistMap> )

    /**
     * @param artistId of artist to look for
     * @param limit number of results cannot go over this value
     *
     * @return all [Song]s that were mapped to artist has [Artist.id] matches [artistId]
     */
    @Query("""
        SELECT DISTINCT Song.*
        FROM SongArtistMap sam 
        JOIN Song ON Song.id = sam.songId
        WHERE sam.artistId = :artistId
        ORDER BY Song.ROWID
        LIMIT :limit
    """)
    fun allSongsBy( artistId: String, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return all [Artist]s featured in this song
     */
    @Query("""
        SELECT DISTINCT A.*
        FROM Artist A
        JOIN SongArtistMap SAM ON SAM.artistId = A.id
        WHERE SAM.songId = :songId
        ORDER BY A.ROWID
        LIMIT :limit
    """)
    fun findArtistsOf( songId: String, limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    /**
     * Delete all mappings where songs aren't exist in `Song` table
     *
     * @return number of rows affected by this operation
     */
    @Query("""
        DELETE FROM SongArtistMap 
        WHERE songId NOT IN (
            SELECT DISTINCT id
            FROM Song
        )
    """)
    fun clearGhostMaps(): Int

    @Query("UPDATE SongArtistMap SET songId = :newId WHERE songId = :oldId")
    fun updateSongId(oldId: String, newId: String)

    @Query("UPDATE SongArtistMap SET artistId = :newId WHERE artistId = :oldId")
    fun updateArtistId(oldId: String, newId: String)

    /**
     * Delete all mappings for a specific song
     *
     * @param songId the song ID to delete mappings for
     * @return number of rows affected by this operation
     */
    @Query("DELETE FROM SongArtistMap WHERE songId = :songId")
    fun deleteBySongId(songId: String): Int

    /**
     * @return every (songId, artistId) pair in this table, used by data
     * cleanups that must scan all mappings at once
     */
    @Query("SELECT songId, artistId FROM SongArtistMap")
    fun allPairsDirect(): List<SongArtistMap>

    /**
     * Delete a single mapping
     *
     * @param songId song side of the pair
     * @param artistId artist side of the pair
     * @return number of rows affected by this operation
     */
    @Query("DELETE FROM SongArtistMap WHERE songId = :songId AND artistId = :artistId")
    fun deletePairDirect(songId: String, artistId: String): Int
}

