package app.it.fast4x.rimusic.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.it.fast4x.rimusic.cleanPrefix

@Immutable
@Entity
data class Artist(
    @PrimaryKey val id: String,
    val name: String? = null,
    val thumbnailUrl: String? = null,
    val timestamp: Long? = null,
    val bookmarkedAt: Long? = null,
    val isYoutubeArtist: Boolean = false,
    val position: Int = -1,
    val lastFetch: Long? = null,
    val dislikedAt: Long? = null
) {
    fun cleanName() = cleanPrefix( this.name ?: "" )

    val isDisliked: Boolean
        get() = dislikedAt != null
}



