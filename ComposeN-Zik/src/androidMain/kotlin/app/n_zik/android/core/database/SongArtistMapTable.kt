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

    /** Inserts [songArtistMap], silently skipping it when the pair already exists (onConflict IGNORE). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore( songArtistMap: SongArtistMap )

    /** Inserts [songArtistMaps], silently skipping pairs that already exist (onConflict IGNORE). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore( songArtistMaps: List<SongArtistMap> )

    /** All [Song]s mapped to the artist whose [Artist.id] equals [artistId]. */
    @Query("""
        SELECT DISTINCT Song.*
        FROM SongArtistMap sam 
        JOIN Song ON Song.id = sam.songId
        WHERE sam.artistId = :artistId
        ORDER BY Song.ROWID
        LIMIT :limit
    """)
    fun allSongsBy( artistId: String, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /** Direct (non-Flow) mirror of [allSongsBy]: Flows are unusable inside a synchronous transaction, so sweeps and propagations that run on the transaction executor query the same data synchronously. */
    @Query("""
        SELECT DISTINCT Song.*
        FROM SongArtistMap sam
        JOIN Song ON Song.id = sam.songId
        WHERE sam.artistId = :artistId
        ORDER BY Song.ROWID
        LIMIT :limit
    """)
    fun allSongsByDirect( artistId: String, limit: Int = Int.MAX_VALUE ): List<Song>

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

    /** Direct (non-Flow) mirror of [findArtistsOf]: Flows are unusable inside a synchronous transaction, so sweeps and propagations that run on the transaction executor query the same data synchronously. */
    @Query("""
        SELECT DISTINCT A.*
        FROM Artist A
        JOIN SongArtistMap SAM ON SAM.artistId = A.id
        WHERE SAM.songId = :songId
        ORDER BY A.ROWID
        LIMIT :limit
    """)
    fun findArtistsOfDirect( songId: String, limit: Int = Int.MAX_VALUE ): List<Artist>

    /** Deletes all mappings whose song no longer exists in the `Song` table. */
    @Query("""
        DELETE FROM SongArtistMap 
        WHERE songId NOT IN (
            SELECT DISTINCT id
            FROM Song
        )
    """)
    fun clearGhostMaps(): Int

    /**
     * Deletes the source song's links to every artist that the target song already
     * holds, so a following [updateSongId] cannot violate the (songId, artistId)
     * primary key for artists already linked to the target.
     *
     * Must be called inside the same transaction as [updateSongId], before it, when
     * merging the source song into an existing target song. The target's own rows are
     * kept, so the merge yields the union of both songs' artist mappings, with the
     * target's rows canonical on a conflict (no pair lost, no duplicate created,
     * no orphaned source row left behind).
     */
    @Query("""
        DELETE FROM SongArtistMap
        WHERE songId = :oldId
        AND artistId IN ( SELECT artistId FROM SongArtistMap WHERE songId = :newId )
    """)
    fun clearConflictingPairs(oldId: String, newId: String): Int

    @Query("UPDATE SongArtistMap SET songId = :newId WHERE songId = :oldId")
    fun updateSongId(oldId: String, newId: String)

    @Query("UPDATE SongArtistMap SET artistId = :newId WHERE artistId = :oldId")
    fun updateArtistId(oldId: String, newId: String)

    /** Delete the (song, :newId) links of every song that also holds a (song, :oldId) link, so a following [updateArtistId] cannot violate the (songId, artistId) primary key for songs already linked to the target. */
    @Query("""
        DELETE FROM SongArtistMap
        WHERE artistId = :newId
        AND songId IN ( SELECT songId FROM SongArtistMap WHERE artistId = :oldId )
    """)
    fun dropLinksAlreadyOn(oldId: String, newId: String): Int

    /** Delete all mappings for [songId]. */
    @Query("DELETE FROM SongArtistMap WHERE songId = :songId")
    fun deleteBySongId(songId: String): Int

    /**
     * @return every (songId, artistId) pair in this table, used by data
     * cleanups that must scan all mappings at once
     */
    @Query("SELECT songId, artistId FROM SongArtistMap")
    fun allPairsDirect(): List<SongArtistMap>

    /**
     * @param songId the song side of the pair
     *
     * @return every (songId, artistId) pair for this song, used by reconcile
     * logging that names the stale links right before they are deleted
     */
    @Query("SELECT songId, artistId FROM SongArtistMap WHERE songId = :songId")
    fun pairsBySongIdDirect(songId: String): List<SongArtistMap>

    /** Delete the single mapping between [songId] and [artistId]. */
    @Query("DELETE FROM SongArtistMap WHERE songId = :songId AND artistId = :artistId")
    fun deletePairDirect(songId: String, artistId: String): Int

    /**
     * Read-only, for the PC bridge (contract §1.1 `Artist.trackCount`).
     *
     * @return the number of songs mapped to each artist that has at least one
     */
    @Query("""
        SELECT sam.artistId AS id, COUNT(DISTINCT sam.songId) AS count
        FROM SongArtistMap sam
        JOIN Song S ON S.id = sam.songId
        GROUP BY sam.artistId
    """)
    fun songCountsDirect(): List<SongCount>
}

