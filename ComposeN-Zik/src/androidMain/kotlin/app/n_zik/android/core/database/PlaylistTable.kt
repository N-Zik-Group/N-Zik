package app.n_zik.android.core.database

import android.database.SQLException
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Update
import androidx.room.Upsert
import app.it.fast4x.rimusic.PINNED_PREFIX

import app.it.fast4x.rimusic.enums.PlaylistSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.models.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

data class PlaylistBookmarkState(
    val browseId: String,
    val isYoutubePlaylist: Boolean
)

@Dao
@RewriteQueriesToDropUnusedColumns
interface PlaylistTable {

    /**
     * @return list of songs that were mapped to at least 1 playlist
     */
    @Query("""
        SELECT DISTINCT S.*
        FROM SongPlaylistMap spm
        JOIN Song S ON S.id = spm.songId
        ORDER BY S.ROWID
        LIMIT :limit
    """)
    fun allSongs( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return list of songs that were mapped to at least 1 **pinned** playlist
     */
    @Query("""
        SELECT DISTINCT S.*
        FROM SongPlaylistMap spm
        JOIN Song S ON S.id = spm.songId
        JOIN Playlist P ON P.id = spm.playlistId
        WHERE P.name LIKE '$PINNED_PREFIX%' COLLATE NOCASE
        ORDER BY S.ROWID
        LIMIT :limit
    """)
    fun allPinnedSongs( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return list of songs that belong YouTube private playlist
     */
    @Query("""
        SELECT DISTINCT S.*
        FROM SongPlaylistMap spm
        JOIN Song S ON S.id = spm.songId
        JOIN Playlist P ON P.id = spm.playlistId
        WHERE P.isYoutubePlaylist
        ORDER BY S.ROWID
        LIMIT :limit
    """)
    fun allYTPlaylistSongs( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return all playlists from this table with number of songs they carry
     */
    @Query("""
        SELECT DISTINCT 
            *,
            (
                SELECT COUNT(songId)
                FROM SongPlaylistMap
                WHERE playlistId = id
            ) as songCount
        FROM Playlist
        ORDER BY ROWID
        LIMIT :limit
    """)
    fun allAsPreview( limit: Int = Int.MAX_VALUE ): Flow<List<PlaylistPreview>>

    /**
     * @return the total number of rows in this table
     */
    @Query("SELECT COUNT(*) FROM Playlist")
    suspend fun countAll(): Int

    /**
     * @param browseId of playlist to look for
     * @return [Playlist] that has [Playlist.browseId] matches [browseId]
     */
    @Query("SELECT DISTINCT * FROM Playlist WHERE browseId = :browseId")
    fun findByBrowseId( browseId: String ): Flow<Playlist?>

    @Query("""
        SELECT browseId, isYoutubePlaylist
        FROM Playlist
        WHERE browseId IN (:browseIds)
    """)
    fun getBookmarkStatesForPlaylists( browseIds: List<String> ): Flow<List<PlaylistBookmarkState>>

    /**
     * @return [Playlist] that has [Playlist.name] equals to [playlistName], case-insensitive
     */
    @Query("""
        SELECT DISTINCT * 
        FROM Playlist 
        WHERE trim(name) COLLATE NOCASE = trim(:playlistName) COLLATE NOCASE
        LIMIT 1
    """)
    fun findByName( playlistName: String ): Flow<Playlist?>

    /**
     * @return playlist with id [playlistId]
     */
    @Query("SELECT * FROM Playlist WHERE id = :playlistId")
    fun findById( playlistId: Long ): Flow<Playlist?>

    /**
     * Inserts [playlist]; a conflict throws [SQLException] (inside a
     * transaction, the entire transaction rolls back). Use when the insert
     * must succeed for the transaction to continue.
     *
     * @return ROWID of the new record
     * @throws SQLException when there's a conflict
     */
    @Insert
    @Throws(SQLException::class)
    fun insert( playlist: Playlist ): Long

    /** Inserts [playlist], silently skipping it when the row already exists (onConflict IGNORE). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore( playlist: Playlist )

    /** Inserts [playlist], replacing the existing row's columns when its primary key already exists. */
    @Upsert
    fun upsert( playlist: Playlist )

    /**
     * Replaces the row with [playlist]; a conflict throws [SQLException]
     * (inside a transaction, the entire transaction rolls back).
     *
     * @throws SQLException when there's a conflict
     */
    @Update
    @Throws(SQLException::class)
    fun update( playlist: Playlist ): Int

    /** Deletes [playlist]; returns the number of rows affected. */
    @Delete
    fun delete( playlist: Playlist ): Int

    @Query("SELECT * FROM Playlist")
    fun getAll(): List<Playlist>

    /**
     * @return whether a playlist with name [playlistName] exists in the database
     */
    @Query("""
        SELECT COUNT(*) > 0
        FROM Playlist
        WHERE name = :playlistName
    """)
    fun exists( playlistName: String ): Flow<Boolean>

    @Query("SELECT COUNT(*) > 0 FROM Playlist WHERE name = :playlistName")
    fun existsDirect( playlistName: String ): Boolean

    /**
     * Toggles the [PINNED_PREFIX] on [Playlist.name]: unpinned → prefixed,
     * pinned → prefix removed. Returns the number of rows affected.
     */
    @Query("""
        UPDATE Playlist
        SET name = 
            CASE
                WHEN name LIKE '$PINNED_PREFIX%' THEN SUBSTR(name, LENGTH('$PINNED_PREFIX') + 1)
                ELSE '$PINNED_PREFIX' || name
            END
        WHERE id = :playlistId
    """)
    fun togglePin( playlistId: Long ): Int

    /**
     * Set the explicit pin state of a playlist (PC bridge, contract §10.2, since 1.7):
     * `true` = pinned now, `false` = not pinned. Unlike [togglePin], the target state is
     * given directly and the write is idempotent (setting the current state changes
     * nothing).
     *
     * @param playlistId playlist identifier to update
     * @param pinned target pin state
     *
     * @return number of playlists updated by this operation
     */
    @Query("""
        UPDATE Playlist
        SET name = 
            CASE
                WHEN :pinned = 1 AND name NOT LIKE '$PINNED_PREFIX%' THEN '$PINNED_PREFIX' || name
                WHEN :pinned = 0 AND name LIKE '$PINNED_PREFIX%' THEN SUBSTR(name, LENGTH('$PINNED_PREFIX') + 1)
                ELSE name
            END
        WHERE id = :playlistId
    """)
    fun pinState( playlistId: Long, pinned: Boolean ): Int

    @Query("UPDATE Playlist SET isAutoSync = NOT isAutoSync WHERE id = :playlistId")
    fun toggleAutoSync( playlistId: Long ): Int

    @Query("SELECT isAutoSync FROM Playlist WHERE id = :playlistId")
    fun isAutoSync( playlistId: Long ): Flow<Boolean>

    @Query("UPDATE Playlist SET position = :position WHERE id = :playlistId")
    fun updatePosition( playlistId: Long, position: Int ): Int

    @Query("UPDATE Playlist SET browseId = :browseId WHERE id = :playlistId")
    fun updateBrowseId( playlistId: Long, browseId: String? ): Int

    //<editor-fold defaultstate="collapsed" desc="Sort as preview">
    @Query("""
        SELECT DISTINCT P.*, COUNT(spm.songId) as songCount
        FROM Playlist P
        LEFT JOIN SongPlaylistMap spm ON P.id = spm.playlistId
        LEFT JOIN Song S ON S.id = spm.songId
        GROUP BY P.id
        ORDER BY COALESCE(SUM(S.totalPlayTimeMs), 0) ASC
        LIMIT :limit
    """)
    fun sortPreviewsByListeningTime( limit: Int = Int.MAX_VALUE ): Flow<List<PlaylistPreview>>

    @Query("""
        SELECT DISTINCT P.*, COUNT(spm.songId) as songCount
        FROM Playlist P
        LEFT JOIN SongPlaylistMap spm ON P.id = spm.playlistId
        LEFT JOIN Song S ON S.id = spm.songId
        GROUP BY P.id
        ORDER BY COALESCE(SUM(S.playCount), 0) ASC
        LIMIT :limit
    """)
    fun sortPreviewsByPlayCount( limit: Int = Int.MAX_VALUE ): Flow<List<PlaylistPreview>>

    @Query("""
        SELECT DISTINCT 
            *,
            (
                SELECT COUNT(songId)
                FROM SongPlaylistMap
                WHERE playlistId = id
            ) as songCount
        FROM Playlist
        ORDER BY 
            CASE WHEN position = -1 THEN 1 ELSE 0 END ASC,
            CASE WHEN position = -1 THEN ROWID ELSE position END ASC
        LIMIT :limit
    """)
    fun sortPreviewsByPosition( limit: Int = Int.MAX_VALUE ): Flow<List<PlaylistPreview>>

    fun sortPreviewsByName( limit: Int = Int.MAX_VALUE ): Flow<List<PlaylistPreview>> =
        allAsPreview( limit ).map { list ->
            list.sortedBy { it.playlist.cleanName() }
        }

    fun sortPreviewsBySongCount( limit: Int = Int.MAX_VALUE ): Flow<List<PlaylistPreview>> =
        allAsPreview( limit ).map { list ->
            list.sortedBy( PlaylistPreview::songCount )
        }

    /**
     * All playlists as [PlaylistPreview], sorted by [sortBy] (a
     * [PlaylistSortBy] property) and [sortOrder] (alphabetical / numerical
     * direction), trimmed to [limit].
     *
     * @param sortBy which playlist property to sort by
     * @param sortOrder the sort direction
     * @param limit stop the query once the result count reaches this number
     */
    fun sortPreviews(
        sortBy: PlaylistSortBy,
        sortOrder: SortOrder,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<PlaylistPreview>> = when( sortBy ) {
        PlaylistSortBy.ListeningTime -> sortPreviewsByListeningTime( limit )
        PlaylistSortBy.PlayCount              -> sortPreviewsByPlayCount()
        PlaylistSortBy.Name         -> sortPreviewsByName()
        PlaylistSortBy.DateAdded    -> allAsPreview()       // Already sorted by ROWID
        PlaylistSortBy.SongCount    -> sortPreviewsBySongCount()
        PlaylistSortBy.Custom       -> sortPreviewsByPosition( limit )
    }.map( sortOrder::applyTo ).take( limit )
    //</editor-fold>

    @Query("SELECT id FROM Playlist WHERE browseId = 'SPOTIFY_IMPORT' OR browseId = 'RIPLAY_IMPORT' OR browseId LIKE '%_HOMESONGS'")
    fun importPlaylistIds(): Flow<List<Long>>
}

