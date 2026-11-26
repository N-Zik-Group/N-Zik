package app.n_zik.android.utils

import app.n_zik.android.enums.DislikeMode

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastMap
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.enums.DurationInMinutes
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import app.n_zik.android.core.utils.Toaster
import timber.log.Timber
import java.util.ArrayDeque
import app.n_zik.android.core.database.Database
import app.n_zik.android.playback.utils.Shuffler
import kotlinx.coroutines.withContext
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.playback.services.upsertSongInfo
import app.n_zik.android.playback.services.launchStoredDurationSelection

var GlobalVolume: Float = 0.5f

fun Player.restoreGlobalVolume() {
    volume = GlobalVolume
}

fun Player.saveGlobalVolume() {
    GlobalVolume = volume
}

fun Player.setGlobalVolume(v: Float) {
    GlobalVolume = v
}

fun Player.getGlobalVolume(): Float {
    return GlobalVolume
}

fun Player.isNowPlaying(mediaId: String): Boolean {
    val cleanId = mediaId.split("/").lastOrNull() ?: mediaId
    val cleanCurrentId = currentMediaItem?.mediaId?.split("/")?.lastOrNull() ?: currentMediaItem?.mediaId
    return cleanId == cleanCurrentId
}

val Player.currentWindow: Timeline.Window?
    get() = if (mediaItemCount == 0) null else currentTimeline.getWindow(currentMediaItemIndex, Timeline.Window())

val Timeline.mediaItems: List<MediaItem>
    get() = List(windowCount) {
        getWindow(it, Timeline.Window()).mediaItem
    }

inline val Timeline.windows: List<Timeline.Window>
    get() = List(windowCount) {
        getWindow(it, Timeline.Window())
    }

val Player.shouldBePlaying: Boolean
    get() = !(playbackState == Player.STATE_ENDED || !playWhenReady)

fun Player.removeMediaItems(range: IntRange) = removeMediaItems(range.first, range.last + 1)

fun Player.seamlessPlay(mediaItem: MediaItem) {
    if (mediaItem.mediaId == currentMediaItem?.mediaId) {
        if (currentMediaItemIndex > 0) removeMediaItems(0 until currentMediaItemIndex)
        if (currentMediaItemIndex < mediaItemCount - 1)
            removeMediaItems(currentMediaItemIndex + 1 until mediaItemCount)
    } else forcePlay(mediaItem)
}


fun Player.shuffleQueue(): Boolean = Shuffler.queue(this)

@SuppressLint("Range")
@UnstableApi
fun Player.playAtMedia(mediaItems: List<MediaItem>, mediaId: String) {
    Timber.tag("Player").d("mediaItem-playAtMedia: %d", mediaItems.size)
    if (mediaItems.isEmpty()) return
    val itemIndex = findMediaItemIndexById(mediaId)

    Timber.tag("Player").d("mediaItem-playAtMedia: %d", itemIndex)
    setMediaItems(mediaItems, itemIndex, C.TIME_UNSET)
    prepare()
    restoreGlobalVolume()
    playWhenReady = true

}

fun Player.forcePlay(mediaItem: MediaItem) {
    setMediaItem(mediaItem.cleaned, true)
    prepare()
    restoreGlobalVolume()
    playWhenReady = true
}

fun Player.playAtIndex(mediaItemIndex: Int) {
    seekTo(mediaItemIndex, C.TIME_UNSET)
    prepare()
    restoreGlobalVolume()
    playWhenReady = true
}

@SuppressLint("Range")
@UnstableApi
fun Player.forcePlayAtIndex(mediaItems: List<MediaItem>, mediaItemIndex: Int) {
    launchForcePlayAtIndex(mediaItems, mediaItemIndex)
}

