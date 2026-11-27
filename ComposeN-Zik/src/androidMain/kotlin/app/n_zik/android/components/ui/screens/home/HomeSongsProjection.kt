package app.n_zik.android.components.ui.screens.home

import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.enums.BuiltInPlaylist
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal fun BuiltInPlaylist.hasSongOriginFilter(youtubeSyncEnabled: Boolean): Boolean =
    youtubeSyncEnabled && (this == BuiltInPlaylist.Favorites || this == BuiltInPlaylist.Disliked)

internal fun projectAllHomeSongs(songs: List<Song>, deviceSongIds: Set<String>, excludeHidden: Boolean): List<Song> =
    songs.filter { song ->
        if (song.isLocal) song.id in deviceSongIds
        else !excludeHidden || song.totalPlayTimeMs >= 1L
    }

internal data class HomeSongsBaseInput(
    val items: List<Song>,
    val recommendationsEnabled: Boolean,
    val recommendations: Map<Song, Int>,
    val parentalControlEnabled: Boolean,
)

internal suspend fun projectHomeSongsBase(input: HomeSongsBaseInput): List<Song> =
    withContext(NzikDispatchers.DATA) {
        val context = currentCoroutineContext()
        val ordered = input.items.toMutableList()
        if (input.recommendationsEnabled) {
            input.recommendations.forEach { (song, position) ->
                context.ensureActive()
                ordered.add(position.coerceIn(0, ordered.size), song)
            }
        }
        val seenIds = HashSet<String>()
        ordered.filter { song ->
            context.ensureActive()
            seenIds.add(song.id) && (!input.parentalControlEnabled || !song.title.startsWith(EXPLICIT_PREFIX, true))
        }
    }

internal suspend fun searchHomeSongs(items: List<Song>, query: String): List<Song> {
    if (query.isEmpty()) return items
    return withContext(NzikDispatchers.DATA) {
        val context = currentCoroutineContext()
        items.filter { song ->
            context.ensureActive()
            song.cleanTitle().contains(query, true) || song.cleanArtistsText().contains(query, true)
        }
    }
}
