package app.n_zik.android.components.ui.screens.home

import app.n_zik.android.models.Playlist
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.components.playlist.refreshPlaylistSource
import app.n_zik.android.core.database.PlaylistLinkKind
import app.n_zik.android.core.database.canRefreshPlaylist
import app.n_zik.android.core.database.playlistLinkKind
import kotlinx.coroutines.CancellationException
import timber.log.Timber

internal data class HomeLibraryRefreshResult(val refreshedPodcasts: Int, val failedPodcasts: Int)

/** Refresh the visible podcast snapshots. */
internal suspend fun refreshHomeLibraryPodcasts(
    targets: List<PlaylistPreview>,
    refreshPodcast: suspend (Playlist) -> Boolean = { refreshPlaylistSource(it) },
): HomeLibraryRefreshResult {
    val supported = targets.map { it.playlist }.distinctBy { it.id }.filter(::canRefreshPlaylist)
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
