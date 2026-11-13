package app.n_zik.android.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Immutable
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Format(
    @PrimaryKey val songId: String,
    val itag: Int? = null,
    val mimeType: String? = null,
    val bitrate: Long? = null,
    val contentLength: Long? = null,
    val lastModified: Long? = null,
    val loudnessDb: Float? = null,
    @ColumnInfo(defaultValue = "NULL") val codecs: String? = null,
    @ColumnInfo(defaultValue = "NULL") val sampleRate: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val perceptualLoudnessDb: Float? = null,
    @ColumnInfo(defaultValue = "NULL") val audioChannels: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val playbackUrl: String? = null,
    @ColumnInfo(defaultValue = "NULL") val downloadQuality: String? = null
)



