package app.n_zik.android.bridge.state

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlayerStateReaderTest {

    private val ids = listOf("aaaaaaaaaaa", "bbbbbbbbbbb", "ccccccccccc")

    /** A player over [ids] whose shuffle order is [shuffleOrder] (window indexes). */
    private fun player(
        current: Int,
        shuffle: Boolean = false,
        shuffleOrder: List<Int> = ids.indices.toList(),
        repeatMode: Int = Player.REPEAT_MODE_OFF,
        count: Int = ids.size,
        durationMs: Long = C.TIME_UNSET,
    ): Player {
        val timeline = mockk<Timeline>()
        every { timeline.windowCount } returns count
        every { timeline.getFirstWindowIndex(any()) } answers {
            if (count == 0) C.INDEX_UNSET else if (firstArg()) shuffleOrder.first() else 0
        }
        every { timeline.getNextWindowIndex(any(), any(), any()) } answers {
            val index = firstArg<Int>()
            val order = if (thirdArg()) shuffleOrder else ids.indices.toList()
            order.getOrNull(order.indexOf(index) + 1) ?: C.INDEX_UNSET
        }
        return mockk<Player> {
            every { currentTimeline } returns timeline
            every { shuffleModeEnabled } returns shuffle
            every { this@mockk.repeatMode } returns repeatMode
            every { getMediaItemAt(any()) } answers {
                MediaItem.Builder()
                    .setMediaId(ids[firstArg()])
                    .setMediaMetadata(MediaMetadata.Builder().setTitle("Title ${firstArg<Int>()}").build())
                    .build()
            }
            every { currentMediaItemIndex } returns current
            every { isPlaying } returns true
            every { playbackParameters } returns PlaybackParameters(1.5f)
            every { currentPosition } returns 42_000L
            every { duration } returns durationMs
        }
    }

    @Test
    fun `queue follows the play order and currentIndex points into it`() {
        val read = PlayerStateReader.read(player(current = 0), sampledAtMs = 1_000L)

        assertEquals(ids, read.items.map { it.trackId })
        assertEquals(0, read.sample.currentIndex)
        assertEquals("aaaaaaaaaaa", read.sample.currentTrackId)
        assertEquals(1.5f, read.sample.speed)
        assertEquals(42_000L, read.sample.positionMs)
        assertEquals(1_000L, read.sample.sampledAtMs)
    }

    @Test
    fun `shuffle order is the published order and the index is mapped into it`() {
        // Window 0 ("a") is current and plays second in the shuffle order [2, 0, 1]
        val read = PlayerStateReader.read(player(current = 0, shuffle = true, shuffleOrder = listOf(2, 0, 1)), 0L)

        assertEquals(listOf("ccccccccccc", "aaaaaaaaaaa", "bbbbbbbbbbb"), read.items.map { it.trackId })
        assertEquals(1, read.sample.currentIndex)
        assertEquals("aaaaaaaaaaa", read.sample.currentTrackId)
    }

    @Test
    fun `effective index maps back to the window index of the same track`() {
        // Shuffle order [2, 0, 1]: published queue is c, a, b
        val shuffled = player(current = 0, shuffle = true, shuffleOrder = listOf(2, 0, 1))

        assertEquals(listOf(2, 0, 1), PlayerStateReader.playOrder(shuffled))
        assertEquals(2, PlayerStateReader.windowIndexOf(shuffled, 0, "ccccccccccc"))
        assertEquals(0, PlayerStateReader.windowIndexOf(shuffled, 1, "aaaaaaaaaaa"))
        assertEquals(1, PlayerStateReader.windowIndexOf(shuffled, 2, "bbbbbbbbbbb"))
        assertEquals(1, PlayerStateReader.windowIndexOf(player(current = 0), 1, "bbbbbbbbbbb"))
    }

    @Test
    fun `index out of bounds or another track maps to nothing`() {
        val shuffled = player(current = 0, shuffle = true, shuffleOrder = listOf(2, 0, 1))

        assertNull(PlayerStateReader.windowIndexOf(shuffled, 0, "aaaaaaaaaaa"))
        assertNull(PlayerStateReader.windowIndexOf(shuffled, 3, "aaaaaaaaaaa"))
        assertNull(PlayerStateReader.windowIndexOf(shuffled, -1, "aaaaaaaaaaa"))
        assertNull(PlayerStateReader.windowIndexOf(player(current = 0, count = 0), 0, "aaaaaaaaaaa"))
    }

    @Test
    fun `repeat modes map to the contract values`() {
        assertEquals(RepeatModeDto.ONE, PlayerStateReader.read(player(0, repeatMode = Player.REPEAT_MODE_ONE), 0L).sample.repeatMode)
        assertEquals(RepeatModeDto.ALL, PlayerStateReader.read(player(0, repeatMode = Player.REPEAT_MODE_ALL), 0L).sample.repeatMode)
        assertEquals(RepeatModeDto.OFF, PlayerStateReader.read(player(0), 0L).sample.repeatMode)
    }

    @Test
    fun `empty timeline reads as the empty state`() {
        val read = PlayerStateReader.read(player(current = 0, count = 0), 0L)

        assertEquals(emptyList<RawItem>(), read.items)
        assertEquals(-1, read.sample.currentIndex)
        assertNull(read.sample.currentTrackId)
        assertFalse(read.sample.isPlaying)
        assertEquals(0L, read.sample.positionMs)
    }

    @Test
    fun `only the current item carries the player's duration`() {
        val read = PlayerStateReader.read(player(current = 1, durationMs = 222_000L), 0L)

        assertEquals(listOf(null, 222_000L, null), read.items.map { it.playerDurationMs })
    }

    @Test
    fun `an unknown player duration gives no fallback`() {
        val read = PlayerStateReader.read(player(current = 0), 0L)

        assertEquals(listOf<Long?>(null, null, null), read.items.map { it.playerDurationMs })
    }
}