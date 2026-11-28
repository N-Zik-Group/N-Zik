package app.n_zik.android.playback.services

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.utils.isExplicit
import app.n_zik.android.utils.mediaItems

/** An in-memory handoff for an explicit restart, independent of persistent queue preferences. */
@UnstableApi
internal class SettingsRestartPlayback private constructor(
    private val items: List<MediaItem>,
    private val index: Int,
    private val positionMs: Long,
    private val shouldPlay: Boolean,
    private val shuffleOrder: List<Int>?,
) {
    fun restore(player: Player, filterExplicit: Boolean, setShuffleOrder: (IntArray) -> Unit) {
        val retained = items.indices.filter { !filterExplicit || !items[it].isExplicit }
        if (retained.isEmpty()) return
        val restoredIndex = retained.indexOfFirst { it >= index }.takeIf { it >= 0 } ?: retained.lastIndex
        val restoredPosition = if (retained[restoredIndex] == index) positionMs else C.TIME_UNSET
        player.setMediaItems(retained.map(items::get), restoredIndex, restoredPosition)
        if (shuffleOrder != null) {
            val restoredIndices = retained.withIndex().associate { (new, old) -> old to new }
            player.shuffleModeEnabled = true
            setShuffleOrder(shuffleOrder.mapNotNull(restoredIndices::get).toIntArray())
        }
        player.playWhenReady = shouldPlay
        if (shouldPlay) player.prepare()
    }

    companion object {
        fun capture(player: Player): SettingsRestartPlayback? {
            val timeline = player.currentTimeline
            val index = player.currentMediaItemIndex
            if (index !in 0 until timeline.windowCount) return null
            return SettingsRestartPlayback(
                items = timeline.mediaItems,
                index = index,
                positionMs = player.contentPosition,
                shouldPlay = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
                shuffleOrder = if (player.shuffleModeEnabled) {
                    generateSequence(timeline.getFirstWindowIndex(true)) { previous ->
                        timeline.getNextWindowIndex(previous, Player.REPEAT_MODE_OFF, true)
                            .takeIf { it != C.INDEX_UNSET }
                    }.take(timeline.windowCount).toList()
                } else null,
            )
        }
    }
}
