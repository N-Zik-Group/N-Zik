package app.n_zik.android.core.database

import android.database.SQLException
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.it.fast4x.rimusic.models.QueuedMediaItem
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface QueuedMediaItemTable {

    /**
     * @return all records from this table
     */
    @Query("""
        SELECT DISTINCT * 
        FROM QueuedMediaItem
        LIMIT :limit
    """)
    fun all( limit: Int = Int.MAX_VALUE ): Flow<List<QueuedMediaItem>>

    @Query("SELECT DISTINCT * FROM QueuedMediaItem LIMIT :limit")
    fun allDirect( limit: Int = Int.MAX_VALUE ): List<QueuedMediaItem>

    /** Inserts [queuedMediaItems]; when even one element fails the whole insert (or the surrounding transaction) rolls back and the exception propagates. */
    @Insert
    @Throws(SQLException::class)
    fun insert( queuedMediaItems: List<QueuedMediaItem> )

    @Query("DELETE FROM QueuedMediaItem")
    fun deleteAll(): Int
}

