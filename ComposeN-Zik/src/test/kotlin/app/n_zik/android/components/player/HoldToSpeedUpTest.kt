package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.ui.screens.player.Thumbnail
import app.n_zik.android.utils.holdToSpeedUpKey
import app.n_zik.android.utils.holdToSpeedUpRateKey
import app.n_zik.android.utils.preferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalAnimationApi::class, UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class HoldToSpeedUpTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun testHoldToSpeedUpPreferenceKeys() {
        assertEquals("holdToSpeedUp", holdToSpeedUpKey)
        assertEquals("holdToSpeedUpRate", holdToSpeedUpRateKey)
    }

    @Test
    fun testHoldingArtworkSpeedsUpPlaybackAndRestoresOnRelease() {
        val player = mockk<Player>(relaxed = true)
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        val timeline = mockk<Timeline>(relaxed = true)
        val mediaItem = MediaItem.Builder().setMediaId("test_track_1").build()

        var currentSpeed = 1f
        val playbackParamsSlot = slot<PlaybackParameters>()

        every { binder.player } returns player
        every { player.isPlaying } returns true
        every { player.playbackParameters } answers { PlaybackParameters(currentSpeed, 1f) }
        every { player.playbackParameters = capture(playbackParamsSlot) } answers {
            currentSpeed = playbackParamsSlot.captured.speed
        }
        every { player.currentTimeline } returns timeline
        every { player.currentMediaItem } returns mediaItem
        every { player.mediaItemCount } returns 1
        every { player.playerError } returns null

        compose.setContent {
            CompositionLocalProvider(LocalPlayerServiceBinder provides binder) {
                Thumbnail(
                    thumbnailTapEnabledKey = true,
                    isShowingLyrics = false,
                    onShowLyrics = {},
                    isShowingStatsForNerds = false,
                    onShowStatsForNerds = {},
                    isShowingVisualizer = false,
                    onShowEqualizer = {},
                    onMaximize = {},
                    onDoubleTap = {},
                    showthumbnail = true,
                    modifier = Modifier.size(200.dp).testTag("player.thumbnail")
                )
            }
        }

        // Initially at 1.0x speed
        assertEquals(1f, currentSpeed, 0.01f)

        // Perform press and hold on thumbnail
        compose.onNodeWithTag("player.thumbnail").performTouchInput {
            down(center)
            advanceEventTime(600) // Exceeds standard long press threshold
        }

        // While holding, playback speed should be boosted to 2.0x
        assertEquals(2f, currentSpeed, 0.01f)

        // Release the hold
        compose.onNodeWithTag("player.thumbnail").performTouchInput {
            up()
        }

        // After release, playback speed must restore to 1.0x
        assertEquals(1f, currentSpeed, 0.01f)
    }

    @Test
    fun testHoldingWhilePausedDoesNotBoostSpeed() {
        val player = mockk<Player>(relaxed = true)
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        val timeline = mockk<Timeline>(relaxed = true)
        val mediaItem = MediaItem.Builder().setMediaId("test_track_2").build()

        var currentSpeed = 1f
        val playbackParamsSlot = slot<PlaybackParameters>()

        every { binder.player } returns player
        every { player.isPlaying } returns false // Paused!
        every { player.playbackParameters } answers { PlaybackParameters(currentSpeed, 1f) }
        every { player.playbackParameters = capture(playbackParamsSlot) } answers {
            currentSpeed = playbackParamsSlot.captured.speed
        }
        every { player.currentTimeline } returns timeline
        every { player.currentMediaItem } returns mediaItem
        every { player.mediaItemCount } returns 1
        every { player.playerError } returns null

        compose.setContent {
            CompositionLocalProvider(LocalPlayerServiceBinder provides binder) {
                Thumbnail(
                    thumbnailTapEnabledKey = true,
                    isShowingLyrics = false,
                    onShowLyrics = {},
                    isShowingStatsForNerds = false,
                    onShowStatsForNerds = {},
                    isShowingVisualizer = false,
                    onShowEqualizer = {},
                    onMaximize = {},
                    onDoubleTap = {},
                    showthumbnail = true,
                    modifier = Modifier.size(200.dp).testTag("player.thumbnail.paused")
                )
            }
        }

        // Press and hold while paused
        compose.onNodeWithTag("player.thumbnail.paused").performTouchInput {
            down(center)
            advanceEventTime(600)
            up()
        }

        // Speed should NOT change when player is paused
        assertEquals(1f, currentSpeed, 0.01f)
    }
}
