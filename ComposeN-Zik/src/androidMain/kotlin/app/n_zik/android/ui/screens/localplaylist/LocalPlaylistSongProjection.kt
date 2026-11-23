package app.n_zik.android.ui.screens.localplaylist

import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.isLocal

internal fun projectLocalPlaylistSongs(
    songs: List<Song>,
    recommendations: Map<Song, Int>,
    query: String,
    onDeviceOnly: Boolean,
    parentalControlEnabled: Boolean,
): List<Song> {
    val ordered = if (onDeviceOnly || recommendations.isEmpty()) {
        songs
    } else {
        val withRecommendations = songs.toMutableList()
        val seenIds = songs.mapTo(HashSet(), Song::id)
        for ((song, position) in recommendations) {
            if (seenIds.add(song.id)) {
                withRecommendations.add(position.coerceIn(0, withRecommendations.size), song)
            }
        }
        withRecommendations
    }

    return ordered.distinctBy(Song::id).filter { song ->
        (!onDeviceOnly || song.isLocal) &&
            (!parentalControlEnabled || !song.title.startsWith(EXPLICIT_PREFIX)) &&
            (song.cleanTitle().contains(query, ignoreCase = true) ||
                song.cleanArtistsText().contains(query, ignoreCase = true))
    }
}
