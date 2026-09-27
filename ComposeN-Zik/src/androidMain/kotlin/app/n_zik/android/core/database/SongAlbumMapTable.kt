package app.n_zik.android.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Upsert
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongAlbumMap
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface SongAlbumMapTable {

    /**
     * Attempt to write the list of [SongAlbumMap] to database.
     *
     * If record exist (determined by its primary key),
     * existing record's columns will be replaced
     * by provided data.
     *
     * @param songAlbumMap list of [SongAlbumMap] to insert to database
     */
    @Upsert
    fun upsert( songAlbumMap: List<SongAlbumMap> )

    /**
     * Remove all songs belong to album with id [albumId]
     *
     * @param albumId album to have its songs wiped
     *
     * @return number of rows affected by this operation
     */
    @Query("DELETE FROM SongAlbumMap WHERE albumId = :albumId")
    fun clear( albumId: String ): Int

    /**
     * Delete all mappings where songs aren't exist in `Song` table
     *
     * @return number of rows affected by this operation
     */
    @Query("""
        DELETE FROM SongAlbumMap 
        WHERE songId NOT IN (
            SELECT DISTINCT id
            FROM Song
        )
    """)
    fun clearGhostMaps(): Int

    /**
     * Results are sorted by [SongAlbumMap.position].
     *
     * @param albumId of artist to look for
     * @param limit number of results cannot go over this value
     *
     * @return all [Song]s that were mapped to album has [Album.id] matches [albumId],
     * sorted by song's position in album
     */
    @Query("""
        SELECT Song.*
        FROM SongAlbumMap
        JOIN Song ON id = songId
        WHERE albumId = :albumId
        ORDER BY SongAlbumMap.position
        LIMIT :limit
    """)
    fun allSongsOf( albumId: String, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    @Query("SELECT DISTINCT Song.* FROM SongAlbumMap JOIN Song ON id = songId WHERE albumId = :albumId ORDER BY SongAlbumMap.position LIMIT :limit")
    fun allSongsOfDirect( albumId: String, limit: Int = Int.MAX_VALUE ): List<Song>

    /**
     * @param artistId of artist to look for
     *
     * @return every [Album] that contains at least one song mapped to the
     * artist — the album-level copies that a rename of the artist row must
     * reach (synchronous, usable inside a transaction)
     */
    @Query("""
        SELECT DISTINCT A.*
        FROM Album A
        JOIN SongAlbumMap SAM ON SAM.albumId = A.id
        JOIN Song S ON S.id = SAM.songId
        JOIN SongArtistMap SA ON SA.songId = S.id
        WHERE SA.artistId = :artistId
    """)
    fun albumsOfArtistDirect( artistId: String ): List<Album>

    /**
     * All songs of the album ranked by play time (desc), i.e. the album's
     * tracklist reordered by popularity instead of disc position.
     */
    @Query("""
        SELECT DISTINCT Song.*
        FROM SongAlbumMap
        JOIN Song ON id = songId
        WHERE albumId = :albumId
        ORDER BY Song.totalPlayTimeMs DESC
        LIMIT :limit
    """)
    fun getTopSongsOfDirect( albumId: String, limit: Int = Int.MAX_VALUE ): List<Song>

    /**
     * @return [Album] that the song belongs to
     */
    @Query("""
        SELECT A.*
        FROM Album A
        JOIN SongAlbumMap SAM ON SAM.albumId = A.id
        WHERE SAM.songId = :songId
        LIMIT :limit
    """)
    fun findAlbumOf( songId: String, limit: Int = Int.MAX_VALUE ): Flow<Album?>

    /**
     * @return every [Album] that the song belongs to (synchronous, usable
     * inside a transaction) - a song mapped to several albums yields every
     * distinct album row, not one arbitrary mapping (the previous `LIMIT 1`
     * picked a nondeterministic album when a song was mapped twice)
     */
    @Query("""
        SELECT DISTINCT A.*
        FROM Album A
        JOIN SongAlbumMap SAM ON SAM.albumId = A.id
        WHERE SAM.songId = :songId
    """)
    fun findAlbumsOfDirect( songId: String ): List<Album>

    @Query("""
        SELECT position FROM SongAlbumMap
        WHERE songId = :songId
        LIMIT 1
    """)
    fun findPositionOf( songId: String ): Flow<Int?>

    @Query("""
        INSERT OR IGNORE INTO SongAlbumMap ( songId, albumId, position )
        VALUES( 
            :songId,
            :albumId,
            CASE
                WHEN :position < 0 THEN COALESCE(
                    (
                        SELECT MAX(position) + 1 
                        FROM SongAlbumMap 
                        WHERE albumId = :albumId
                    ), 
                    0
                )
                ELSE :position 
            END
        )
    """)
    fun map( songId: String, albumId: String, position: Int = -1 )

    @Query("UPDATE SongAlbumMap SET songId = :newId WHERE songId = :oldId")
    fun updateSongId(oldId: String, newId: String)

    @Query("UPDATE SongAlbumMap SET albumId = :newId WHERE albumId = :oldId")
    fun updateAlbumId(oldId: String, newId: String)

    /**
     * Delete all mappings for a specific song
     *
     * @param songId the song ID to delete mappings for
     * @return number of rows affected by this operation
     */
    @Query("DELETE FROM SongAlbumMap WHERE songId = :songId")
    fun deleteBySongId(songId: String): Int
}

