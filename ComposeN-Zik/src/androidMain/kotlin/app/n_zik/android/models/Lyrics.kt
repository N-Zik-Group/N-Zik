package app.n_zik.android.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import app.n_zik.android.models.Song

@Immutable
@Entity(
    primaryKeys = ["songId", "type"],
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
class Lyrics(
    val songId: String,
    val type: String,
    val data: String?,
    /**
     * `true` when [data] is the user's own: text typed in the lyrics editor or a track picked from LrcLib.
     * Automatic fetches must never replace it (gh-765); "Fetch lyrics again" resets it.
     */
    @ColumnInfo(defaultValue = "0")
    val isEdited: Boolean = false,
    /**
     * Epoch millis of the last fetch that wrote [data]; `null` for rows stored before the column
     * existed or after a manual "fetch lyrics again" reset. Drives the 30-day refetch TTL.
     */
    @ColumnInfo
    val lastFetchedAt: Long? = null,
)




