package app.n_zik.android.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import kotlinx.coroutines.flow.Flow

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

@Dao
@RewriteQueriesToDropUnusedColumns
interface ImportSongTable {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(importSong: ImportSong)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(importSongs: List<ImportSong>)

    @Query("SELECT position FROM ImportSong WHERE originalId = :originalId AND playlistId = :playlistId LIMIT 1")
    fun getPosition(originalId: String, playlistId: Long? = null): Int?

    @Query("SELECT position FROM ImportSong WHERE originalId = :originalId LIMIT 1")
    fun getPositionGlobal(originalId: String): Int?

    @Query("DELETE FROM ImportSong WHERE originalId = :originalId")
    fun deleteByOriginalId(originalId: String)

    @Query("SELECT * FROM ImportSong")
    fun getAllEntries(): List<ImportSong>

    @Query("DELETE FROM ImportSong")
    fun clear()
}
