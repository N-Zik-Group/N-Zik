package app.it.fast4x.rimusic.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.it.fast4x.rimusic.cleanPrefix

@Immutable
@Entity
data class Album(
    @PrimaryKey val id: String,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val year: String? = null,
    val authorsText: String? = null,
    val shareUrl: String? = null,
    val timestamp: Long? = null,
    val bookmarkedAt: Long? = null,
    val isYoutubeAlbum: Boolean = false,
    val position: Int = -1,
    val lastFetch: Long? = null,
    val dislikedAt: Long? = null
) {
    fun toggleBookmark(): Album {
        return copy(
            bookmarkedAt = if (bookmarkedAt == null) System.currentTimeMillis() else null
        )
    }

    val isDisliked: Boolean
        get() = dislikedAt != null

    fun cleanTitle() = cleanPrefix( this.title ?: "" )

    fun cleanAuthorsText() = cleanPrefix( this.authorsText ?: "" )
}