@SuppressLint("Range")
@UnstableApi
internal fun Player.launchForcePlayAtIndex(
    mediaItems: List<MediaItem>,
    mediaItemIndex: Int,
    canApplyQueue: () -> Boolean = { true },
    onQueueApplied: () -> Unit = {},
): Job? {
    if (mediaItems.isEmpty()) return null

    // This will prevent UI from freezing up during conversion
    return launchStoredDurationSelection(this) { selection ->
        val cleanedMediaItems = selection.enrich(
            mediaItems.fastMap( MediaItem::cleaned ).fastDistinctBy( MediaItem::mediaId )
        )
        // Use the cleaned mediaId for lookup to ensure consistent comparison
        val targetMediaId = mediaItems.getOrNull(mediaItemIndex)?.cleaned?.mediaId
        val foundIndex = if (targetMediaId != null) {
            cleanedMediaItems.indexOfFirst { it.mediaId == targetMediaId }
        } else {
            -1
        }
        val newIndex = if (foundIndex >= 0) {
            foundIndex
        } else {
            Timber.tag("Player").w("forcePlayAtIndex: target index $mediaItemIndex not found after dedup, falling back to 0")
            0
        }

        // Publish known local lengths before optional network metadata prefetch.
        var applied = false
        selection.publish {
            if (!canApplyQueue()) return@publish
            setMediaItems( cleanedMediaItems, newIndex, C.TIME_UNSET )
            prepare()
            restoreGlobalVolume()
            playWhenReady = true
            applied = true
            onQueueApplied()
        }
        if (!applied) return@launchStoredDurationSelection

        // Pre-fetch full metadata (with browse IDs) asynchronously.
        // This ensures the UI shows the correct artist names + browse IDs without delaying playback.
        val targetItem = cleanedMediaItems.getOrNull(newIndex)
        if (targetItem != null) {
            val videoId = targetItem.mediaId.substringAfterLast('/')
            val explicitAlbumId = targetItem.mediaMetadata.extras?.getString("albumId")?.takeIf(String::isNotBlank)
            val exactTrackAlbum = if (targetItem.isLocal) null else try {
                upsertSongInfo(videoId, explicitAlbumId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.tag("PlayerPrefetch").w(error, "Metadata prefetch failed for $videoId")
                null
            }
            // The service observes the committed artist and title. Keep the verified exact
            // endpoint as an album hint when several stored editions make it ambiguous.
            if (explicitAlbumId == null && exactTrackAlbum != null) {
                withContext(NzikDispatchers.UI) {
                    selection.ensureCurrent()
                    for (index in 0 until mediaItemCount) {
                        val queued = getMediaItemAt(index)
                        if (queued.mediaId == targetItem.mediaId && queued.mediaMetadata.extras?.getString("albumId").isNullOrBlank()) {
                            replaceMediaItem(index, queued.withPlaybackAlbum(exactTrackAlbum.id, exactTrackAlbum.title, derived = true))
                            break
                        }
                    }
                }
            }
        }
    }
}

@UnstableApi
fun Player.forcePlayFromBeginning(mediaItems: List<MediaItem>) =
    forcePlayAtIndex(mediaItems, 0)

fun Player.forceSeekToPrevious() {
    if (hasPreviousMediaItem() || currentPosition > maxSeekToPreviousPosition) {
        seekToPrevious()
    } else if (mediaItemCount > 0) {
        seekTo(mediaItemCount - 1, C.TIME_UNSET)
    }
}

fun Player.forceSeekToNext() =
    if (hasNextMediaItem()) seekToNext() else seekTo(0, C.TIME_UNSET)

fun Player.playNext() {
    seekToNextMediaItem()
    //seekToNext()
    prepare()
    restoreGlobalVolume()
    playWhenReady = true
}

fun Player.playPrevious() {
    seekToPreviousMediaItem()
    //seekToPrevious()
    prepare()
    restoreGlobalVolume()
    playWhenReady = true
}

@UnstableApi
fun Player.addNext(mediaItem: MediaItem, context: Context? = null) {
    if (context != null && excludeMediaItem(mediaItem, context)) return

    val itemIndex = findMediaItemIndexById(mediaItem.mediaId)
    if (itemIndex >= 0) removeMediaItem(itemIndex)

    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        forcePlay(mediaItem)
    } else {
        addMediaItem(currentMediaItemIndex + 1, mediaItem.cleaned)
    }
}

@UnstableApi
fun Player.addNext(mediaItems: List<MediaItem>, context: Context? = null) {
    val filteredMediaItems = if (context != null) excludeMediaItems(mediaItems, context)
    else mediaItems
    if (filteredMediaItems.isEmpty()) return

    filteredMediaItems.forEach { mediaItem ->
        val itemIndex = findMediaItemIndexById(mediaItem.mediaId)
        if (itemIndex >= 0) removeMediaItem(itemIndex)
    }

    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        setMediaItems(filteredMediaItems.map { it.cleaned })

        if( playbackState == Player.STATE_IDLE )
            prepare()

        play()
    } else {
        addMediaItems(currentMediaItemIndex + 1, filteredMediaItems.map { it.cleaned })
    }

}


fun Player.enqueue(mediaItem: MediaItem, context: Context? = null) {
     if (context != null && excludeMediaItem(mediaItem, context)) return

    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        forcePlay(mediaItem)
    } else {
        addMediaItem(mediaItemCount, mediaItem.cleaned)
    }
}


@UnstableApi
fun Player.enqueue(mediaItems: List<MediaItem>, context: Context? = null) {
    val filteredMediaItems = if (context != null) excludeMediaItems(mediaItems, context)
    else mediaItems
    if (filteredMediaItems.isEmpty()) return

    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        forcePlayFromBeginning(filteredMediaItems)
    } else {
        addMediaItems(mediaItemCount, filteredMediaItems.map { it.cleaned })
    }
}


