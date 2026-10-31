package app.n_zik.android.components.podcast

import androidx.media3.common.MediaItem
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.utils.asMediaItem
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.isPodcastBrowseId
import app.n_zik.android.core.database.canRefreshPlaylist
import app.n_zik.android.components.playlist.refreshPlaylistSource
import app.n_zik.android.components.playlist.loadCompletePodcastEpisodes
import app.n_zik.android.R
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Removes a saved snapshot without recreating a row deleted by another surface. */
internal suspend fun removeSavedPodcastBookmark(playlistId: Long): PodcastBookmarkOperation =
    withContext(NzikDispatchers.DATA) {
        var operation = PodcastBookmarkOperation.Remove
        Database.transaction {
            val latest = playlistTable.findById(playlistId).first()
            if (latest != null) {
                operation = if (isPodcastBrowseId(latest.browseId)) podcastBookmarkOperation(latest)
                    else PodcastBookmarkOperation.ExistingImport
                if (operation == PodcastBookmarkOperation.Remove) playlistTable.delete(latest)
            }
        }
        operation
    }

/** Handles local podcast bookmarking without changing remote account-library flags. */
fun launchSavedPodcastBookmarkToggle(
    playlist: Playlist?,
    scope: CoroutineScope,
    onRemoved: () -> Unit,
    onExistingImport: () -> Unit,
): Boolean {
    if (playlist == null || !isPodcastBrowseId(playlist.browseId)) return false
    scope.launch(NzikDispatchers.UI) {
        try {
            if (removeSavedPodcastBookmark(playlist.id) == PodcastBookmarkOperation.ExistingImport) {
                onExistingImport()
            } else {
                Toaster.done()
                onRemoved()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("PodcastBookmark").e(error, "Unable to remove saved podcast bookmark")
        }
    }
    return true
}

/** Replaces only this snapshot's mappings after a successful exact-ID podcast fetch. */
internal suspend fun refreshPodcastPlaylist(
    playlistId: Long,
    browseId: String,
    loadEpisodes: suspend (String) -> List<MediaItem>? = { id ->
        loadCompletePodcastEpisodes(id).map { it.asMediaItem }
    },
): Boolean = withContext(NzikDispatchers.DATA) {
    if (!isPodcastBrowseId(browseId)) return@withContext false
    val latest = Database.playlistTable.findById(playlistId).first()
        ?.takeIf { it.browseId == browseId && !it.isEditable } ?: return@withContext false
    refreshPlaylistSource(latest, loadEpisodes)
}

/** Routes podcast refresh to its browse endpoint and keeps failures visible in logs. */
fun launchSavedPodcastRefresh(playlist: Playlist?, scope: CoroutineScope): Boolean {
    val browseId = playlist?.browseId ?: return false
    if (!isPodcastBrowseId(browseId)) return false
    if (!canRefreshPlaylist(playlist)) return true
    scope.launch(NzikDispatchers.DATA) {
        try {
            if (refreshPodcastPlaylist(playlist.id, browseId)) Toaster.done()
            else {
                Timber.tag("PodcastRefresh").w("Podcast snapshot refresh did not complete")
                Toaster.e(R.string.error)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("PodcastRefresh").e(error, "Unable to refresh saved podcast episodes")
            Toaster.e(R.string.error)
        }
    }
    return true
}
