package app.n_zik.android.core.database

import app.n_zik.android.enums.AlbumSortBy
import app.n_zik.android.enums.ArtistSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.utils.durationTextToMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class DislikedAlbumTrackDuration(val albumId: String, val durationText: String?)

internal fun AlbumTable.sortDisliked(sortBy: AlbumSortBy, order: SortOrder): Flow<List<Album>> {
    val sorted = when (sortBy) {
        AlbumSortBy.Title, AlbumSortBy.OnDevice -> allDisliked().map { rows ->
            rows.sortedBy { it.cleanTitle().lowercase() }
        }
        AlbumSortBy.Artist -> allDisliked().map { rows ->
            rows.sortedBy { it.cleanAuthorsText().lowercase() }
        }
        AlbumSortBy.Songs -> sortDislikedBySongCount()
        AlbumSortBy.Duration -> allDisliked().combine(dislikedAlbumTrackDurations()) { rows, tracks ->
            val durations = tracks.groupingBy { it.albumId }.fold(0L) { total, track ->
                total + (track.durationText?.let(::durationTextToMillis) ?: 0L).coerceAtLeast(0L)
            }
            rows.sortedBy { durations[it.id] ?: 0L }
        }
        AlbumSortBy.PlayCount, AlbumSortBy.ListeningTime -> sortDislikedByPlayback(sortBy == AlbumSortBy.PlayCount)
        AlbumSortBy.DateAdded -> allDisliked().map { rows -> rows.sortedBy(Album::timestamp) }
        AlbumSortBy.Year -> allDisliked().map { rows -> rows.sortedBy { it.year?.toIntOrNull() } }
        AlbumSortBy.Custom -> allDisliked().map { rows -> rows.sortedBy(Album::position) }
    }
    return sorted.map(order::applyTo)
}

internal fun ArtistTable.sortDisliked(sortBy: ArtistSortBy, order: SortOrder): Flow<List<Artist>> {
    val sorted = when (sortBy) {
        ArtistSortBy.Name, ArtistSortBy.OnDevice -> allDisliked().map { rows ->
            rows.sortedBy { it.cleanName().lowercase() }
        }
        ArtistSortBy.PlayCount, ArtistSortBy.ListeningTime -> sortDislikedByPlayback(sortBy == ArtistSortBy.PlayCount)
        ArtistSortBy.DateAdded -> allDisliked().map { rows -> rows.sortedBy(Artist::timestamp) }
        ArtistSortBy.Custom -> allDisliked().map { rows -> rows.sortedBy(Artist::position) }
    }
    return sorted.map(order::applyTo)
}
