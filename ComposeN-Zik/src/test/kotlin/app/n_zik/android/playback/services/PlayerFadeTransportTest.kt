package app.n_zik.android.playback.services

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.it.fast4x.rimusic.utils.GlobalVolume
import app.n_zik.android.listentogether.ListenTogetherGuestGuardPlayer
import app.n_zik.android.listentogether.listenTogetherGuestLock
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Checks the phone pause adapter without claiming physical audio fade verification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class PlayerFadeTransportTest {
    private lateinit var upstream: Player
    private lateinit var guard: ListenTogetherGuestGuardPlayer
    private val acceptedIntents = mutableListOf<Boolean>()
    private val guestIntents = mutableListOf<Boolean>()
    private var clock = 1000L

    @Before fun setUp() {
        listenTogetherGuestLock.value = false
        upstream = mockk(relaxed = true)
        every { upstream.isPlaying } returns false
        every { upstream.playWhenReady } returns false
        every { upstream.playbackState } returns Player.STATE_IDLE
        val context: Context = RuntimeEnvironment.getApplication()
        guard = ListenTogetherGuestGuardPlayer(upstream, context, clock = { clock }, toastSink = {})
        guard.onTransportIntent = { acceptedIntents += it }
        guard.onGuestPlayPause = { guestIntents += it }
    }

    @After fun tearDown() {
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
        listenTogetherGuestLock.value = false
    }

    @Test fun `active configured fade delays pause until animation ends`() {
        every { upstream.isPlaying } returns true
        every { upstream.playWhenReady } returns true
        every { upstream.playbackState } returns Player.STATE_READY
        val previouslyPaused = ShadowChoreographer.isPaused()
        val previousFrameDelay = ShadowChoreographer.getFrameDelay()
        val previousGlobalVolume = GlobalVolume
        var currentVolume = 1f
        val volumeWrites = mutableListOf<Float>()
        val volumesAtPause = mutableListOf<Float>()
        every { upstream.volume } answers { currentVolume }
        every { upstream.volume = any() } answers {
            currentVolume = firstArg()
            volumeWrites += currentVolume
        }
        every { upstream.pause() } answers { volumesAtPause += currentVolume }
        // The real helper fades from getGlobalVolume() and restores that global value at end.
        GlobalVolume = 1f
        // Robolectric 4.17 auto-advances vsync by default, even with a PAUSED looper.
        // Pause the frame clock too so idling cannot drain the whole fade in one call.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        try {
            guard.pauseWithFade(1000L)
            verify(exactly = 0) { upstream.pause() }
            assertEquals(emptyList<Boolean>(), acceptedIntents)
            // The first frame establishes ValueAnimator's start time.
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS)
            verify(exactly = 0) { upstream.pause() }
            assertTrue("Active fade must attenuate volume before pausing", currentVolume > 0f && currentVolume < 1f)
            shadowOf(Looper.getMainLooper()).idleFor(499, TimeUnit.MILLISECONDS)
            verify(exactly = 0) { upstream.pause() }
            // The next frame crosses the configured 1000ms animation boundary.
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            verify(exactly = 1) { upstream.pause() }
            assertEquals(listOf(false), acceptedIntents)
            assertTrue("Fade must reach zero before restoring global volume", volumeWrites.any { it == 0f })
            assertEquals(listOf(0f), volumesAtPause)
            assertEquals(1f, volumeWrites.last(), 0f)
            assertEquals(1f, currentVolume, 0f)
        } finally {
            shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
            GlobalVolume = previousGlobalVolume
            ShadowChoreographer.setFrameDelay(previousFrameDelay)
            ShadowChoreographer.setPaused(previouslyPaused)
        }
    }

    @Test fun `active disabled fade pauses immediately`() {
        every { upstream.isPlaying } returns true
        every { upstream.playWhenReady } returns true
        every { upstream.playbackState } returns Player.STATE_READY
        guard.pauseWithFade(0L)
        verify(exactly = 1) { upstream.pause() }
        assertEquals(listOf(false), acceptedIntents)
    }

    @Test fun `buffering player still receives immediate pause with fade configured`() {
        every { upstream.playbackState } returns Player.STATE_BUFFERING
        guard.pauseWithFade(1000L)
        verify(exactly = 1) { upstream.pause() }
        assertEquals(listOf(false), acceptedIntents)
    }

    @Test fun `no audio pause preserves guest intent and blocked helper tail`() {
        listenTogetherGuestLock.value = true
        guard.pauseWithFade(1000L)
        verify(exactly = 1) { upstream.pause() }
        assertEquals(listOf(false), guestIntents)
        assertEquals(listOf(false), acceptedIntents)
        guard.setMediaItem(MediaItem.Builder().setMediaId("blocked").build())
        guard.pauseWithFade(0L)
        verify(exactly = 1) { upstream.pause() }
        assertEquals(listOf(false), guestIntents)
        assertEquals(listOf(false), acceptedIntents)
        clock += ListenTogetherGuestGuardPlayer.TAIL_WINDOW_MS + 1
        guard.pauseWithFade(0L)
        verify(exactly = 2) { upstream.pause() }
        assertEquals(listOf(false, false), guestIntents)
        assertEquals(listOf(false, false), acceptedIntents)
    }
}
