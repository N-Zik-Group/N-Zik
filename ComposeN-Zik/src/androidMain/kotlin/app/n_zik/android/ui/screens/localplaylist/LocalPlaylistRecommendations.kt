package app.n_zik.android.ui.screens.localplaylist

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.core.database.artistEntryNames
import app.n_zik.android.enums.RecommendationsNumber
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.playback.utils.smartShuffleInsertionPositions
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.relatedSongs

internal suspend fun loadLocalPlaylistRecommendations(
    items: List<Song>,
    recommendationsNumber: RecommendationsNumber,
    allowSong: (Song) -> Boolean = { true },
): Map<Song, Int> {
    val remoteTracks = items.filterNot(Song::isLocal)
    if (remoteTracks.isEmpty()) return emptyMap()

    val targetRecommendations = recommendationsNumber.calculateAdaptiveRecommendations(items.size)
    val allRelatedSongs = mutableListOf<Song>()
    val existingSongIds = items.map { it.id }.toSet()

    // For large playlists, make more requests to get enough recommendations
    val numberOfRequests = when {
        remoteTracks.size <= 100 -> 1
        remoteTracks.size <= 500 -> 3
        remoteTracks.size <= 1000 -> 5
        remoteTracks.size <= 2000 -> 8
        else -> 10
    }

    val seedSongs = remoteTracks.shuffled().take(numberOfRequests)

    for (seedSong in seedSongs) {
        try {
            val relatedSongsResult = Innertube.relatedSongs(videoId = seedSong.id)?.getOrNull()

            currentCoroutineContext().ensureActive()
            relatedSongsResult?.songs?.forEach { songItem ->
                val info = songItem.info ?: return@forEach
                val videoId = info.endpoint?.videoId ?: return@forEach
                if (!existingSongIds.contains(videoId)) {
                    val song = Song(
                        // raw videoId + "e:" title prefix, as in Innertube.SongItem.asSong
                        id = videoId,
                        title = (if (songItem.explicit) EXPLICIT_PREFIX else "") + (info.name.orEmpty()),
                        artistsText = songItem.authors.artistEntryNames().joinToString(", "),
                        durationText = songItem.durationText,
                        thumbnailUrl = songItem.thumbnail?.url
                    )

                    if (allowSong(song) && !allRelatedSongs.any { it.id == song.id }) {
                        allRelatedSongs.add(song)
                    }
                }
            }

            if (numberOfRequests > 1) delay(200L)

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            // Continue with other requests even if one fails
            continue
        }
    }

    // The service may return fewer distinct candidates than the target.
    val candidates = allRelatedSongs.take(targetRecommendations)
    val positions = smartShuffleInsertionPositions(items.size, candidates)
    return candidates.associateWith(positions::getValue)
}
