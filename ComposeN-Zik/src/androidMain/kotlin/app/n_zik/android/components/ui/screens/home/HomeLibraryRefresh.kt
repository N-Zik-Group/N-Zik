package app.n_zik.android.components.ui.screens.home

import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.n_zik.android.components.playlist.refreshPlaylistSource
import app.n_zik.android.core.database.PlaylistLinkKind
import app.n_zik.android.core.database.canRefreshPlaylist
import app.n_zik.android.core.database.playlistLinkKind
import kotlinx.coroutines.CancellationException
import timber.log.Timber

internal data class HomeLibraryRefreshResult(val refreshedPodcasts: Int, val failedPodcasts: Int)

/** Podcast snapshots refresh locally; ordinary playlist IDs retain the existing service path. */
internal suspend fun refreshHomeLibraryTargets(
    targets: List<PlaylistPreview>,
    dispatchPlaylists: suspend (List<Long>) -> Unit,
    refreshPodcast: suspend (Playlist) -> Boolean = { refreshPlaylistSource(it) },
): HomeLibraryRefreshResult {
    val supported = targets.map { it.playlist }.distinctBy { it.id }.filter(::canRefreshPlaylist)
    val ordinary = supported.filter { playlistLinkKind(it.browseId) == PlaylistLinkKind.YouTubePlaylist }
    if (ordinary.isNotEmpty()) dispatchPlaylists(ordinary.map { it.id })
    var refreshed = 0
    var failed = 0
    supported.filter { playlistLinkKind(it.browseId) == PlaylistLinkKind.YouTubePodcast }.forEach { podcast ->
        try {
            if (refreshPodcast(podcast)) refreshed++ else failed++
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("HomeLibraryRefresh").e(error, "Unable to refresh podcast snapshot")
            failed++
        }
    }
    return HomeLibraryRefreshResult(refreshed, failed)
}
