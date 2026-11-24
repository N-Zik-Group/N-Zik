package app.n_zik.android.core.sync

import app.n_zik.android.utils.excludeDislikedSongsKey
import app.n_zik.android.enums.DislikeMode

import android.content.Context
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.innertube.YtMusic.likeVideoOrSong
import app.n_zik.innertube.YtMusic.removelikeVideoOrSong
import app.n_zik.android.core.database.Database
import app.n_zik.android.appContext
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.syncPushSongLikeKey
import app.n_zik.android.utils.syncDirectionKey
import app.n_zik.android.enums.SyncDirection
import app.n_zik.android.utils.preferences
import kotlinx.coroutines.flow.first
import app.n_zik.android.core.utils.Toaster

/**
 * Handles YouTube syncing for song likes.
 */
object YouTubeSync {

    /**
     * Rotates like state: neutral → liked → disliked → neutral.
     *
     * - **Liked** → pushed to YouTube as "like"
     * - **Neutral** → pushed to YouTube as "unlike"
     * - **Disliked** → local blacklist only, NO YouTube push
     *
     * Also triggers auto-download when liked (if enabled in settings).
     *
     * This function must not be called on **main thread**.
     */
    @UnstableApi
    suspend fun rotateSongLikeState( context: Context, mediaItem: MediaItem ) {
        assert( Looper.myLooper() != Looper.getMainLooper() ) {
            "Cannot run YouTubeSync.rotateSongLikeState on main thread"
        }

        val showDisliked = appContext().preferences.getString(excludeDislikedSongsKey, DislikeMode.Enabled.name)
            ?.let { runCatching { DislikeMode.valueOf(it) }.getOrNull() }?.isEnabled ?: true
        val likeState = changeSongLikeState(context, mediaItem, rotate = showDisliked)

        // Check if we should push to YouTube
        val shouldPushToYt = likeState != false  // Disliked = local only
            && isYouTubeSyncEnabled()
            && appContext().preferences.getBoolean(syncPushSongLikeKey, false)
            && appContext().preferences.getString(syncDirectionKey, SyncDirection.TWO_WAY.name)?.let {
                runCatching { SyncDirection.valueOf(it) }.getOrNull()
            } != SyncDirection.YT_TO_APP
            && isNetworkConnected( context )

        if( shouldPushToYt ) {
            // Try YouTube sync
            val response =
                if( likeState == true )
                    likeVideoOrSong( mediaItem.mediaId )
                else
                    removelikeVideoOrSong( mediaItem.mediaId )

            val ytMessageId = when {
                likeState == true && response.isSuccess -> R.string.sync_youtube_like_success
                likeState == true && response.isFailure -> R.string.sync_youtube_like_failed
                likeState == null && response.isSuccess -> R.string.sync_youtube_unlike_success
                else                                    -> R.string.sync_youtube_unlike_failed
            }
            if( response.isSuccess )
                Toaster.s( ytMessageId )
            else
                Toaster.e( ytMessageId )
        } else {
            // Local only toast
            val messageId = when( likeState ) {
                true -> R.string.library_favorites_added
                false -> R.string.library_dislikes_added
                null -> R.string.library_dislikes_removed
            }
            with( mediaItem.mediaMetadata ) {
                if( title != null )
                    Toaster.s( messageId, "\"$title - $artist\"" )
                else
                    Toaster.s( messageId )
            }
        }
    }

    /**
     * Toggles like state without dislike: neutral → liked → neutral.
     *
     * - **Liked** → pushed to YouTube as "like"
     * - **Neutral** → pushed to YouTube as "unlike"
     *
     * Also triggers auto-download when liked (if enabled in settings).
     *
     * This function must not be called on **main thread**.
     */
    @UnstableApi
    suspend fun toggleSongLikeState( context: Context, mediaItem: MediaItem ) {
        assert( Looper.myLooper() != Looper.getMainLooper() ) {
            "Cannot run YouTubeSync.toggleSongLikeState on main thread"
        }

        val likeState = changeSongLikeState(context, mediaItem, rotate = false)

        val shouldPushToYt = isYouTubeSyncEnabled()
            && appContext().preferences.getBoolean(syncPushSongLikeKey, false)
            && appContext().preferences.getString(syncDirectionKey, SyncDirection.TWO_WAY.name)?.let {
                runCatching { SyncDirection.valueOf(it) }.getOrNull()
            } != SyncDirection.YT_TO_APP
            && isNetworkConnected( context )

        if( shouldPushToYt ) {
            val response =
                if( likeState == true )
                    likeVideoOrSong( mediaItem.mediaId )
                else
                    removelikeVideoOrSong( mediaItem.mediaId )

            val ytMessageId = when {
                likeState == true && response.isSuccess -> R.string.sync_youtube_like_success
                likeState == true && response.isFailure -> R.string.sync_youtube_like_failed
                likeState == null && response.isSuccess -> R.string.sync_youtube_unlike_success
                else                                    -> R.string.sync_youtube_unlike_failed
            }
            if( response.isSuccess )
                Toaster.s( ytMessageId )
            else
                Toaster.e( ytMessageId )
        } else {
            val messageId = when( likeState ) {
                true -> R.string.library_favorites_added
                null -> R.string.library_favorites_removed
                else -> R.string.library_favorites_added
            }
            with( mediaItem.mediaMetadata ) {
                if( title != null )
                    Toaster.s( messageId, "\"$title - $artist\"" )
                else
                    Toaster.s( messageId )
            }
        }
    }

    private suspend fun changeSongLikeState(
        context: Context,
        mediaItem: MediaItem,
        rotate: Boolean,
    ): Boolean? = Database.awaitWriteBatch {
        val id = mediaItem.mediaId
        var previous: Boolean? = null
        var current: Boolean? = null
        transaction {
            previous = songTable.likeState(id).first()
            insertIgnore(mediaItem)
            if (rotate) songTable.rotateLikeState(id)
            else songTable.likeState(id, if (previous == true) null else true)
            current = songTable.likeState(id).first()
        }
        if (previous != true && current == true) {
            MyDownloadHelper.downloadNewlyLiked(context, mediaItem)
        } else if (previous == true && current != true) {
            MyDownloadHelper.removeNewlyUnliked(context, mediaItem)
        }
        current
    }
}

