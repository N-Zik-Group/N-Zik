package app.n_zik.android.components.playlist

import android.content.Context
import app.n_zik.android.R
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.isPlaylistBookmarked
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.enums.SyncDirection
import app.n_zik.android.models.Playlist
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getSyncDirection
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.syncPushPlaylistKey
import app.n_zik.innertube.YtMusic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Commits music playlist bookmarks from both the saved list and its details. */
internal fun launchSavedPlaylistBookmarkToggle(
    context: Context,
    playlist: Playlist,
    browseId: String,
    onFinished: () -> Unit,
) {
    val app = context.applicationContext
    val owner = ProfileWorkOwner.capture(app) ?: run {
        onFinished()
        return Toaster.e(R.string.common_error_title)
    }
    val wasBookmarked = isPlaylistBookmarked(playlist)
    val bookmarked = !wasBookmarked
    NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
        try {
            val updated = withContext(NzikDispatchers.DATA) {
                if (!ProfileWorkOwner.isBound(app, owner)) return@withContext null
                val current = Database.playlistTable.findById(playlist.id).first()
                    ?.takeIf { it.browseId == browseId && isPlaylistBookmarked(it) == wasBookmarked }
                    ?: return@withContext null
                val pushPlaylist = app.preferences.getBoolean(syncPushPlaylistKey, false)
                if (isYouTubeSyncEnabled() && pushPlaylist && getSyncDirection() != SyncDirection.YT_TO_APP &&
                    isNetworkConnected(app)) {
                    val sourceId = browseId.removePrefix("VL")
                    if (bookmarked) YtMusic.likePlaylistOrAlbum(sourceId).getOrThrow()
                    else YtMusic.removelikePlaylistOrAlbum(sourceId).getOrThrow()
                }
                commitSavedPlaylistBookmark(current, bookmarked) {
                    ProfileWorkOwner.isBound(app, owner)
                }
            }
            if (!ProfileWorkOwner.isBound(app, owner)) return@launch
            if (updated == null) {
                Toaster.e(R.string.common_error_title)
                return@launch
            }
            Toaster.s(if (bookmarked) R.string.library_favorites_added else R.string.library_favorites_removed)
            if (bookmarked) MyDownloadHelper.downloadBookmarkedPlaylist(app, browseId, updated)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("PlaylistBookmark").e(error, "Failed to toggle playlist bookmark")
            if (ProfileWorkOwner.isBound(app, owner)) Toaster.e(R.string.common_error_title)
        } finally {
            onFinished()
        }
    }
}

/** Updates the latest row without overwriting metadata changed during a remote request. */
internal suspend fun commitSavedPlaylistBookmark(
    expected: Playlist,
    bookmarked: Boolean,
    isCurrentProfile: () -> Boolean,
): Playlist? = Database.awaitWriteBatch {
    var saved: Playlist? = null
    transaction {
        if (!isCurrentProfile()) return@transaction
        val latest = playlistTable.findById(expected.id).first()
            ?.takeIf { it.browseId == expected.browseId && isPlaylistBookmarked(it) == isPlaylistBookmarked(expected) }
            ?: return@transaction
        val changed = latest.copy(isYoutubePlaylist = bookmarked)
        if (playlistTable.update(changed) > 0) saved = changed
    }
    saved
}
