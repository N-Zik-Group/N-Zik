package app.n_zik.android.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import timber.log.Timber
import app.n_zik.android.LOCAL_KEY_PREFIX

/**
 * Stores the original import position of songs from Spotify/RiPlay CSV imports.
 * Used by the match system to preserve the user's intended order after matching.
 */
@Entity
data class ImportSong(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalId: String,
    val position: Int,
    val playlistId: Long? = null,
    val importDate: Long = System.currentTimeMillis()
)

data class ImportMatchRow(
    @Embedded val entry: ImportSong,
    val songExists: Boolean,
    val songDuration: String?,
)

@Dao
@RewriteQueriesToDropUnusedColumns
interface ImportSongTable {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(importSong: ImportSong)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(importSongs: List<ImportSong>)

    @Query("SELECT position FROM ImportSong WHERE originalId = :originalId AND playlistId = :playlistId LIMIT 1")
    suspend fun getPosition(originalId: String, playlistId: Long? = null): Int?

    @Query("SELECT position FROM ImportSong WHERE originalId = :originalId LIMIT 1")
    suspend fun getPositionGlobal(originalId: String): Int?

    @Query("DELETE FROM ImportSong WHERE originalId = :originalId")
    suspend fun deleteByOriginalId(originalId: String)

    @Query("""
        SELECT pending.*, song.id IS NOT NULL AS songExists, song.durationText AS songDuration
        FROM ImportSong AS pending
        LEFT JOIN Song AS song ON song.id = pending.originalId
        WHERE :playlistId IS NULL OR pending.playlistId = :playlistId
        ORDER BY pending.id
    """)
    suspend fun getMatchRows(playlistId: Long?): List<ImportMatchRow>

    @Transaction
    suspend fun reconcileMatches(playlistId: Long? = null): List<ImportSong> {
        val failedEntries = mutableListOf<ImportSong>()
        for (row in getMatchRows(playlistId)) {
            val originalId = row.entry.originalId
            val isYouTubeId = originalId.length == 11 && !originalId.startsWith(LOCAL_KEY_PREFIX)
            // Riplay placeholders can retain a YouTube ID until their duration is resolved.
            if (row.songExists && (!isYouTubeId || row.songDuration == "00:00")) {
                failedEntries.add(row.entry)
            } else {
                deleteByOriginalId(originalId)
            }
        }
        if (playlistId != null) {
            Timber.tag("MatchPlaylist").d("Import reconciliation left %d unresolved rows", failedEntries.size)
        }
        return failedEntries
    }

    /** Keeps pending matches aligned when existing playlist members move. */
    @Query("""
        UPDATE ImportSong SET position = (
            SELECT position FROM SongPlaylistMap
            WHERE songId = ImportSong.originalId AND playlistId = :playlistId
        )
        WHERE playlistId = :playlistId AND EXISTS (
            SELECT 1 FROM SongPlaylistMap
            WHERE songId = ImportSong.originalId AND playlistId = :playlistId
        )
    """)
    suspend fun alignPlaylistPositions(playlistId: Long)

    @Query("DELETE FROM ImportSong")
    suspend fun clear()
}
