package app.n_zik.android.components.podcast

import androidx.media3.common.MediaItem
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.models.Playlist
import app.n_zik.android.utils.asPodcastMediaItem
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.isPodcastBrowseId
import app.n_zik.android.components.playlist.refreshPlaylistSource
import app.n_zik.android.components.playlist.loadCompletePodcastEpisodes
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Removes a saved snapshot without recreating a row deleted by another surface. */
internal suspend fun removeSavedPodcastBookmark(
    playlistId: Long,
    expectedBrowseId: String? = null,
    isCurrentProfile: () -> Boolean = { true },
): PodcastBookmarkOperation? = withContext(NzikDispatchers.DATA) {
    var operation: PodcastBookmarkOperation? = null
    Database.transaction {
        if (!isCurrentProfile()) return@transaction
        val latest = playlistTable.findById(playlistId).first() ?: return@transaction
        if (!isPodcastBrowseId(latest.browseId) ||
            (expectedBrowseId != null && latest.browseId != expectedBrowseId)
        ) return@transaction
        operation = when (podcastBookmarkOperation(latest)) {
            PodcastBookmarkOperation.Remove -> PodcastBookmarkOperation.Remove.takeIf { playlistTable.delete(latest) > 0 }
            PodcastBookmarkOperation.ExistingImport -> PodcastBookmarkOperation.ExistingImport
            PodcastBookmarkOperation.Create -> null
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
    onFinished: () -> Unit = {},
): Boolean {
    if (playlist == null || !isPodcastBrowseId(playlist.browseId)) return false
    val context = appContext()
    val owner = ProfileWorkOwner.capture(context) ?: run {
        Toaster.e(R.string.common_error_title)
        onFinished()
        return true
    }
    val isCurrentProfile = { ProfileWorkOwner.isBound(context, owner) }
    scope.launch(NzikDispatchers.UI) {
        try {
            val operation = removeSavedPodcastBookmark(playlist.id, playlist.browseId, isCurrentProfile)
            if (!isCurrentProfile()) return@launch
            when (operation) {
                PodcastBookmarkOperation.ExistingImport -> onExistingImport()
                PodcastBookmarkOperation.Remove -> {
                    Toaster.done()
                    onRemoved()
                }
                else -> Toaster.e(R.string.common_error_title)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("PodcastBookmark").e(error, "Unable to remove saved podcast bookmark")
            if (isCurrentProfile()) Toaster.e(R.string.common_error_title)
        } finally {
            onFinished()
        }
    }
    return true
}

/** Replaces only this snapshot's mappings after a successful exact-ID podcast fetch. */
internal suspend fun refreshPodcastPlaylist(
    playlistId: Long,
    browseId: String,
    loadEpisodes: suspend (String) -> List<MediaItem>? = { id ->
        loadCompletePodcastEpisodes(id).map { it.asPodcastMediaItem(id) }
    },
): Boolean = withContext(NzikDispatchers.DATA) {
    if (!isPodcastBrowseId(browseId)) return@withContext false
    val latest = Database.playlistTable.findById(playlistId).first()
        ?.takeIf { it.browseId == browseId && !it.isEditable } ?: return@withContext false
    refreshPlaylistSource(latest, loadEpisodes)
}
