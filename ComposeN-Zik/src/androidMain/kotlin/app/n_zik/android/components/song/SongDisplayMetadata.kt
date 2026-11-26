package app.n_zik.android.components.song

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import app.n_zik.android.R
import app.n_zik.android.models.Info
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.models.Song
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.artistLinksWithStoredFallback
import app.n_zik.android.utils.preferences
import app.n_zik.android.core.database.trackBrowseIdOrigin
import app.n_zik.android.utils.resolveTrackArtistName
import app.n_zik.android.utils.splitArtistNames
import app.n_zik.android.utils.usableTrackArtistName
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

/** Keeps a row's stored byline and its exact artist identities in the same presentation. */
@Composable
internal fun songArtistDisplayLabel(
    song: Song,
    storedCredits: List<Info>,
    source: MediaItem? = null,
): String {
    val preferences = LocalContext.current.preferences
    val placeholder = stringResource(R.string.metadata_unknown_artist)
    val linked = (source?.artistLinksWithStoredFallback(storedCredits) {
        preferences.trackBrowseIdOrigin(song.id, it)
    } ?: storedCredits).filter { isArtistIdForSong(it.id, song.isLocal) }
    val storedName = usableTrackArtistName(song.artistsText, placeholder)
    val sourceName = source?.mediaMetadata?.artist?.toString()?.takeIf {
        usableTrackArtistName(it, placeholder).splitArtistNames().size > storedName.splitArtistNames().size
    }
    return resolveTrackArtistName(sourceName, song.artistsText, linked, placeholder) ?: placeholder
}
