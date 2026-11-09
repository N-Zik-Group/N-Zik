package app.n_zik.android.components.song

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.Flow

/** Observes metadata only while the row is composed and its lifecycle is active. */
@Composable
internal fun rememberSongDisplayMetadata(song: Song, storedSongs: Flow<Song?>): Song {
    val stored by storedSongs.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)
    return resolveSongDisplayMetadata(song, stored)
}

internal fun resolveSongDisplayMetadata(incoming: Song, stored: Song?): Song {
    if (stored == null || stored.id != incoming.id) return incoming
    // A reset's cleared artwork/artist fields are authoritative; only duration can be enriched remotely.
    return if (stored.durationText.isNullOrBlank() && !incoming.durationText.isNullOrBlank()) {
        stored.copy(durationText = incoming.durationText).apply { playCount = stored.playCount }
    } else {
        stored
    }
}
