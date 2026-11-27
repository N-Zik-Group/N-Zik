package app.n_zik.android.ui.screens.localplaylist

import app.n_zik.android.hasExplicitPrefix
import app.n_zik.android.models.Song
import app.n_zik.android.models.SongPlaylistMap
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
            (!parentalControlEnabled || !song.title.hasExplicitPrefix()) &&
            (song.cleanTitle().contains(query, ignoreCase = true) ||
                song.cleanArtistsText().contains(query, ignoreCase = true))
    }
}

/** Assigns the requested order to its existing slots, leaving hidden entries at their positions. */
internal fun reorderVisiblePlaylistMappings(
    mappings: List<SongPlaylistMap>,
    visibleSongIds: List<String>,
): List<SongPlaylistMap> {
    val mappingsById = mappings.associateBy(SongPlaylistMap::songId)
    val visibleMappings = visibleSongIds.distinct().mapNotNull(mappingsById::get)
    val slots = visibleMappings.map(SongPlaylistMap::position).sorted()
    return visibleMappings.mapIndexedNotNull { index, mapping ->
        val slot = slots[index]
        mapping.copy(position = slot).takeIf { slot != mapping.position }
    }
}
