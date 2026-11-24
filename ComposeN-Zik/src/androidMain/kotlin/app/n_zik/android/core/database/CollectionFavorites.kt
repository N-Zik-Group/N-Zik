package app.n_zik.android.core.database

import android.content.Context
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import app.n_zik.android.R
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.utils.canPushToYTM
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.syncPushSongLikeKey
import app.n_zik.innertube.YtMusic

internal data class CollectionFavoritesChange(val like: Boolean, val songIds: List<String>)

internal data class CollectionFavoritesSelection(
    val owner: ProfileStorageIdentity,
    val change: CollectionFavoritesChange,
)

/** Matches the identifier used by the library's MediaItem insertion. */
internal fun collectionFavoriteSongId(mediaId: String): String = mediaId.substringAfterLast('/')

/** Freezes the IDs affected by the single action offered for this collection. */
internal fun collectionFavoritesChange(
    songIds: Set<String>,
    isLiked: (String) -> Boolean,
): CollectionFavoritesChange? {
    if (songIds.isEmpty()) return null
    val missingLikes = songIds.filterNot(isLiked)
    return if (missingLikes.isEmpty()) CollectionFavoritesChange(false, songIds.toList())
    else CollectionFavoritesChange(true, missingLikes)
}

/** Applies confirmed favorite changes locally and queues supported YouTube pushes after commit. */
internal object CollectionFavorites {
    private const val ROOM_QUERY_BATCH_SIZE = 500
    private const val YOUTUBE_REQUEST_INTERVAL_MS = 1_000L
    private val youtubeVideoId = Regex("[A-Za-z0-9_-]{11}")
    private val youtubePushes by lazy {
        Channel<suspend () -> Unit>(Channel.UNLIMITED).also { queue ->
            NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                for (push in queue) {
                    runCatching { push() }.onFailure { error ->
                        if (error is CancellationException) throw error
                        Timber.tag("CollectionFavorites").w(error, "Unable to sync collection favorites")
                        Toaster.e(R.string.library_collection_favorites_sync_failed)
                    }
                }
            }
        }
    }

    /** Reads one snapshot for the count shown at confirmation. */
    suspend fun resolve(context: Context, songIds: Set<String>): CollectionFavoritesSelection? {
        val owner = ProfileWorkOwner.capture(context) ?: return null
        if (!ProfileWorkOwner.isBound(context, owner)) return null
        val likedIds = mutableSetOf<String>()
        Database.transaction {
            songIds.chunked(ROOM_QUERY_BATCH_SIZE).forEach { ids ->
                likedIds.addAll(songTable.findLikedIdsDirect(ids))
            }
        }
        return collectionFavoritesChange(songIds) { it in likedIds }
            ?.let { CollectionFavoritesSelection(owner, it) }
    }

    /** Skips confirmed items whose state already matches the requested result. */
    suspend fun apply(
        context: Context,
        itemsBySongId: Map<String, MediaItem>,
        selection: CollectionFavoritesSelection,
    ): Int = Database.awaitWriteBatch {
        val (owner, change) = selection
        if (!ProfileWorkOwner.isBound(context, owner)) return@awaitWriteBatch 0
        val changed = mutableListOf<MediaItem>()
        transaction {
            change.songIds.chunked(ROOM_QUERY_BATCH_SIZE).forEach { ids ->
                val existingSongs = songTable.findByIdsDirect(ids).associateBy { it.id }
                ids.forEach { id ->
                    val item = itemsBySongId.getValue(id)
                    val existingSong = existingSongs[id]
                    val currentlyLiked = (existingSong?.likedAt ?: 0L) > 0L
                    if (currentlyLiked != change.like) {
                        if (existingSong == null) insertIgnore(item, autoFix = false)
                        val newState = true.takeIf { change.like }
                        if (songTable.likeState(id, newState) > 0) changed.add(item)
                    }
                }
            }
        }

        if (!ProfileWorkOwner.isBound(context, owner)) return@awaitWriteBatch 0
        if (change.like) {
            changed.forEach { MyDownloadHelper.downloadNewlyLiked(context, it) }
        }
        val remoteIds = changed.map(MediaItem::mediaId).filter(youtubeVideoId::matches)
        if (remoteIds.isNotEmpty() && canPushLikes(context, owner)) {
            youtubePushes.trySend { pushLikes(context, owner, remoteIds, change.like) }.getOrThrow()
        }
        changed.size
    }

    private fun canPushLikes(context: Context, owner: ProfileStorageIdentity): Boolean =
        ProfileWorkOwner.isBound(context, owner) && isNetworkConnected(context) &&
            isYouTubeSyncEnabled() && canPushToYTM() && context.preferences.getBoolean(syncPushSongLikeKey, false)

    private suspend fun pushLikes(
        context: Context,
        owner: ProfileStorageIdentity,
        songIds: List<String>,
        like: Boolean,
    ) {
        var failed = false
        for (id in songIds) {
            if (!ProfileWorkOwner.isBound(context, owner)) return
            val current = Database.songTable.findByIdDirect(id) ?: continue
            if (((current.likedAt ?: 0L) > 0L) != like) continue
            if (!canPushLikes(context, owner)) break

            val result = if (like) YtMusic.likeVideoOrSong(id) else YtMusic.removelikeVideoOrSong(id)
            result.onFailure { error ->
                if (error is CancellationException) throw error
                Timber.tag("CollectionFavorites").w(error, "Unable to sync collection favorite")
                failed = true
            }
            delay(YOUTUBE_REQUEST_INTERVAL_MS)
        }
        if (failed && ProfileWorkOwner.isBound(context, owner)) {
            Toaster.e(R.string.library_collection_favorites_sync_failed)
        }
    }
}
