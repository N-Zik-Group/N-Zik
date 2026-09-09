package app.kreate.android.me.knighthat.sync

import app.n_zik.android.core.database.*
import app.it.fast4x.rimusic.utils.showDislikedPlaylistKey
import app.it.fast4x.rimusic.utils.excludeDislikedSongsKey
import app.it.fast4x.rimusic.enums.DislikeMode

import android.content.Context
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import it.fast4x.innertube.YtMusic.likeVideoOrSong
import it.fast4x.innertube.YtMusic.removelikeVideoOrSong
import app.n_zik.android.core.database.Database
import app.n_zik.android.appContext
import app.n_zik.android.download.utils.MyDownloadHelper
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import app.it.fast4x.rimusic.utils.isNetworkConnected
import app.it.fast4x.rimusic.utils.syncPushSongLikeKey
import app.it.fast4x.rimusic.utils.syncDirectionKey
import app.it.fast4x.rimusic.enums.SyncDirection
import app.it.fast4x.rimusic.utils.preferences
import kotlinx.coroutines.flow.first
import app.kreate.android.me.knighthat.utils.Toaster

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

        // Get state BEFORE rotation to determine what the NEW state will be
        val currentState = Database.songTable.likeState( mediaItem.mediaId ).first()
        val showDisliked = appContext().preferences.getString(excludeDislikedSongsKey, DislikeMode.Enabled.name)?.let { runCatching { DislikeMode.valueOf(it) }.getOrNull() }?.isEnabled ?: true

        Database.insertIgnore( mediaItem )
        if (showDisliked) {
            Database.songTable.rotateLikeState( mediaItem.mediaId )
        } else {
            Database.songTable.toggleLike( mediaItem.mediaId )
        }

        // Determine new state based on rotation: neutral → liked → disliked → neutral
        val likeState = if (showDisliked) {
            when(currentState) {
                null -> true    // neutral → liked
                true -> false   // liked → disliked
                false -> null   // disliked → neutral
            }
        } else {
            when(currentState) {
                null -> true    // neutral → liked
                true -> null    // liked → neutral
                false -> true   // disliked → liked
            }
        }

        MyDownloadHelper.downloadOnLike( mediaItem, likeState, context )

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
                likeState == true && response.isSuccess -> R.string.songs_liked_yt
                likeState == true && response.isFailure -> R.string.songs_liked_yt_failed
                likeState == null && response.isSuccess -> R.string.song_unliked_yt
                else                                    -> R.string.songs_unliked_yt_failed
            }
            if( response.isSuccess )
                Toaster.s( ytMessageId )
            else
                Toaster.e( ytMessageId )
        } else {
            // Local only toast
            val messageId = when( likeState ) {
                true -> R.string.added_to_favorites
                false -> R.string.added_to_dislikes
                null -> R.string.removed_from_dislikes
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

        val currentState = Database.songTable.likeState( mediaItem.mediaId ).first()

        Database.insertIgnore( mediaItem )
        Database.songTable.toggleLike( mediaItem.mediaId )

        val likeState = when(currentState) {
            null -> true    // neutral → liked
            true -> null    // liked → neutral
            false -> true   // disliked → liked
        }

        MyDownloadHelper.downloadOnLike( mediaItem, likeState, context )

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
                likeState == true && response.isSuccess -> R.string.songs_liked_yt
                likeState == true && response.isFailure -> R.string.songs_liked_yt_failed
                likeState == null && response.isSuccess -> R.string.song_unliked_yt
                else                                    -> R.string.songs_unliked_yt_failed
            }
            if( response.isSuccess )
                Toaster.s( ytMessageId )
            else
                Toaster.e( ytMessageId )
        } else {
            val messageId = when( likeState ) {
                true -> R.string.added_to_favorites
                null -> R.string.removed_from_favorites
                else -> R.string.added_to_favorites
            }
            with( mediaItem.mediaMetadata ) {
                if( title != null )
                    Toaster.s( messageId, "\"$title - $artist\"" )
                else
                    Toaster.s( messageId )
            }
        }
    }
}

