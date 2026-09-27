package app.n_zik.android.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Upsert
import app.n_zik.android.models.Lyrics
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface LyricsTable {

    /**
     * @param songId of song to look for
     * @param type the type of lyrics to look for (KARAOKE, SYNC, UNSYNC)
     * @return [Lyrics] that has [Lyrics.songId] matches [songId] and [Lyrics.type] matches [type]
     */
    @Query("SELECT DISTINCT * FROM Lyrics WHERE songId = :songId AND type = :type")
    fun findBySongIdAndType( songId: String, type: String ): Flow<Lyrics?>

    @Query("SELECT DISTINCT * FROM Lyrics WHERE songId = :songId")
    fun findAllBySongId( songId: String ): Flow<List<Lyrics>>

    /**
     * Attempt to write [lyrics] into database.
     *
     * If [lyrics] exist (determined by its primary key),
     * existing record's columns will be replaced
     * by provided [lyrics]' data.
     *
     * @param lyrics data intended to insert in to database
     * @return ROWID of successfully modified record
     */
    @Upsert
    fun upsert( lyrics: Lyrics ): Long

    /**
     * Reset the lyrics fetch TTL for every row of the song
     * ([Lyrics.lastFetchedAt] → NULL) so the lyrics are fetched again. Called
     * by the song « Update » action: the metadata the lyrics lookup is based
     * on (title / artists) was just refreshed.
     *
     * @param songId song to have its lyrics fetch TTL reset
     *
     * @return number of lyrics rows affected by this operation
     */
    @Query("UPDATE Lyrics SET lastFetchedAt = NULL WHERE songId = :songId")
    fun resetFetchTtlBySongId( songId: String ): Int
}


