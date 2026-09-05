package app.it.fast4x.rimusic.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.utils.durationTextToMillis
import app.it.fast4x.rimusic.utils.setLikeState
import kotlinx.serialization.Serializable

@Serializable
@Immutable
@Entity
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artistsText: String? = null,
    val durationText: String?,
    val thumbnailUrl: String?,
    val likedAt: Long? = null,
    val totalPlayTimeMs: Long = 0,
    val position: Int = -1,
    val isYoutubeSong: Boolean = false
) {
    @ColumnInfo(name = "playCount", defaultValue = "0")
    var playCount: Int = 0
    companion object {
        fun makePlaceholder( id: String ) =
            Song(
                id = id,
                title = "",
                durationText = null,
                thumbnailUrl = null
            )
    }

    val formattedTotalPlayTime: String
        get() {
            val seconds = totalPlayTimeMs / 1000

            val hours = seconds / 3600

            return when {
                hours == 0L -> "${seconds / 60}m"
                hours < 24L -> "${hours}h"
                else -> "${hours / 24}d"
            }
        }

    fun toggleLike(): Song {
        return copy(
            //likedAt = if (likedAt == null) System.currentTimeMillis() else null
            likedAt = setLikeState(likedAt)
        )
    }

    fun cleanTitle() = cleanPrefix( this.title )

    fun cleanArtistsText() = cleanPrefix( this.artistsText ?: "" )

    fun relativePlayTime(): Double {
        val totalPlayTimeMs = durationTextToMillis(this.durationText ?: "")
        return if(totalPlayTimeMs > 0) this.totalPlayTimeMs.toDouble() / totalPlayTimeMs.toDouble() else 0.0
    }
}


