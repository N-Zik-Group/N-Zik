package app.n_zik.android.playback.services

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.crossfadeDurationKey
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
    fun `real handoff keeps shared session open and incoming pause resume owns broadcasts`() {
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
        assertEquals(0.8f * kotlin.math.sqrt(0.125f), incoming.currentVolume, 0.0001f)
        assertEquals(0.8f * kotlin.math.sqrt(0.875f), outgoing.currentVolume, 0.0001f)
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
        assertTrue("Repeated update work must not extend an eight-second fade by seconds",
            releasedAtMs - startedAtMs in 8000L..8160L)
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
        incoming.listeners.toList().forEach {
            it.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        }
        val pausedVolume = incoming.currentVolume
        scheduler.advanceTimeBy(4000L)
        scheduler.runCurrent()
        assertEquals(pausedVolume, incoming.currentVolume, 0f)
        incoming.readyToPlay = true
        incoming.listeners.toList().forEach {
            it.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
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
        incoming.listeners.toList().forEach {
            it.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        }
        // Both callbacks occur before the next 160ms gain-update tick at 1120ms.
        scheduler.advanceTimeBy(40L)
        scheduler.runCurrent()
        incoming.readyToPlay = true
        incoming.listeners.toList().forEach {
            it.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
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
    fun `primary session event invalidates preloaded old session while primary keeps playing`() {
        val primary = Target("A", 41)
        val preloaded = Target("B", 41)
        prepareHandoff(primary, preloaded)
        primary.sessionId = 42

        primary.events(Player.EVENT_AUDIO_SESSION_ID)

        assertSame(primary.player, service.player)
        assertNull(field("secondaryPlayer").get(service))
        assertFalse(field("isCrossfading").getBoolean(service))
        verify(exactly = 1) { preloaded.player.stop() }
        verify(exactly = 1) { preloaded.player.release() }
        verify(exactly = 0) { primary.player.stop() }
        verify(exactly = 0) { primary.player.pause() }
        verify(exactly = 0) { primary.player.release() }
        assertTrue(primary.readyToPlay)
        assertSessions(open(41), close(41), open(42))
    }

    @Test
    fun `session divergence immediately before handoff refuses overlap without closing primary`() {
        val primary = Target("A", 41)
        val preloaded = Target("B", 41)
        prepareHandoff(primary, preloaded)
        preloaded.sessionId = 42

        invoke("startCrossfade")

        assertSame(primary.player, service.player)
        assertNull(field("secondaryPlayer").get(service))
        assertFalse(field("isCrossfading").getBoolean(service))
        verify(exactly = 1) { preloaded.player.release() }
        verify(exactly = 0) { primary.player.stop() }
        verify(exactly = 0) { primary.player.release() }
        assertSessions(open(41))
    }

    @Test
    fun `active fade divergence releases outgoing before closing its announced session`() {
        val outgoing = Target("A", 41)
        val incoming = Target("B", 41)
        prepareHandoff(outgoing, incoming)
        invoke("startCrossfade")
        scheduler.runCurrent()
        var sessionsAtRelease: List<Pair<String?, Int>>? = null
        every { outgoing.player.release() } answers {
            sessionsAtRelease = sessionHistory()
            Unit
        }
        incoming.sessionId = 42

        incoming.events(Player.EVENT_AUDIO_SESSION_ID)
        scheduler.runCurrent()

        assertEquals(listOf(open(41)), sessionsAtRelease)
        assertSame(incoming.player, service.player)
        assertFalse(field("isCrossfading").getBoolean(service))
        verify(exactly = 1) { outgoing.player.stop() }
        verify(exactly = 1) { outgoing.player.release() }
        verify(exactly = 0) { incoming.player.release() }
        verify { incoming.player.volume = 0.8f }
        assertSessions(open(41), close(41), open(42))
    }

    @Test
    fun `outgoing session event also terminates a divergent active fade`() {
        val outgoing = Target("A", 41)
        val incoming = Target("B", 41)
        prepareHandoff(outgoing, incoming)
        invoke("startCrossfade")
        scheduler.runCurrent()
        outgoing.sessionId = 42

        outgoing.events(Player.EVENT_AUDIO_SESSION_ID)
        scheduler.runCurrent()

        assertSame(incoming.player, service.player)
        assertFalse(field("isCrossfading").getBoolean(service))
        verify(exactly = 1) { outgoing.player.stop() }
        verify(exactly = 1) { outgoing.player.release() }
        verify(exactly = 0) { incoming.player.release() }
        verify { incoming.player.volume = 0.8f }
        assertSessions(open(41))
    }

    @Test
    fun `crossfade player factory configures the adopted primary session before prepare`() {
        field("playbackAudioSessionId").setInt(service, 41)
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
            // Explicit IDs are published synchronously; the diagnostics facade must expose them too.
            assertEquals("Factory must configure the raw player", 41, raw.audioSessionId)
            assertEquals("Diagnostics facade must delegate the session getter", 41, created.audioSessionId)
            val guarded = PlayerServiceModern::class.java.getDeclaredMethod(
                "createGuestGuardPlayer", Player::class.java,
            ).apply { isAccessible = true }.invoke(service, created) as Player
            assertEquals("UI facade must preserve the session through seek coalescing", 41, guarded.audioSessionId)
            assertEquals(Player.STATE_IDLE, created.playbackState)
            // Empty preparation deterministically reaches ENDED without loading media or AudioTrack.
            created.prepare()
            shadowOf(created.applicationLooper).idle()
            assertEquals(Player.STATE_ENDED, created.playbackState)
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

    @Test
    fun `production renderers factory pins automatic renderer messages to the shared sink session`() {
        field("playbackAudioSessionId").setInt(service, 41)
        val factory = requireNotNull(invoke("createRendersFactory"))
        val sink = factory.javaClass.getDeclaredMethod(
            "buildAudioSink", Context::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }.invoke(factory, service, false, false) as AudioSink
        // Capture the actual factory-created wrapper's output without initializing an AudioTrack.
        assertTrue(sink is SharedAudioSessionSink)
        val delegate = mockk<AudioSink>(relaxed = true)
        sink.javaClass.getDeclaredField("delegate").apply { isAccessible = true }.set(sink, delegate)
        sink.setAudioSessionId(C.AUDIO_SESSION_ID_UNSET)
        sink.setAudioSessionId(99)
        verify(exactly = 2) { delegate.setAudioSessionId(41) }
        assertSessions()
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
        val listeners = mutableListOf<Player.Listener>()
        val analyticsListeners = mutableListOf<AnalyticsListener>()
        val player = mockk<ExoPlayer>(relaxed = true) {
            every { audioSessionId } answers { sessionId }
            every { currentMediaItem } returns MediaItem.Builder().setMediaId(id).build()
            every { volume } answers { currentVolume }
            every { volume = any() } answers { currentVolume = firstArg(); Unit }
            every { playbackState } returns Player.STATE_READY
            every { playWhenReady } answers { readyToPlay }
            every { playWhenReady = any() } answers { readyToPlay = firstArg(); Unit }
            every { repeatMode } returns Player.REPEAT_MODE_ALL
            every { playbackParameters } returns PlaybackParameters.DEFAULT
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
