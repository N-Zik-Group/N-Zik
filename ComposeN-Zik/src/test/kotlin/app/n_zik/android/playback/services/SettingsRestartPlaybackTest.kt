package app.n_zik.android.playback.services

import android.app.Application
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsRestartPlaybackTest {
    private val items = List(3) { index ->
        val extras = Bundle().apply { putBoolean("androidx.media3.session.EXTRAS_KEY_IS_EXPLICIT", index == 1) }
        MediaItem.Builder().setMediaId("track-$index")
            .setMediaMetadata(MediaMetadata.Builder().setExtras(extras).build())
            .build()
    }

    @Test
    fun `playing queue resumes at the captured item and position`() {
        val replacement = mockk<Player>(relaxed = true)

        assertNotNull(SettingsRestartPlayback.capture(source(shouldPlay = true))).restore(replacement, false) {}

        verify(exactly = 1) { replacement.setMediaItems(items, 1, 43_000L) }
        verify(exactly = 1) { replacement.playWhenReady = true }
        verify(exactly = 1) { replacement.prepare() }
    }

    @Test
    fun `paused queue stays paused without preparing audio`() {
        val replacement = mockk<Player>(relaxed = true)

        assertNotNull(SettingsRestartPlayback.capture(source(shouldPlay = false))).restore(replacement, false) {}

        verify(exactly = 1) { replacement.setMediaItems(items, 1, 43_000L) }
        verify(exactly = 1) { replacement.playWhenReady = false }
        verify(exactly = 0) { replacement.prepare() }
    }

    @Test
    fun `empty queue has no restart handoff`() {
        val source = mockk<Player>()
        every { source.currentTimeline } returns mockk { every { windowCount } returns 0 }
        every { source.currentMediaItemIndex } returns 0

        assertNull(SettingsRestartPlayback.capture(source))
    }

    @Test
    fun `restoring a shuffled queue remaps traversal after filtering explicit tracks`() {
        val replacement = mockk<Player>(relaxed = true)
        val source = source(shouldPlay = false, shuffle = true)
        val setShuffleOrder = mockk<(IntArray) -> Unit>(relaxed = true)

        assertNotNull(SettingsRestartPlayback.capture(source)).restore(replacement, true, setShuffleOrder)

        verify(exactly = 1) { replacement.setMediaItems(listOf(items[0], items[2]), 1, C.TIME_UNSET) }
        verify(exactly = 1) { replacement.shuffleModeEnabled = true }
        verify(exactly = 1) { setShuffleOrder(match { it.contentEquals(intArrayOf(1, 0)) }) }
    }

    private fun source(shouldPlay: Boolean, shuffle: Boolean = false): Player {
        val timeline = mockk<Timeline>()
        every { timeline.windowCount } returns items.size
        every { timeline.getWindow(any(), any()) } answers {
            secondArg<Timeline.Window>().apply { mediaItem = items[firstArg<Int>()] }
        }
        if (shuffle) {
            every { timeline.getFirstWindowIndex(true) } returns 2
            every { timeline.getNextWindowIndex(2, Player.REPEAT_MODE_OFF, true) } returns 0
            every { timeline.getNextWindowIndex(0, Player.REPEAT_MODE_OFF, true) } returns 1
        }
        return mockk<Player>().also {
            every { it.currentTimeline } returns timeline
            every { it.currentMediaItemIndex } returns 1
            every { it.contentPosition } returns 43_000L
            every { it.playWhenReady } returns shouldPlay
            every { it.shuffleModeEnabled } returns shuffle
            every { it.playbackState } returns Player.STATE_READY
        }
    }
}