fun Player.findNextMediaItemById(mediaId: String): MediaItem? = runCatching {
    for (i in currentMediaItemIndex until mediaItemCount) {
        if (getMediaItemAt(i).mediaId == mediaId) return getMediaItemAt(i)
    }
    return null
}.getOrNull()

fun Player.findMediaItemIndexById(mediaId: String): Int {
    // Strip prefix for robust comparison
    val cleanSearchId = mediaId.split("/").lastOrNull() ?: mediaId
    // Search the entire queue, not just from currentMediaItemIndex
    for (i in 0 until mediaItemCount) {
        try {
            val item = getMediaItemAt(i)
            val cleanItemId = item.mediaId.split("/").lastOrNull() ?: item.mediaId
            if (cleanItemId == cleanSearchId) {
                return i
            }
        } catch (e: Exception) {
            // Index might be out of bounds if queue changed during iteration
            Timber.tag("Player").e(e, "Error accessing media item at index $i")
            continue
        }
    }
    return -1
}

fun Player.excludeMediaItems(mediaItems: List<MediaItem>, context: Context): List<MediaItem> {
    var filteredMediaItems = mediaItems
    runCatching {
        val preferences = context.preferences
        val excludeSongWithDurationLimit =
            preferences.getEnum(excludeSongsWithDurationLimitKey, DurationInMinutes.Disabled)

        if (excludeSongWithDurationLimit != DurationInMinutes.Disabled) {
            filteredMediaItems = filteredMediaItems.filter {
                val duration = it.mediaMetadata.extras?.getString("durationText")?.let(::durationTextToMillis)
                duration == null || duration < excludeSongWithDurationLimit.asMillis
            }

            val excludedSongs = mediaItems.size - filteredMediaItems.size
            if (excludedSongs > 0)
                Toaster.n( R.string.player_excluded_song_count_format, arrayOf( excludedSongs ) )
        }

        // Filter disliked songs if setting is enabled
        val excludeDislikedSongs = preferences.getString(excludeDislikedSongsKey, DislikeMode.Enabled.name)?.let { runCatching { DislikeMode.valueOf(it) }.getOrNull() } ?: DislikeMode.Enabled
        if (excludeDislikedSongs.isEnabled) {
            // runBlocking justified: excludeMediaItems is a public non-suspend API with 11+ callers; the Room query runs on the named dispatcher while the caller thread waits (gh-606)
            val dislikedSongIds = runBlocking(NzikDispatchers.DATA) {
                Database.songTable.getAllDislikedIds()
            }
            if (dislikedSongIds.isNotEmpty()) {
                val beforeCount = filteredMediaItems.size
                filteredMediaItems = filteredMediaItems.filter {
                    !dislikedSongIds.contains(it.mediaId)
                }
                val excludedDisliked = beforeCount - filteredMediaItems.size
                if (excludedDisliked > 0)
                    Timber.tag("Player").d("Excluded $excludedDisliked disliked songs")
            }
        }

        // Filter songs from disliked artists if setting is enabled
        val excludeDislikedArtists = preferences.getString(excludeDislikedArtistsKey, DislikeMode.Enabled.name)?.let { runCatching { DislikeMode.valueOf(it) }.getOrNull() } ?: DislikeMode.Enabled
        if (excludeDislikedArtists.isEnabled) {
            // runBlocking justified: excludeMediaItems is a public non-suspend API with 11+ callers; the Room query runs on the named dispatcher while the caller thread waits (gh-606)
            val dislikedArtistSongIds = runBlocking(NzikDispatchers.DATA) {
                Database.songTable.getSongsByDislikedArtists()
            }
            if (dislikedArtistSongIds.isNotEmpty()) {
                val beforeCount = filteredMediaItems.size
                filteredMediaItems = filteredMediaItems.filter {
                    !dislikedArtistSongIds.contains(it.mediaId)
                }
                val excludedDisliked = beforeCount - filteredMediaItems.size
                if (excludedDisliked > 0)
                    Timber.tag("Player").d("Excluded $excludedDisliked songs from disliked artists")
            }
        }

        // Filter songs from disliked albums if setting is enabled
        val excludeDislikedAlbums = preferences.getString(excludeDislikedAlbumsKey, DislikeMode.Enabled.name)?.let { runCatching { DislikeMode.valueOf(it) }.getOrNull() } ?: DislikeMode.Enabled
        if (excludeDislikedAlbums.isEnabled) {
            // runBlocking justified: excludeMediaItems is a public non-suspend API with 11+ callers; the Room query runs on the named dispatcher while the caller thread waits (gh-606)
            val dislikedAlbumSongIds = runBlocking(NzikDispatchers.DATA) {
                Database.songTable.getSongsByDislikedAlbums()
            }
            if (dislikedAlbumSongIds.isNotEmpty()) {
                val beforeCount = filteredMediaItems.size
                filteredMediaItems = filteredMediaItems.filter {
                    !dislikedAlbumSongIds.contains(it.mediaId)
                }
                val excludedDisliked = beforeCount - filteredMediaItems.size
                if (excludedDisliked > 0)
                    Timber.tag("Player").d("Excluded $excludedDisliked songs from disliked albums")
            }
        }
    }.onFailure {
        Timber.tag("Player").e(it.message)
    }

    return filteredMediaItems
}
fun Player.excludeMediaItem(mediaItem: MediaItem, context: Context): Boolean {
    runCatching {
        val preferences = context.preferences
        val excludeSongWithDurationLimit =
            preferences.getEnum(excludeSongsWithDurationLimitKey, DurationInMinutes.Disabled)
        if (excludeSongWithDurationLimit != DurationInMinutes.Disabled) {
            val duration = mediaItem.mediaMetadata.extras?.getString("durationText")
                ?.let(::durationTextToMillis) ?: return false
            val excludedSong = duration <= excludeSongWithDurationLimit.asMillis

            if (excludedSong)
                Toaster.n( R.string.player_excluded_song_count_format, arrayOf( 1 ) )

            return excludedSong
        }
    }.onFailure {
        Timber.tag("Player").e(it.message)
        return false
    }

    return false

}

