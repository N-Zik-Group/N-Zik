package app.n_zik.android.bridge.state

import androidx.media3.common.C
import androidx.media3.common.Player
import app.it.fast4x.rimusic.utils.EXPLICIT_BUNDLE_TAG

/** Queue item as read from the player, before its `isLiked` / `isDownloaded` flags are resolved. */
internal data class RawItem(
    val mediaId: String,
    val trackId: String,
    val title: CharSequence?,
    val artist: CharSequence?,
    val hasArtwork: Boolean,
    val durationText: String?,
    /** Duration the player knows for this item (current item once loaded), the fallback of [durationText]. */
    val playerDurationMs: Long? = null,
    /** Explicit flag carried by the item's extras (the title prefix is read by [TrackMapping]). */
    val isExplicitExtra: Boolean = false,
)

/** One read of the player: its queue items and a [PlayerSample] whose queue is still empty. */
internal data class PlayerRead(val items: List<RawItem>, val sample: PlayerSample)

/** Reads a [Player] into the bridge's shape; call it on the player's (main) thread. */
internal object PlayerStateReader {
    private const val DURATION_TEXT_EXTRA = "durationText"

    // Same extras as the phone's `MediaItem.isExplicit` (rim/utils/Utils.kt:405-412)
    private const val STANDARD_EXPLICIT_EXTRA = "androidx.media3.session.EXTRAS_KEY_IS_EXPLICIT"

    fun read(player: Player, sampledAtMs: Long): PlayerRead {
        val shuffle = player.shuffleModeEnabled
        val repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatModeDto.ONE
            Player.REPEAT_MODE_ALL -> RepeatModeDto.ALL
            else -> RepeatModeDto.OFF
        }
        val order = playOrder(player)
        val currentWindow = player.currentMediaItemIndex
        // Only the current item is loaded: its real duration covers a track whose metadata has none
        val currentDurationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        val items = order.map { index ->
            val item = player.getMediaItemAt(index)
            val metadata = item.mediaMetadata
            RawItem(
                mediaId = item.mediaId,
                trackId = TrackMapping.trackIdOf(item.mediaId),
                title = metadata.title,
                artist = metadata.artist,
                hasArtwork = metadata.artworkUri != null,
                durationText = metadata.extras?.getString(DURATION_TEXT_EXTRA),
                playerDurationMs = currentDurationMs.takeIf { index == currentWindow },
                isExplicitExtra = metadata.extras?.let {
                    it.getBoolean(EXPLICIT_BUNDLE_TAG) || it.getBoolean(STANDARD_EXPLICIT_EXTRA)
                } == true,
            )
        }
        val currentIndex = order.indexOf(player.currentMediaItemIndex)
        val sample = PlayerSample(
            queue = emptyList(),
            currentIndex = currentIndex,
            currentTrackId = items.getOrNull(currentIndex)?.trackId,
            isPlaying = items.isNotEmpty() && player.isPlaying,
            speed = player.playbackParameters.speed,
            positionMs = if (items.isEmpty()) 0L else player.currentPosition.coerceAtLeast(0L),
            sampledAtMs = sampledAtMs,
            repeatMode = repeatMode,
            shuffle = shuffle,
        )
        return PlayerRead(items, sample)
    }

    /**
     * Window indexes of [player] in the effective play order (shuffle order when enabled):
     * position `i` of the published queue is window `playOrder(player)[i]` (contract §7.1).
     */
    fun playOrder(player: Player): List<Int> {
        val shuffle = player.shuffleModeEnabled
        val timeline = player.currentTimeline
        val windowCount = timeline.windowCount
        return buildList {
            var index = if (windowCount == 0) C.INDEX_UNSET else timeline.getFirstWindowIndex(shuffle)
            while (index != C.INDEX_UNSET && size < windowCount) {
                add(index)
                index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
            }
        }
    }

    /**
     * Window index of the item a client designates by its effective-order [index] and
     * [trackId], read from [player] right now; `null` when [index] is out of bounds or the
     * item there is another track (contract §9 `QUEUE_MISMATCH`).
     */
    fun windowIndexOf(player: Player, index: Int, trackId: String): Int? {
        val window = playOrder(player).getOrNull(index) ?: return null
        return window.takeIf { TrackMapping.trackIdOf(player.getMediaItemAt(it).mediaId) == trackId }
    }
}
