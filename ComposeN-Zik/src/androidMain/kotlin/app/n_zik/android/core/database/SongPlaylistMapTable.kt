package app.n_zik.android.core.database

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.Dao
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Update
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.enums.PlaylistSongSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongPlaylistMap
import app.it.fast4x.rimusic.utils.durationTextToMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

@Dao
@RewriteQueriesToDropUnusedColumns
interface SongPlaylistMapTable {

    /** Removes [songId] from all playlists. */
    @Query("DELETE FROM SongPlaylistMap WHERE songId = :songId")
    fun deleteBySongId( songId: String ): Int

    /** Removes [songId] from playlist [playlistId]. */
    @Query("DELETE FROM SongPlaylistMap WHERE songId = :songId AND playlistId = :playlistId")
    fun deleteBySongId( songId: String, playlistId: Long ): Int

    /** Removes all songs from playlist [playlistId]. */
    @Query("DELETE FROM SongPlaylistMap WHERE playlistId = :playlistId")
    fun clear( playlistId: Long ): Int

    /** Deletes all mappings whose song no longer exists in the `Song` table. */
    @Query("""
        DELETE FROM SongPlaylistMap 
        WHERE songId NOT IN (
            SELECT DISTINCT id
            FROM Song
        )
    """)
    fun clearGhostMaps(): Int

    /** All [Song]s mapped to the playlist whose [Playlist.id] equals [playlistId]. */
    @Query("""
        SELECT DISTINCT S.*
        FROM SongPlaylistMap SPM
        JOIN Song S ON S.id = SPM.songId
        WHERE SPM.playlistId = :playlistId
        ORDER BY SPM.ROWID
        LIMIT :limit
    """)
    fun allSongsOf( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateReplace( songPlaylistMaps: List<SongPlaylistMap> ): Int

    /** The [SongPlaylistMap] linking [songId] to [playlistId], if any. */
    @Query("SELECT * FROM SongPlaylistMap WHERE playlistId = :playlistId AND songId = :songId")
    fun findById( songId: String, playlistId: Long ): Flow<SongPlaylistMap?>

    /**
     * @return position of [songId] in [playlistId], `-1` otherwise
     */
    @Query("""
        SELECT COALESCE(
            (
                SELECT position
                FROM SongPlaylistMap 
                WHERE songId = :songId 
                AND playlistId = :playlistId
            ),
            -1
        ) 
        """)
    fun findPositionOf( songId: String, playlistId: Long ): Int

    /**
     * @return whether [songId] is mapped to any playlist
     */
    @Query("""
        SELECT COUNT(s.id) > 0
        FROM Song s
        JOIN SongPlaylistMap spm ON spm.songId = s.id 
        WHERE s.id = :songId
    """)
    fun isMapped( songId: String ): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM SongPlaylistMap WHERE songId = :songId)")
    fun isMappedSync( songId: String ): Boolean

    /**
     * Batch variant of [isMapped]: checks a whole list of songs with a single query.
     *
     * @return the subset of [songIds] that is mapped to at least one playlist;
     * any ID absent from the result is not mapped
     */
    @Query("SELECT DISTINCT songId FROM SongPlaylistMap WHERE songId IN (:songIds)")
    fun songsInPlaylists( songIds: List<String> ): Flow<List<String>>

    /**
     * @return list of [Playlist.id] that [songId] is mapped to
     */
    @Query("""
        SELECT playlistId
        FROM SongPlaylistMap
        WHERE songId = :songId
    """)
    fun mappedTo( songId: String ): Flow<List<Long>>

    /** Randomly reassigns [SongPlaylistMap.position] to every song in playlist [playlistId]. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Query("""
        UPDATE SongPlaylistMap 
        SET position = shuffled.new_position
        FROM (
            SELECT spm1.songId, ROW_NUMBER() OVER (ORDER BY RANDOM()) - 1 AS new_position
            FROM SongPlaylistMap spm1
            WHERE spm1.playlistId = :playlistId
        ) AS shuffled
        WHERE playlistId = :playlistId
        AND shuffled.songId = SongPlaylistMap.songId 
    """)
    // You'll get complain from IDE that "shuffled" and "FROM"
    // aren't exist, that's OK - IGNORE it.
    fun shufflePositions( playlistId: Long ): Int

    /** Moves the song from position [from] to [to] within playlist [playlistId], shifting the other songs accordingly. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Query("""
        UPDATE SongPlaylistMap
        SET position = updated.new_position
        FROM (
            SELECT 
                spm.songId, 
                spm.position,
                CASE 
                    WHEN spm.position = :from THEN :to 
                    WHEN spm.position > :from AND spm.position <= :to THEN position - 1 
                    WHEN spm.position < :from AND spm.position >= :to THEN position + 1 
                    ELSE spm.position
                END AS new_position
            FROM SongPlaylistMap spm
            WHERE spm.playlistId = :playlistId
        ) AS updated
        WHERE playlistId = :playlistId
        AND updated.songId = SongPlaylistMap.songId
    """)
    // You'll get complain from IDE that "updated" and "FROM"
    // aren't exist, that's OK - IGNORE it.
    fun move( playlistId: Long, from: Int, to: Int ): Int

    @Query("UPDATE SongPlaylistMap SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    fun updatePosition( playlistId: Long, songId: String, position: Int )

    /**
     * Inserts [songId] into [playlistId] at the next available position
     * (MAX(position) + 1, or 0 for an empty playlist); skipped if the pair already exists.
     */
    @Query("""
        INSERT OR IGNORE INTO SongPlaylistMap ( songId, playlistId, position )
        VALUES( 
            :songId,
            :playlistId,
            COALESCE(
                (
                    SELECT MAX(position) + 1 
                    FROM SongPlaylistMap 
                    WHERE playlistId = :playlistId
                ), 
                0
            )
        )
    """)
    fun map( songId: String, playlistId: Long )

