package app.n_zik.android.bridge.state

import androidx.media3.common.C

/**
 * Player state read at one instant, in the shape the bridge publishes (contract §7.1).
 * [positionMs] is the position at [sampledAtMs] (epoch ms); it moves on its own while
 * [isPlaying], so it never takes part in change detection (see [BridgeStateHub]).
 */
internal data class PlayerSample(
    /** Tracks in the effective play order. */
    val queue: List<TrackDto>,
    /** Index of the current track in [queue], `-1` when the queue is empty. */
    val currentIndex: Int,
    val currentTrackId: String?,
    val isPlaying: Boolean,
    /** Since 1.4: the player is in `STATE_BUFFERING` (loading a track, or rebuffering while playing). */
    val isBuffering: Boolean = false,
    /** Since 1.5 (contract §7.1): the player's live duration, [C.TIME_UNSET] while it is not known yet. */
    val durationMs: Long = C.TIME_UNSET,
    val speed: Float,
    val positionMs: Long,
    val sampledAtMs: Long,
    val repeatMode: RepeatModeDto,
    val shuffle: Boolean,
) {
    companion object {
        /** No player service bound, or nothing loaded (contract §7.1: empty snapshot). */
        val EMPTY = PlayerSample(
            queue = emptyList(),
            currentIndex = -1,
            currentTrackId = null,
            isPlaying = false,
            speed = 1f,
            positionMs = 0L,
            sampledAtMs = 0L,
            repeatMode = RepeatModeDto.OFF,
            shuffle = false,
        )
    }
}
