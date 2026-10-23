package app.n_zik.android.core.database

import android.database.SQLException
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.it.fast4x.rimusic.models.SearchQuery
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface SearchQueryTable {

    /** All [SearchQuery]s whose [SearchQuery.query] contains [searchTerm], case-insensitively (`COLLATE NOCASE`). */
    @Query("""
        SELECT DISTINCT * 
        FROM SearchQuery 
        WHERE `query` LIKE '%' || :searchTerm || '%' COLLATE NOCASE
        """)
    fun findAllContain( searchTerm: String ): Flow<List<SearchQuery>>

    /** Inserts [searchQuery], silently skipping it when the row already exists (onConflict IGNORE). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore( searchQuery: SearchQuery )

    /** Deletes [searchQuery]. */
    @Delete
    fun delete( searchQuery: SearchQuery )

    /** Deletes every entry in [searchQuery]; returns the number of rows affected. */
    @Delete
    fun delete( searchQuery: List<SearchQuery> ): Int

    @Query("DELETE FROM SearchQuery")
    fun deleteAll(): Int
}