    @Query("INSERT OR IGNORE INTO SongPlaylistMap ( songId, playlistId, position ) VALUES (:songId, :playlistId, :position)")
    fun mapAtPosition( songId: String, playlistId: Long, position: Int )

    @Query("SELECT IFNULL(MAX(position), -1) FROM SongPlaylistMap WHERE playlistId = :playlistId")
    fun getMaxPosition(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM SongPlaylistMap WHERE playlistId = :playlistId")
    fun countSongsInPlaylist(playlistId: Long): Int

    //<editor-fold defaultstate="collapsed" desc="Sort songs of playlist">
    @Query("""
        SELECT DISTINCT S.*
        FROM SongPlaylistMap spm
        LEFT JOIN Song S ON S.id = spm.songId
        LEFT JOIN SongAlbumMap sam ON sam.songId = S.id
        LEFT JOIN Album A ON A.id = sam.albumId
        WHERE spm.playlistId = :playlistId
        ORDER BY 
            A.title IS NULL, 
            CASE
                WHEN A.title LIKE "$MODIFIED_PREFIX%" THEN SUBSTR(A.title, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE A.title
            END
        LIMIT :limit
    """)
    fun sortSongsByAlbum( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    @Query("""
        SELECT DISTINCT S.*
        FROM SongPlaylistMap spm
        LEFT JOIN Song S ON S.id = spm.songId
        LEFT JOIN SongAlbumMap sam ON sam.songId = S.id
        LEFT JOIN Album A ON A.id = sam.albumId
        WHERE spm.playlistId = :playlistId
        ORDER BY A.year IS NULL, A.year
        LIMIT :limit
    """)
    fun sortSongsByAlbumYear( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    fun sortSongsByArtist( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allSongsOf( playlistId, limit ).map { list ->
            list.sortedBy( Song::cleanArtistsText )
        }

    @Query("""
        SELECT DISTINCT S.*
        FROM SongPlaylistMap spm
        LEFT JOIN Song S ON S.id = spm.songId
        LEFT JOIN SongAlbumMap sam ON sam.songId = S.id
        LEFT JOIN Album A ON A.id = sam.albumId
        WHERE spm.playlistId = :playlistId
        ORDER BY 
            A.title IS NULL, 
            S.artistsText IS NULL,
            CASE
                WHEN A.title LIKE "$MODIFIED_PREFIX%" THEN SUBSTR(A.title, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE A.title
            END,
            CASE
                WHEN S.artistsText LIKE "$MODIFIED_PREFIX%" THEN SUBSTR(S.artistsText, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE S.artistsText
            END
        LIMIT :limit
    """)
    fun sortSongsByAlbumAndArtist( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    @Query("""
        SELECT S.*
        FROM SongPlaylistMap spm
        LEFT JOIN Song S ON S.id = spm.songId
        LEFT JOIN Event E ON E.songId = S.id
        WHERE spm.playlistId = :playlistId
        ORDER BY E.timestamp ASC
        LIMIT :limit
    """)
    fun sortSongsByDatePlayed( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    fun sortSongsByPlayTime( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allSongsOf( playlistId, limit ).map { list ->
            list.sortedBy( Song::totalPlayTimeMs )
        }

    fun sortSongsByRelativePlayTime( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allSongsOf( playlistId, limit ).map { list ->
            list.sortedBy( Song::relativePlayTime )
        }

    @Query("""
        SELECT S.id, S.title, S.artistsText, S.durationText, S.thumbnailUrl, S.likedAt, S.totalPlayTimeMs, S.position, S.isYoutubeSong, COUNT(E.songId) as playCount
        FROM SongPlaylistMap spm
        LEFT JOIN Song S ON S.id = spm.songId
        LEFT JOIN Event E ON E.songId = S.id
        WHERE spm.playlistId = :playlistId
        GROUP BY S.id
        ORDER BY playCount ASC
        LIMIT :limit
    """)
    fun sortSongsByPlayCount( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    @Query("""
        SELECT S.*
        FROM SongPlaylistMap spm
        LEFT JOIN Song S ON S.id = spm.songId
        WHERE spm.playlistId = :playlistId
        ORDER BY spm.position
        LIMIT :limit
    """)
    fun sortSongsByPosition( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    fun sortSongsByTitle( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allSongsOf( playlistId, limit ).map { list ->
            list.sortedBy( Song::cleanTitle )
        }

    fun sortSongsByDuration( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allSongsOf( playlistId, limit ).map { list ->
            list.sortedBy { durationTextToMillis( it.durationText ?: "0" ) }
        }

    fun sortSongsByLikedAt( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allSongsOf( playlistId, limit ).map { list ->
            list.sortedBy( Song::likedAt )
        }

    /**
     * Dispatches [sortBy] to the prebuilt playlist sort queries below, then applies
     * [sortOrder] and truncates to [limit].
     *
     * @see PlaylistSongSortBy
     * @see SortOrder
     */
    fun sortSongs(
        playlistId: Long,
        sortBy: PlaylistSongSortBy,
        sortOrder: SortOrder,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Song>> = when( sortBy ) {
        PlaylistSongSortBy.Album            -> sortSongsByAlbum( playlistId )
        PlaylistSongSortBy.AlbumYear        -> sortSongsByAlbumYear( playlistId )
        PlaylistSongSortBy.Artist           -> sortSongsByArtist( playlistId )
        PlaylistSongSortBy.ArtistAndAlbum   -> sortSongsByAlbumAndArtist( playlistId )
        PlaylistSongSortBy.DatePlayed       -> sortSongsByDatePlayed( playlistId )
        PlaylistSongSortBy.PlayTime         -> sortSongsByPlayTime( playlistId )
        PlaylistSongSortBy.RelativePlayTime -> sortSongsByRelativePlayTime( playlistId )
        PlaylistSongSortBy.PlayCount        -> sortSongsByPlayCount( playlistId )
        PlaylistSongSortBy.Custom           -> sortSongsByPosition( playlistId )
        // Spec 2: "Rewind Top" of a generated rewind-* playlist = the top order snapshot
        // written at generation time (no new query — same source as Custom)
        PlaylistSongSortBy.RewindTop        -> sortSongsByPosition( playlistId )
        PlaylistSongSortBy.Title            -> sortSongsByTitle( playlistId )
        PlaylistSongSortBy.Duration         -> sortSongsByDuration( playlistId )
        PlaylistSongSortBy.DateLiked        -> sortSongsByLikedAt( playlistId )
        PlaylistSongSortBy.DateAdded        -> allSongsOf( playlistId )     // Already sorted by ROWID
        PlaylistSongSortBy.Downloaded       -> sortSongsByTitle( playlistId ) // Handled in-memory
    }.map( sortOrder::applyTo ).take( limit )
    //</editor-fold>

    @Query("UPDATE SongPlaylistMap SET songId = :newId WHERE songId = :oldId")
    fun updateSongId(oldId: String, newId: String)

    @Query("SELECT * FROM SongPlaylistMap WHERE songId = :songId")
    fun getAllForSong(songId: String): List<SongPlaylistMap>

    @Query("SELECT DISTINCT songId FROM SongPlaylistMap")
    fun allMappedSongIds(): Flow<List<String>>

    /**
     * Synchronous mirror of [sortSongsByPosition] for the PC bridge: only mapped songs
     * that exist, in playlist order.
     */
    @Query("""
        SELECT S.*
        FROM SongPlaylistMap spm
        JOIN Song S ON S.id = spm.songId
        WHERE spm.playlistId = :playlistId
        ORDER BY spm.position
    """)
    fun songsByPositionDirect( playlistId: Long ): List<Song>

    /**
     * Read-only, for the PC bridge (contract §1.1 `Playlist.artworkTrackId`).
     *
     * @return every (playlist, song) pair whose song has a thumbnail, in playlist order
     */
    @Query("""
        SELECT spm.playlistId AS playlistId, spm.songId AS songId
        FROM SongPlaylistMap spm
        JOIN Song S ON S.id = spm.songId
        WHERE S.thumbnailUrl IS NOT NULL
        ORDER BY spm.playlistId, spm.position
    """)
    fun songsWithThumbnailDirect(): List<PlaylistSongRef>
}

/** One song of one playlist; see [SongPlaylistMapTable.songsWithThumbnailDirect]. */
data class PlaylistSongRef(
    val playlistId: Long,
    val songId: String
)
