package app.n_zik.android.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.n_zik.android.cleanPrefix
import app.n_zik.android.musicbrainz.models.ExternalLink
import app.n_zik.android.musicbrainz.utils.toFlagEmoji

@Immutable
@Entity
data class Artist(
    @PrimaryKey val id: String,
    val name: String? = null,
    val thumbnailUrl: String? = null,
    val timestamp: Long? = null,
    val bookmarkedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val isYoutubeArtist: Boolean = false,
    @ColumnInfo(defaultValue = "-1") val position: Int = -1,
    @ColumnInfo(defaultValue = "NULL") val lastFetch: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val dislikedAt: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val genres: List<String>? = null,
    @ColumnInfo(defaultValue = "NULL") val artistType: String? = null,
    @ColumnInfo(defaultValue = "NULL") val countryCode: String? = null,
    @ColumnInfo(defaultValue = "NULL") val beginYear: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val tags: List<String>? = null,
    @ColumnInfo(defaultValue = "NULL") val rating: Float? = null,
    @ColumnInfo(defaultValue = "NULL") val ratingVotes: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val wikipediaUrl: String? = null,
    @ColumnInfo(defaultValue = "NULL") val wikipediaBio: String? = null,
    @ColumnInfo(defaultValue = "NULL") val description: String? = null,
    @ColumnInfo(defaultValue = "NULL") val disambiguation: String? = null,
    @ColumnInfo(defaultValue = "NULL") val links: List<ExternalLink>? = null,
    @ColumnInfo(defaultValue = "NULL") val mbId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val youtubeChannelId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val mbLastFetch: Long? = null
) {
    fun cleanName() = cleanPrefix( this.name ?: "" )

    val isDisliked: Boolean
        get() = dislikedAt != null

    val info: String
        get() = buildList {
            beginYear?.let { add(it) }
            countryCode?.let { add(it.toFlagEmoji()) }
        }.joinToString("    ")

    val keywords: List<String>
        get() = (genres.orEmpty() + tags.orEmpty())
            .distinctBy { it.lowercase() }
            .take(8)
}



