package app.n_zik.android.playback.services

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlaybackLoadControlTest {
    @Test
    fun `back buffer covers rewind increments and is retained across seeks`() {
        val loadControl = createSeekFriendlyLoadControl()
        assertEquals(35_000_000L, loadControl.getBackBufferDurationUs(PlayerId.UNSET))
        assertTrue(loadControl.retainBackBufferFromKeyframe(PlayerId.UNSET))
    }

    @Test
    fun `initial seek and rebuffer playback resume at one second`() {
        val loadControl = createSeekFriendlyLoadControl()
        loadControl.onPrepared(PlayerId.UNSET)
        try {
            for (local in listOf(false, true)) for (rebuffering in listOf(false, true)) {
                assertFalse(loadControl.shouldStartPlayback(parameters(999_999, rebuffering, local = local)))
                assertTrue(loadControl.shouldStartPlayback(parameters(1_000_000, rebuffering, local = local)))
                assertFalse(loadControl.shouldStartPlayback(parameters(1_999_998, rebuffering, speed = 2f, local = local)))
                assertTrue(loadControl.shouldStartPlayback(parameters(2_000_000, rebuffering, speed = 2f, local = local)))
            }
        } finally {
            loadControl.onReleased(PlayerId.UNSET)
        }
    }

    @Test
    fun `forward buffering retains the fifty second window`() {
        val loadControl = createSeekFriendlyLoadControl()
        loadControl.onPrepared(PlayerId.UNSET)
        try {
            for (local in listOf(false, true)) {
                assertTrue(loadControl.shouldContinueLoading(parameters(49_999_999, local = local)))
                assertFalse(loadControl.shouldContinueLoading(parameters(50_000_000, local = local)))
            }
        } finally {
            loadControl.onReleased(PlayerId.UNSET)
        }
    }

    private fun parameters(
        bufferedUs: Long,
        rebuffering: Boolean = false,
        speed: Float = 1f,
        local: Boolean = false,
    ): LoadControl.Parameters {
        val item = MediaItem.fromUri(if (local) "file:///music/test.mp3" else "https://example.invalid/test.mp3")
        val timeline: Timeline = SinglePeriodTimeline(120_000_000L, true, false, false, null, item)
        return LoadControl.Parameters(
            PlayerId.UNSET, timeline, MediaPeriodId(timeline.getUidOfPeriod(0)), 0L, bufferedUs,
            speed, true, rebuffering, C.TIME_UNSET, C.TIME_UNSET,
        )
    }
}
