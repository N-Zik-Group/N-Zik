package app.kreate.android.me.knighthat.sync

import app.n_zik.android.core.database.*

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
     * Rotates like state through 3 states: neutral → liked → disliked → neutral.
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

        Database.insertIgnore( mediaItem )
        Database.songTable.rotateLikeState( mediaItem.mediaId )

        val likeState = Database.songTable.likeState( mediaItem.mediaId ).first()
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
}