val Player.mediaItems: List<MediaItem>
    get() = object : AbstractList<MediaItem>() {
        override val size: Int
            get() = mediaItemCount

        override fun get(index: Int): MediaItem = getMediaItemAt(index)
    }

fun Player.getCurrentQueueIndex(): Int {
    if (currentTimeline.isEmpty) {
        return -1
    }
    var index = 0
    var currentMediaItemIndex = currentMediaItemIndex
    while (currentMediaItemIndex != C.INDEX_UNSET) {
        currentMediaItemIndex = currentTimeline.getPreviousWindowIndex(currentMediaItemIndex, REPEAT_MODE_OFF, shuffleModeEnabled)
        if (currentMediaItemIndex != C.INDEX_UNSET) {
            index++
        }
    }
    return index
}

fun Player.togglePlayPause() {
    if (!playWhenReady && playbackState == Player.STATE_IDLE) {
        prepare()
    }
    playWhenReady = !playWhenReady
}

fun Player.toggleRepeatMode() {
    repeatMode = when (repeatMode) {
        REPEAT_MODE_OFF -> REPEAT_MODE_ALL
        REPEAT_MODE_ALL -> REPEAT_MODE_ONE
        REPEAT_MODE_ONE -> REPEAT_MODE_OFF
        else -> throw IllegalStateException()
    }
}

fun Player.toggleShuffleMode() {
    shuffleModeEnabled = !shuffleModeEnabled
}

fun Player.getQueueWindows(): List<Timeline.Window> {
    val timeline = currentTimeline
    if (timeline.isEmpty) {
        return emptyList()
    }
    val queue = ArrayDeque<Timeline.Window>()
    val queueSize = timeline.windowCount

    val currentMediaItemIndex: Int = currentMediaItemIndex
    queue.add(timeline.getWindow(currentMediaItemIndex, Timeline.Window()))

    var firstMediaItemIndex = currentMediaItemIndex
    var lastMediaItemIndex = currentMediaItemIndex
    val shuffleModeEnabled = shuffleModeEnabled
    while ((firstMediaItemIndex != C.INDEX_UNSET || lastMediaItemIndex != C.INDEX_UNSET) && queue.size < queueSize) {
        if (lastMediaItemIndex != C.INDEX_UNSET) {
            lastMediaItemIndex = timeline.getNextWindowIndex(lastMediaItemIndex, REPEAT_MODE_OFF, shuffleModeEnabled)
            if (lastMediaItemIndex != C.INDEX_UNSET) {
                queue.add(timeline.getWindow(lastMediaItemIndex, Timeline.Window()))
            }
        }
        if (firstMediaItemIndex != C.INDEX_UNSET && queue.size < queueSize) {
            firstMediaItemIndex = timeline.getPreviousWindowIndex(firstMediaItemIndex, REPEAT_MODE_OFF, shuffleModeEnabled)
            if (firstMediaItemIndex != C.INDEX_UNSET) {
                queue.addFirst(timeline.getWindow(firstMediaItemIndex, Timeline.Window()))
            }
        }
    }
    return queue.toList()
}



