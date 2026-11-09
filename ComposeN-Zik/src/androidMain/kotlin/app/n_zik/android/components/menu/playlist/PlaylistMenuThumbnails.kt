package app.n_zik.android.components.menu.playlist

import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/** Rechecks custom artwork after writes and resets, falling back to the current song collage. */
internal fun playlistMenuThumbnails(
    songs: Flow<List<Song>>,
    revisions: Flow<Long>,
    ownsProfile: () -> Boolean,
    customThumbnail: () -> String?
): Flow<List<String>> = combine(songs, revisions) { rows, _ ->
    if (!ownsProfile()) return@combine emptyList()
    val custom = customThumbnail()
    if (!ownsProfile()) emptyList()
    else custom?.let(::listOf) ?: rows.mapNotNull(Song::thumbnailUrl).takeLast(4)
}.distinctUntilChanged().flowOn(NzikDispatchers.DATA)
