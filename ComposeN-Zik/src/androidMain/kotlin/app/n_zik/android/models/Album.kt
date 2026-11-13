package app.n_zik.android.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.n_zik.android.cleanPrefix
import app.n_zik.android.musicbrainz.models.ExternalLink

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
    @ColumnInfo(defaultValue = "0") val isYoutubeAlbum: Boolean = false,
    @ColumnInfo(defaultValue = "-1") val position: Int = -1,
    @ColumnInfo(defaultValue = "NULL") val lastFetch: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val dislikedAt: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val genres: List<String>? = null,
    @ColumnInfo(defaultValue = "NULL") val originalYear: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val albumType: String? = null,
    @ColumnInfo(defaultValue = "NULL") val tags: List<String>? = null,
    @ColumnInfo(defaultValue = "NULL") val rating: Float? = null,
    @ColumnInfo(defaultValue = "NULL") val ratingVotes: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val wikipediaUrl: String? = null,
    @ColumnInfo(defaultValue = "NULL") val wikipediaInfo: String? = null,
    @ColumnInfo(defaultValue = "NULL") val description: String? = null,
    @ColumnInfo(defaultValue = "NULL") val links: List<ExternalLink>? = null,
    @ColumnInfo(defaultValue = "NULL") val mbId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val youtubeAlbumId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val mbLastFetch: Long? = null
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

    val info: String
        get() = buildList {
            originalYear?.let { add(it.toString()) }
            albumType?.let { add(it) }
        }.joinToString("    ")

    val keywords: List<String>
        get() = (genres.orEmpty() + tags.orEmpty())
            .distinctBy { it.lowercase() }
            .take(8)
}



