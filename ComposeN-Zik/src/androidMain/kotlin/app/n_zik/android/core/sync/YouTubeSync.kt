package app.n_zik.android.core.sync

import android.content.Context
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import app.n_zik.android.R
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.collectionFavoriteSongId
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.enums.DislikeMode
import app.n_zik.android.utils.canPushToYTM
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.excludeDislikedSongsKey
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.syncPushSongLikeKey
import app.n_zik.innertube.YtMusic.likeVideoOrSong
import app.n_zik.innertube.YtMusic.removelikeVideoOrSong

object YouTubeSync {
    private val youtubeVideoId = Regex("[A-Za-z0-9_-]{11}")

    @UnstableApi
    suspend fun rotateSongLikeState(context: Context, mediaItem: MediaItem) {
        val allowDislike = context.preferences.getString(excludeDislikedSongsKey, DislikeMode.Enabled.name)
            ?.let { runCatching { DislikeMode.valueOf(it) }.getOrNull() }?.isEnabled ?: true
        updateFavorite(context, mediaItem, if (allowDislike) FavoriteAction.Rotate else FavoriteAction.Toggle)
    }

    @UnstableApi
    suspend fun toggleSongLikeState(context: Context, mediaItem: MediaItem) {
        updateFavorite(context, mediaItem, FavoriteAction.Toggle)
    }

    @UnstableApi
    internal fun launchFavoriteUpdate(context: Context, items: List<MediaItem>, action: FavoriteAction) {
        val app = context.applicationContext
        val owner = ProfileWorkOwner.capture(app) ?: return
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            for (item in items) updateFavorite(app, item, action, owner)
        }
    }

    @UnstableApi
    private suspend fun updateFavorite(
        context: Context,
        mediaItem: MediaItem,
        action: FavoriteAction,
        owner: ProfileStorageIdentity? = ProfileWorkOwner.capture(context),
    ) {
        assert(Looper.myLooper() != Looper.getMainLooper()) { "Favorite updates require a background thread" }
        if (owner == null || !ProfileWorkOwner.isBound(context, owner)) return
        val id = collectionFavoriteSongId(mediaItem.mediaId)
        val item = if (id == mediaItem.mediaId) mediaItem else mediaItem.buildUpon().setMediaId(id).build()
        val change = try {
            persistFavorite(context, item, action, owner)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.tag("YouTubeSync").e(error, "Unable to update favorite")
            Toaster.e(R.string.library_collection_favorites_update_failed)
            return
        }
        if (!ProfileWorkOwner.isBound(context, owner)) return
        if (change == null) {
            Toaster.s(R.string.library_favorites_already_added)
            return
        }

        val (previous, current) = change
        showFavoriteChange(previous, current, item.mediaMetadata.title?.toString())
        val shouldPush = youtubeVideoId.matches(id) && current != false &&
            isYouTubeSyncEnabled() && canPushToYTM() &&
            context.preferences.getBoolean(syncPushSongLikeKey, false) && isNetworkConnected(context)
        if (shouldPush) {
            val response = if (current == true) likeVideoOrSong(id) else removelikeVideoOrSong(id)
            val error = response.exceptionOrNull()
            if (error is CancellationException) throw error
            if (error != null && ProfileWorkOwner.isBound(context, owner)) {
                Toaster.e(if (current == true) R.string.sync_youtube_like_failed else R.string.sync_youtube_unlike_failed)
            }
        }
    }

    private suspend fun persistFavorite(
        context: Context,
        item: MediaItem,
        action: FavoriteAction,
        owner: ProfileStorageIdentity,
    ): FavoriteChange? =
        Database.awaitWriteBatch {
            if (!ProfileWorkOwner.isBound(context, owner)) return@awaitWriteBatch null
            var change: FavoriteChange? = null
            transaction {
                val id = item.mediaId
                val previous = songTable.likeState(id).first()
                val desired = action.next(previous)
                if (desired == previous) return@transaction
                insertIgnore(item)
                check(songTable.likeState(id, desired) > 0) { "Favorite row could not be updated for $id" }
                change = FavoriteChange(previous, desired)
            }
            change?.let { (previous, current) ->
                try {
                    if (previous != true && current == true) MyDownloadHelper.downloadNewlyLiked(context, item)
                    if (previous == true && current != true) MyDownloadHelper.removeNewlyUnliked(context, item)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Timber.tag("YouTubeSync").w(error, "Unable to update favorite downloads")
                }
            }
            change
        }
}

internal enum class FavoriteAction {
    Add, Toggle, Rotate;

    fun next(current: Boolean?): Boolean? = when (this) {
        Add -> true
        Toggle -> if (current == true) null else true
        Rotate -> when (current) {
            null -> true
            true -> false
            false -> null
        }
    }
}

private data class FavoriteChange(val previous: Boolean?, val current: Boolean?)

internal fun showFavoriteChange(previous: Boolean?, current: Boolean?, name: String?) {
    when {
        current == true -> Toaster.s(R.string.library_favorites_added)
        current == false -> {
            if (name.isNullOrBlank()) Toaster.s(R.string.library_dislikes_added_generic)
            else Toaster.s(R.string.library_dislikes_added, "\"$name\"")
        }
        previous == false -> {
            if (name.isNullOrBlank()) Toaster.s(R.string.library_dislikes_removed_generic)
            else Toaster.s(R.string.library_dislikes_removed, "\"$name\"")
        }
        else -> Toaster.s(R.string.library_favorites_removed)
    }
}
