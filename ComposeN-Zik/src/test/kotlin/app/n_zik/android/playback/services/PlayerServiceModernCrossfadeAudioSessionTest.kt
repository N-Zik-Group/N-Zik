package app.n_zik.android.playback.services

import android.app.Application
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.crossfadeDurationKey
import app.it.fast4x.rimusic.utils.crossfadeEnabledKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.appContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises audio-session ownership through the real crossfade swap and captured listeners. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlayerServiceModernCrossfadeAudioSessionTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private lateinit var application: Application
    private lateinit var service: PlayerServiceModern
    private val rawCallbackPlayer = mockk<Player>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        application = ApplicationProvider.getApplicationContext()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns application
        service = spyk(Robolectric.buildService(PlayerServiceModern::class.java).get(), recordPrivateCalls = true)
        (field("coroutineScope").get(service) as CoroutineScope).cancel()
        field("coroutineScope").set(service, scope)
        field("crossfadeClockMs").set(service, { scheduler.currentTime })
        service.preferences.edit().clear().commit()
        field("mediaSession").set(service, mockk<MediaLibrarySession>(relaxed = true))
        // Keep the real handoff and identity publication without the unrelated DB/artwork pipeline.
        every { service.onMediaItemTransition(any(), any()) } answers {
            PlayerServiceModern::class.java.getDeclaredMethod("publishCurrentMediaItem", MediaItem::class.java)
                .apply { isAccessible = true }.invoke(service, firstArg<MediaItem?>())
            Unit
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `real handoff keeps effect session open and incoming pause resume owns broadcasts`() {
        val outgoing = Target("A", 41)
        val incoming = Target("B", 41)
        prepareHandoff(outgoing, incoming)
        val outgoingAnalytics = outgoing.analyticsListeners.single()
        val incomingAnalytics = incoming.analyticsListeners.single()
        val staleOutgoingListener = outgoing.listeners.single()
        assertSessions(open(41))

        invoke("startCrossfade")
        assertSame(incoming.player, service.player)
        assertEquals("B", service.currentMediaItem.value?.mediaId)
        assertTrue(field("isCrossfading").getBoolean(service))
        assertObserved(outgoing, incoming)
        assertEquals(listOf(outgoingAnalytics), outgoing.analyticsListeners)
        assertEquals(listOf(incomingAnalytics), incoming.analyticsListeners)
        verify(exactly = 0) { outgoing.player.removeAnalyticsListener(any()) }
        verify(exactly = 0) { incoming.player.removeAnalyticsListener(any()) }
        assertSessions(open(41))
        verify(exactly = 1) { outgoing.player.removeListener(staleOutgoingListener) }

        // A queued outgoing event must not close the incoming session after the actual swap.
        outgoing.readyToPlay = false
        staleOutgoingListener.onEvents(rawCallbackPlayer, events(Player.EVENT_PLAY_WHEN_READY_CHANGED))
        assertSessions(open(41))
        incoming.readyToPlay = false
        incoming.events(Player.EVENT_PLAY_WHEN_READY_CHANGED)
        assertSessions(open(41), close(41))
        incoming.readyToPlay = true
        incoming.events(Player.EVENT_PLAY_WHEN_READY_CHANGED)
        assertSessions(open(41), close(41), open(41))

        scheduler.advanceTimeBy(3001)
        scheduler.runCurrent()
        verify(exactly = 1) { outgoing.player.stop() }
        verify(exactly = 1) { outgoing.player.release() }
        verify(exactly = 0) { incoming.player.release() }
        assertFalse(field("isCrossfading").getBoolean(service))
        assertObserved(incoming)
        assertTrue(outgoing.analyticsListeners.isEmpty())
        assertEquals(listOf(incomingAnalytics), incoming.analyticsListeners)
        verify(exactly = 1) { outgoing.player.removeAnalyticsListener(outgoingAnalytics) }
        verify(exactly = 0) { incoming.player.removeAnalyticsListener(any()) }
        assertSessions(open(41), close(41), open(41))
        incoming.readyToPlay = false
        incoming.events(Player.EVENT_PLAY_WHEN_READY_CHANGED)
        assertSessions(open(41), close(41), open(41), close(41))
    }

    @Test
    fun `eight second fade includes delayed transition publication and ends at its deadline`() {
        val outgoing = Target("A", 41)
        val incoming = Target("B", 41)
        prepareHandoff(outgoing, incoming)
        service.preferences.edit().putInt(crossfadeDurationKey, 8000).commit()
        var publicationDelayMs = 0L
        field("crossfadeClockMs").set(service, { scheduler.currentTime + publicationDelayMs })
        every { service.onMediaItemTransition(any(), any()) } answers {
            publicationDelayMs += 1000L
            Unit
        }

        invoke("startCrossfade")
        scheduler.runCurrent()
        // Metrolist quadratic ramp: at progress 0.125, fadeIn = 1 - 0.875^2.
        val fadeInAtOneEighth = 1f - 0.875f * 0.875f
        assertEquals(0.8f * fadeInAtOneEighth, incoming.currentVolume, 0.0001f)
        assertEquals(0.8f * (1f - fadeInAtOneEighth), outgoing.currentVolume, 0.0001f)
        scheduler.advanceTimeBy(6999L)
        scheduler.runCurrent()
        assertTrue(field("isCrossfading").getBoolean(service))
        scheduler.advanceTimeBy(1L)
        scheduler.runCurrent()

        assertFalse(field("isCrossfading").getBoolean(service))
        assertEquals(0.8f, incoming.currentVolume, 0f)
        assertEquals(0f, outgoing.currentVolume, 0f)
        verify(exactly = 1) { outgoing.player.release() }
    }

    @Test
    fun `gain update overhead does not accumulate into a longer fade`() {
        val outgoing = Target("A", 41)
        val incoming = Target("B", 41)
        prepareHandoff(outgoing, incoming)
        service.preferences.edit().putInt(crossfadeDurationKey, 8000).commit()
        var overheadMs = 0L
        var releasedAtMs = -1L
        field("crossfadeClockMs").set(service, { scheduler.currentTime + overheadMs })
        for (target in listOf(outgoing, incoming)) {
            every { target.player.volume = any() } answers {
                target.currentVolume = firstArg()
                overheadMs += 20L
                Unit
            }
        }
        every { outgoing.player.release() } answers {
            releasedAtMs = scheduler.currentTime + overheadMs
            Unit
        }

        invoke("startCrossfade")
        val startedAtMs = overheadMs
        scheduler.advanceTimeBy(8001L)
        scheduler.runCurrent()

        assertFalse(field("isCrossfading").getBoolean(service))
        // Completion is measured on the inflated clock the fade itself sees: with 20 fixed
        // steps the deadline can be crossed by at most one 400 ms step plus update overhead.
        assertTrue("Repeated update work must not extend an eight-second fade by seconds",
            releasedAtMs - startedAtMs in 8000L..8600L)
        assertEquals(0.8f, incoming.currentVolume, 0f)
        assertEquals(0f, outgoing.currentVolume, 0f)
    }

    @Test
    fun `pause freezes gains and is excluded from the eight second fade`() {
        val outgoing = Target("A", 41)
        val incoming = Target("B", 41)
        prepareHandoff(outgoing, incoming)
        service.preferences.edit().putInt(crossfadeDurationKey, 8000).commit()
        invoke("startCrossfade")
        scheduler.advanceTimeBy(1000L)
        scheduler.runCurrent()
        incoming.readyToPlay = false
        // The service's own listener needs the encrypted prefs the test sandbox lacks;
        // the fade clock only needs the handoff's sync listener.
        incoming.listeners.toList().filter { it !== service }.forEach {
            it.onIsPlayingChanged(false)
        }
        val pausedVolume = incoming.currentVolume
        scheduler.advanceTimeBy(4000L)
        scheduler.runCurrent()
        assertEquals(pausedVolume, incoming.currentVolume, 0f)
        incoming.readyToPlay = true
        incoming.listeners.toList().filter { it !== service }.forEach {
            it.onIsPlayingChanged(true)
        }
        scheduler.advanceTimeBy(6999L)
        scheduler.runCurrent()
        assertTrue(field("isCrossfading").getBoolean(service))
        scheduler.advanceTimeBy(101L)
        scheduler.runCurrent()

        assertFalse(field("isCrossfading").getBoolean(service))
        assertEquals(0.8f, incoming.currentVolume, 0f)
        verify(exactly = 1) { outgoing.player.release() }
    }

    @Test
    fun `pause between service gain updates moves completion by precisely its duration`() {
        val outgoing = Target("A", 41)
        val incoming = Target("B", 41)
        prepareHandoff(outgoing, incoming)
        service.preferences.edit().putInt(crossfadeDurationKey, 8000).commit()
        invoke("startCrossfade")
        scheduler.advanceTimeBy(1000L)
        scheduler.runCurrent()
        incoming.readyToPlay = false
        incoming.listeners.toList().filter { it !== service }.forEach {
            it.onIsPlayingChanged(false)
        }
        // Both callbacks occur before the next 400ms gain-update tick at 1200ms.
        scheduler.advanceTimeBy(40L)
        scheduler.runCurrent()
        incoming.readyToPlay = true
        incoming.listeners.toList().filter { it !== service }.forEach {
            it.onIsPlayingChanged(true)
        }
        scheduler.advanceTimeBy(6999L)
        scheduler.runCurrent()
        assertTrue(field("isCrossfading").getBoolean(service))
        verify(exactly = 0) { outgoing.player.release() }
        scheduler.advanceTimeBy(1L)
        scheduler.runCurrent()

        assertFalse(field("isCrossfading").getBoolean(service))
        assertEquals(0.8f, incoming.currentVolume, 0f)
        assertEquals(0f, outgoing.currentVolume, 0f)
        verify(exactly = 1) { outgoing.player.release() }
    }

    @Test
    fun `zero duration completes and cleans up on the first update`() {
        assertImmediateFadeCompletes(durationMs = 0)
    }

    @Test
    fun `negative duration completes and cleans up on the first update`() {
        assertImmediateFadeCompletes(durationMs = -1000)
    }

    @Test
    fun `publication longer than the duration completes without another delay`() {
        assertImmediateFadeCompletes(durationMs = 8000, publicationDelayMs = 9000L)
    }

    private fun assertImmediateFadeCompletes(durationMs: Int, publicationDelayMs: Long = 0L) {
        val outgoing = Target("A", 41)
        val incoming = Target("B", 41)
        prepareHandoff(outgoing, incoming)
        service.preferences.edit().putInt(crossfadeDurationKey, durationMs).commit()
        var publicationElapsedMs = 0L
        field("crossfadeClockMs").set(service, { scheduler.currentTime + publicationElapsedMs })
        every { service.onMediaItemTransition(any(), any()) } answers {
            publicationElapsedMs += publicationDelayMs
            Unit
        }
        invoke("startCrossfade")
        val syncListener = incoming.listeners.first()
        scheduler.runCurrent()

        assertEquals(0L, scheduler.currentTime)
        assertFalse(field("isCrossfading").getBoolean(service))
        assertNull(field("activeCrossfade").get(service))
        assertNull(field("crossfadeJob").get(service))
        assertNull(field("fadingPlayer").get(service))
        assertEquals(0.8f, incoming.currentVolume, 0f)
        assertEquals(0f, outgoing.currentVolume, 0f)
        assertTrue(outgoing.listeners.isEmpty())
        assertFalse(incoming.listeners.contains(syncListener))
        assertObserved(incoming)
        verify(exactly = 1) { outgoing.player.release() }
        verify(exactly = 0) { incoming.player.release() }
    }

    @Test
    fun `cancelling actual handoff removes outgoing diagnostics and preserves incoming observation`() {
        val outgoing = Target("A", 41)
        val incoming = Target("B", 41)
        prepareHandoff(outgoing, incoming)
        val outgoingAnalytics = outgoing.analyticsListeners.single()
        val incomingAnalytics = incoming.analyticsListeners.single()
        invoke("startCrossfade")
        scheduler.runCurrent()
        assertObserved(outgoing, incoming)

        invoke("cancelCrossfadeAndReset")
        scheduler.runCurrent()

        assertSame(incoming.player, service.player)
        assertFalse(field("isCrossfading").getBoolean(service))
        assertObserved(incoming)
        assertTrue(outgoing.analyticsListeners.isEmpty())
        assertEquals(listOf(incomingAnalytics), incoming.analyticsListeners)
        verify(exactly = 1) { outgoing.player.removeAnalyticsListener(outgoingAnalytics) }
        verify(exactly = 1) { outgoing.player.release() }
        verify(exactly = 0) { incoming.player.removeAnalyticsListener(any()) }
        verify(exactly = 0) { incoming.player.release() }
    }

    @Test
    fun `rescheduling replaces preload and removes only its diagnostics`() {
        val primary = Target("A", 41)
        val preloaded = Target("B", 41)
        prepareHandoff(primary, preloaded)
        val preloadAnalytics = preloaded.analyticsListeners.single()
        val primaryAnalytics = primary.analyticsListeners.single()

        // Scheduling always disposes the previous preload, even when crossfade is disabled.
        invoke("scheduleCrossfade")

        assertNull(field("secondaryPlayer").get(service))
        assertSame(primary.player, service.player)
        assertObserved(primary)
        assertTrue(preloaded.analyticsListeners.isEmpty())
        assertEquals(listOf(primaryAnalytics), primary.analyticsListeners)
        verify(exactly = 1) { preloaded.player.removeAnalyticsListener(preloadAnalytics) }
        verify(exactly = 1) { preloaded.player.release() }
        verify(exactly = 0) { primary.player.removeAnalyticsListener(any()) }
        verify(exactly = 0) { primary.player.release() }
    }

    @Test
    fun `per player sessions swap without the shared session gate`() {
        val primary = Target("A", 41)
        val preloaded = Target("B", 42)
        prepareHandoff(primary, preloaded)

        invoke("startCrossfade")

        // Per-player sessions are the Metrolist model: distinct session ids no longer block
        // the swap, and the effect session announcement moves close-old/open-new.
        assertSame(preloaded.player, service.player)
        assertTrue(field("isCrossfading").getBoolean(service))
        verify(exactly = 0) { primary.player.stop() }
        verify(exactly = 0) { primary.player.release() }
        assertSessions(open(41), close(41), open(42))
    }

    @Test
    fun `cleanup recovers failed session swaps before stopping or releasing outgoing`() {
        val outgoing = Target("A", 41)
        val incoming = Target("B", 42)
        prepareHandoff(outgoing, incoming)
        val session = field("mediaSession").get(service) as MediaLibrarySession
        var sessionPlayer: Player = SessionArtworkPlayer(outgoing.player)
        var attempts = 0
        every { session.player } answers { sessionPlayer }
        every { session.player = any() } answers {
            attempts++
            if (attempts <= 2) throw IllegalStateException("Session swap rejected")
            sessionPlayer = firstArg()
            Unit
        }
        every { outgoing.player.stop() } answers {
            assertSame(incoming.player, underlyingPlayer(session.player))
            Unit
        }
        every { outgoing.player.release() } answers {
            assertSame(incoming.player, underlyingPlayer(session.player))
            Unit
        }

        invoke("startCrossfade")
        scheduler.runCurrent()
        assertEquals(2, attempts)
        assertSame(outgoing.player, underlyingPlayer(session.player))
        verify(exactly = 0) { outgoing.player.stop() }
        verify(exactly = 0) { outgoing.player.release() }

        invoke("cleanupCrossfade")
        scheduler.runCurrent()

        assertEquals(3, attempts)
        assertSame(incoming.player, underlyingPlayer(session.player))
        verify(exactly = 1) { outgoing.player.stop() }
        verify(exactly = 1) { outgoing.player.release() }
        verify(exactly = 0) { incoming.player.release() }
    }

    @Test
    fun `failed cleanup recovers through the production scheduled retry without another fade`() {
        val outgoing = Target("A", 41)
        val incoming = Target("B", 42)
        prepareHandoff(outgoing, incoming)
        val session = field("mediaSession").get(service) as MediaLibrarySession
        var sessionPlayer: Player = SessionArtworkPlayer(outgoing.player)
        var rejectSwap = true
        var attempts = 0
        every { session.player } answers { sessionPlayer }
        every { session.player = any() } answers {
            attempts++
            if (rejectSwap) throw IllegalStateException("Session swap rejected")
            sessionPlayer = firstArg()
            Unit
        }

        invoke("startCrossfade")
        scheduler.runCurrent()
        invoke("cleanupCrossfade")
        scheduler.runCurrent()

        assertEquals(3, attempts)
        assertSame(outgoing.player, underlyingPlayer(session.player))
        verify(exactly = 0) { outgoing.player.stop() }
        verify(exactly = 0) { outgoing.player.release() }

        every { outgoing.player.stop() } answers {
            assertSame(incoming.player, underlyingPlayer(session.player))
            Unit
        }
        every { outgoing.player.release() } answers {
            assertSame(incoming.player, underlyingPlayer(session.player))
            Unit
        }
        rejectSwap = false
        scheduler.advanceTimeBy(249L)
        scheduler.runCurrent()
        assertEquals(3, attempts)
        scheduler.advanceTimeBy(1L)
        scheduler.runCurrent()

        assertEquals(4, attempts)
        assertNull(field("pendingSessionPlayerRelease").get(service))
        assertNull(field("sessionRebindRecoveryJob").get(service))

        assertSame(incoming.player, underlyingPlayer(session.player))
        verify(exactly = 1) { outgoing.player.stop() }
        verify(exactly = 1) { outgoing.player.release() }
        verify(exactly = 0) { incoming.player.release() }
    }

    @Test
    fun `recovery attempts are bounded while a permanently rejected session target stays alive`() {
        val outgoing = Target("A", 41)
        val incoming = Target("B", 42)
        prepareHandoff(outgoing, incoming)
        val session = field("mediaSession").get(service) as MediaLibrarySession
        every { session.player = any() } throws IllegalStateException("Session unavailable")
        invoke("startCrossfade")
        scheduler.advanceTimeBy(20_000L)
        scheduler.runCurrent()
        verify(exactly = 8) { session.player = any() } // Two initial, one cleanup, five delayed.
        verify(exactly = 0) { outgoing.player.stop() }
        verify(exactly = 0) { outgoing.player.release() }
        assertSame(outgoing.player, field("pendingSessionPlayerRelease").get(service))
        assertNull(field("sessionRebindRecoveryJob").get(service))
    }

    @Test
    fun `recovery binds the live primary rather than a target superseded during backoff`() {
        val outgoing = Target("A", 41)
        val incoming = Target("B", 42)
        val replacement = Target("C", 43)
        prepareHandoff(outgoing, incoming)
        val session = field("mediaSession").get(service) as MediaLibrarySession
        var attempts = 0
        var sessionPlayer: Player = SessionArtworkPlayer(outgoing.player)
        every { session.player } answers { sessionPlayer }
        every { session.player = any() } answers {
            if (++attempts <= 3) throw IllegalStateException("Temporary failure")
            sessionPlayer = firstArg()
            Unit
        }
        invoke("startCrossfade")
        invoke("cancelCrossfadeAndReset")
        scheduler.runCurrent()
        service.player = replacement.player
        scheduler.advanceTimeBy(250L)
        scheduler.runCurrent()
        assertSame(replacement.player, underlyingPlayer(session.player))
        verify(exactly = 1) { outgoing.player.release() }
        verify(exactly = 0) { incoming.player.release() }
        verify(exactly = 0) { replacement.player.release() }
    }

    @Test
    fun `destruction detaches session cancels recovery and releases both raw players exactly once`() {
        val outgoing = Target("A", 41)
        val incoming = Target("B", 42)
        prepareHandoff(outgoing, incoming)
        val session = field("mediaSession").get(service) as MediaLibrarySession
        every { session.player = any() } throws IllegalStateException("Session unavailable")
        var detached = false
        every { session.release() } answers { detached = true; Unit }
        listOf(outgoing, incoming).forEach { target ->
            every { target.player.stop() } answers { assertTrue(detached); Unit }
            every { target.player.release() } answers { assertTrue(detached); Unit }
        }
        invoke("startCrossfade")
        scheduler.advanceTimeBy(3000L)
        scheduler.runCurrent()
        val recovery = field("sessionRebindRecoveryJob").get(service) as Job
        assertTrue(recovery.isActive)
        assertSame(outgoing.player, field("pendingSessionPlayerRelease").get(service))

        service.onDestroy()
        scheduler.advanceTimeBy(20_000L)
        scheduler.runCurrent()
        service.onDestroy() // Repeated lifecycle delivery must not release a raw player twice.

        assertTrue(recovery.isCancelled)
        assertNull(field("sessionRebindRecoveryJob").get(service))
        assertNull(field("pendingSessionPlayerRelease").get(service))
        assertNull(field("crossfadeJob").get(service))
        verify(exactly = 3) { session.player = any() }
        verify(exactly = 1) { session.release() }
        verify(exactly = 1) { outgoing.player.release() }
        verify(exactly = 1) { incoming.player.release() }
    }

    @Test
    fun `terminal incoming end disposes fade without restarting playback`() = assertTerminalCleanup(Player.STATE_ENDED)

    @Test
    fun `explicit incoming stop disposes fade without restarting playback`() = assertTerminalCleanup(Player.STATE_IDLE)

    @Test
    fun `incoming error disposes fade without restarting playback`() = assertTerminalCleanup(Player.STATE_READY, error = true)

    private fun assertTerminalCleanup(state: Int, error: Boolean = false) {
        val outgoing = Target("A", 41)
        val incoming = Target("B", 42)
        prepareHandoff(outgoing, incoming)
        service.preferences.edit().putBoolean(crossfadeEnabledKey, true).commit()
        every { incoming.player.duration } returns 180_000L
        every { incoming.player.currentPosition } returns 1_000L
        every { incoming.player.hasNextMediaItem() } returns true
        every { incoming.player.nextMediaItemIndex } returns 1
        every { incoming.player.getMediaItemAt(1) } returns MediaItem.Builder().setMediaId("C").build()
        invoke("startCrossfade")
        scheduler.advanceTimeBy(600L)
        scheduler.runCurrent()
        assertTrue(field("isCrossfading").getBoolean(service))
        incoming.playing = false
        incoming.state = state
        if (error) every { incoming.player.playerError } returns mockk(relaxed = true)
        incoming.listeners.toList().filter { it !== service }.forEach { it.onIsPlayingChanged(false) }
        scheduler.advanceTimeBy(150L)
        scheduler.runCurrent()
        assertFalse(field("isCrossfading").getBoolean(service))
        assertNull(field("activeCrossfade").get(service))
        assertNull("Terminal cleanup must not arm another fade", field("crossfadeTriggerJob").get(service))
        assertEquals(0.8f, incoming.currentVolume, 0f)
        verify(exactly = 1) { outgoing.player.release() }
        verify(exactly = 0) { incoming.player.release() }
        verify(exactly = 0) { incoming.player.play() }
        verify(exactly = 0) { outgoing.player.play() }
    }

    @Test
    fun `two times speed halves the fade deadline and keeps quadratic gains at the wall clock midpoint`() {
        val outgoing = Target("A", 41)
        val incoming = Target("B", 42)
        outgoing.speed = PlaybackParameters(2f)
        prepareHandoff(outgoing, incoming)
        service.preferences.edit().putInt(crossfadeDurationKey, 8000).commit()
        invoke("startCrossfade")
        scheduler.advanceTimeBy(2000L)
        scheduler.runCurrent()
        assertEquals(0.6f, incoming.currentVolume, 0.0001f)
        assertEquals(0.2f, outgoing.currentVolume, 0.0001f)
        scheduler.advanceTimeBy(1999L)
        scheduler.runCurrent()
        assertTrue(field("isCrossfading").getBoolean(service))
        verify(exactly = 0) { outgoing.player.release() }
        scheduler.advanceTimeBy(1L)
        scheduler.runCurrent()
        assertFalse(field("isCrossfading").getBoolean(service))
        assertEquals(0.8f, incoming.currentVolume, 0f)
        verify(exactly = 1) { outgoing.player.release() }
    }

    @Test
    fun `real preload and handoff preserve the deterministic shuffle permutation for subsequent skips`() {
        val outgoing = ExoPlayer.Builder(application).build()
        val incoming = spyk(ExoPlayer.Builder(application).build())
        // Real queue and shuffle traversal without opening a media source or audio device.
        every { incoming.prepare() } answers { Unit }
        every { incoming.setMediaItems(any<List<MediaItem>>(), any<Int>(), any<Long>()) } answers {
            callOriginal()
            // A known different order makes removing the production copy fail deterministically.
            incoming.shuffleOrder = DefaultShuffleOrder(intArrayOf(0, 1, 2, 3, 4), 9L)
            Unit
        }
        every { service["createCrossfadeExoPlayer"]() } returns incoming
        val items = (0..4).map { MediaItem.Builder().setMediaId("track-$it").setUri("file:///track-$it.mp3").build() }
        try {
            outgoing.setMediaItems(items, 0, 0L)
            outgoing.shuffleModeEnabled = true
            outgoing.shuffleOrder = DefaultShuffleOrder(intArrayOf(0, 3, 1, 4, 2), 7L)
            outgoing.repeatMode = Player.REPEAT_MODE_OFF
            service.player = outgoing
            PlayerServiceModern::class.java.getDeclaredMethod("preloadCrossfade", Long::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(service, 0L)
            assertEquals("track-3", incoming.currentMediaItem?.mediaId)
            invoke("startCrossfade")
            assertSame(incoming, service.player)
            // Queue traversal is real; unrelated Discord callbacks require full service startup.
            incoming.removeListener(service)
            val subsequent = mutableListOf<String>()
            while (incoming.hasNextMediaItem()) {
                incoming.seekToNextMediaItem()
                subsequent += incoming.currentMediaItem?.mediaId.orEmpty()
            }
            assertEquals(listOf("track-1", "track-4", "track-2"), subsequent)
        } finally {
            invoke("cancelCrossfadeAndReset")
            incoming.release()
        }
    }

    @Test
    fun `buffering incoming track freezes the fade clock`() {
        val outgoing = Target("A", 41)
        val incoming = Target("B", 41)
        prepareHandoff(outgoing, incoming)
        service.preferences.edit().putInt(crossfadeDurationKey, 8000).commit()
        invoke("startCrossfade")
        scheduler.advanceTimeBy(1000L)
        scheduler.runCurrent()
        val volumeBeforeBuffering = incoming.currentVolume
        assertTrue(volumeBeforeBuffering > 0f)
        // Incoming buffers: playWhenReady stays true but isPlaying is false, so the fade
        // must hold instead of burning its time (Metrolist gates the fade on isPlaying).
        incoming.playing = false
        incoming.listeners.toList().filter { it !== service }.forEach { it.onIsPlayingChanged(false) }
        scheduler.advanceTimeBy(4000L)
        scheduler.runCurrent()
        assertEquals(volumeBeforeBuffering, incoming.currentVolume, 0f)
        incoming.playing = true
        incoming.listeners.toList().filter { it !== service }.forEach { it.onIsPlayingChanged(true) }
        scheduler.advanceTimeBy(9000L)
        scheduler.runCurrent()

        assertFalse(field("isCrossfading").getBoolean(service))
        assertEquals(0.8f, incoming.currentVolume, 0f)
        verify(exactly = 1) { outgoing.player.release() }
    }

    @Test
    fun `crossfade player factory keeps the ExoPlayer allocated session and wires diagnostics and facade`() {
        every { service["createMediaSourceFactory"]() } returns mockk<DefaultMediaSourceFactory>(relaxed = true)
        val created = invoke("createCrossfadeExoPlayer") as ExoPlayer
        val diagnostics = field("crossfadeAudioDiagnostics").get(service) as Map<*, *>
        val records = mutableListOf<String>()
        val tree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (tag == "CrossfadeAudio") records += message
            }
        }
        Timber.plant(tree)
        try {
            assertTrue(diagnostics.containsKey(created))
            val raw = created.javaClass.getDeclaredField("delegate").apply {
                isAccessible = true
            }.get(created) as ExoPlayer
            // No shared service session: the factory no longer pins an explicit session ID.
            assertEquals(C.AUDIO_SESSION_ID_UNSET, raw.audioSessionId)
            assertEquals(Player.STATE_IDLE, created.playbackState)
            // Empty preparation deterministically reaches ENDED without loading media or AudioTrack.
            created.prepare()
            // Media3 allocates the session on playbackLooper and posts it to applicationLooper.
            // Empty-queue ENDED is synchronous, so draining only the main looper races that work.
            val playbackReady = CountDownLatch(1)
            Handler(raw.playbackLooper).post { playbackReady.countDown() }
            assertTrue("Playback initialization must reach the barrier", playbackReady.await(5, TimeUnit.SECONDS))
            shadowOf(created.applicationLooper).idle()
            assertEquals(Player.STATE_ENDED, created.playbackState)
            // The session ExoPlayer allocated for this player alone must surface through
            // both the diagnostics facade and the guarded UI facade.
            val rawSession = raw.audioSessionId
            assertTrue("Prepared factory player must keep its own allocated session", rawSession > 0)
            assertEquals("Diagnostics facade must delegate the session getter", rawSession, created.audioSessionId)
            val guarded = PlayerServiceModern::class.java.getDeclaredMethod(
                "createGuestGuardPlayer", Player::class.java,
            ).apply { isAccessible = true }.invoke(service, created) as Player
            assertEquals("UI facade must preserve the session through seek coalescing", rawSession, guarded.audioSessionId)
            val playbackRecords = records.filter { it.startsWith("event=PLAYBACK_STATE ") }
            assertTrue("The real factory player must deliver analytics through the attached listener", playbackRecords.any {
                it.contains("player=${System.identityHashCode(created)} ") &&
                    it.contains("reportedState=${Player.STATE_ENDED}")
            })
            assertTrue("Diagnostic records must stay bounded", records.all { it.length <= 2048 })
            assertSessions()
        } finally {
            (field("currentSeekCoalescer").get(service) as? SeekCoalescingPlayer)?.detach()
            field("secondaryPlayer").set(service, created)
            try {
                invoke("cancelCrossfadeAndReset")
                assertFalse(diagnostics.containsKey(created))
            } finally {
                Timber.uproot(tree)
            }
        }
    }

    /** Follow the actual session facades instead of treating a setter call as a completed bind. */
    private fun underlyingPlayer(player: Player): Player = when (player) {
        is ForwardingPlayer -> underlyingPlayer(player.wrappedPlayer)
        is SeekCoalescingPlayer -> underlyingPlayer(
            SeekCoalescingPlayer::class.java.getDeclaredField("upstream")
                .apply { isAccessible = true }.get(player) as Player
        )
        else -> player
    }

    private fun prepareHandoff(outgoing: Target, incoming: Target) {
        // Mock players enter through the same registration hook as factory-created players.
        invokeTarget("attachCrossfadeAudioDiagnostics", outgoing.player)
        invokeTarget("attachCrossfadeAudioDiagnostics", incoming.player)
        service.player = outgoing.player
        invokeTarget("bindAudioEffectPlayer", outgoing.player)
        field("secondaryPlayer").set(service, incoming.player)
    }

    private inner class Target(val id: String, var sessionId: Int) {
        var currentVolume = 0.8f
        var readyToPlay = true
        // Audio flow independent of playWhenReady: a buffering track is ready but not playing.
        var playing = true
        var state = Player.STATE_READY
        var speed = PlaybackParameters.DEFAULT
        val listeners = mutableListOf<Player.Listener>()
        val analyticsListeners = mutableListOf<AnalyticsListener>()
        val player = mockk<ExoPlayer>(relaxed = true) {
            every { audioSessionId } answers { sessionId }
            every { currentMediaItem } returns MediaItem.Builder().setMediaId(id).build()
            every { volume } answers { currentVolume }
            every { volume = any() } answers { currentVolume = firstArg(); Unit }
            every { playbackState } answers { state }
            every { playerError } returns null
            every { playWhenReady } answers { readyToPlay }
            every { isPlaying } answers { playing && readyToPlay }
            every { playWhenReady = any() } answers { readyToPlay = firstArg(); Unit }
            every { repeatMode } returns Player.REPEAT_MODE_ALL
            every { playbackParameters } answers { speed }
            every { playbackParameters = any() } answers { speed = firstArg(); Unit }
            every { addAnalyticsListener(any()) } answers { analyticsListeners += firstArg<AnalyticsListener>(); Unit }
            every { removeAnalyticsListener(any()) } answers { analyticsListeners.remove(firstArg<AnalyticsListener>()); Unit }
            every { addListener(any()) } answers { listeners += firstArg<Player.Listener>(); Unit }
            every { removeListener(any()) } answers { listeners.remove(firstArg<Player.Listener>()); Unit }
        }

        fun events(event: Int) {
            listeners.toList().forEach { it.onEvents(rawCallbackPlayer, this@PlayerServiceModernCrossfadeAudioSessionTest.events(event)) }
        }
    }

    private fun assertObserved(vararg targets: Target) {
        val diagnostics = field("crossfadeAudioDiagnostics").get(service) as Map<*, *>
        assertEquals(targets.size, diagnostics.size)
        targets.forEach { target ->
            assertTrue("${target.id} must remain registered", diagnostics.containsKey(target.player))
            assertSame(diagnostics[target.player], target.analyticsListeners.single())
        }
    }

    private fun events(event: Int) = Player.Events(FlagSet.Builder().add(event).build())

    private fun broadcasts(): List<Intent> = shadowOf(application).broadcastIntents.filter {
        it.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION ||
            it.action == AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
    }

    private fun sessionHistory() = broadcasts().map {
        it.action to it.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
    }

    private fun assertSessions(vararg expected: Pair<String, Int>) = assertEquals(expected.toList(), sessionHistory())

    private fun open(id: Int) = AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION to id

    private fun close(id: Int) = AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION to id

    private fun field(name: String) = PlayerServiceModern::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun invoke(name: String) = PlayerServiceModern::class.java.getDeclaredMethod(name).apply {
        isAccessible = true
    }.invoke(service)

    private fun invokeTarget(name: String, target: ExoPlayer) =
        PlayerServiceModern::class.java.getDeclaredMethod(name, ExoPlayer::class.java).apply {
            isAccessible = true
        }.invoke(service, target)
}
