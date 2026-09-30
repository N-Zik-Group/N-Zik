package app.n_zik.android.bridge.state

import androidx.media3.common.C
import androidx.media3.common.Player

/** Queue item as read from the player, before its `isLiked` / `isDownloaded` flags are resolved. */
internal data class RawItem(
    val mediaId: String,
    val trackId: String,
    val title: CharSequence?,
    val artist: CharSequence?,
    val hasArtwork: Boolean,
    val durationText: String?,
)

/** One read of the player: its queue items and a [PlayerSample] whose queue is still empty. */
internal data class PlayerRead(val items: List<RawItem>, val sample: PlayerSample)

/** Reads a [Player] into the bridge's shape; call it on the player's (main) thread. */
internal object PlayerStateReader {
    private const val DURATION_TEXT_EXTRA = "durationText"

    fun read(player: Player, sampledAtMs: Long): PlayerRead {
        val shuffle = player.shuffleModeEnabled
        val repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatModeDto.ONE
            Player.REPEAT_MODE_ALL -> RepeatModeDto.ALL
            else -> RepeatModeDto.OFF
        }
        val timeline = player.currentTimeline
        val windowCount = timeline.windowCount
        // Contract §7.1: the queue is sent in the effective play order (shuffle order when enabled)
        val order = buildList {
            var index = if (windowCount == 0) C.INDEX_UNSET else timeline.getFirstWindowIndex(shuffle)
            while (index != C.INDEX_UNSET && size < windowCount) {
                add(index)
                index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
            }
        }
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
}
