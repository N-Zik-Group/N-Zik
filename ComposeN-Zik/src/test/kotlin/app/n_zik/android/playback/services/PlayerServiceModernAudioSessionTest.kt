package app.n_zik.android.playback.services

import android.app.Application
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.os.Parcel
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.PresetsReverb
import app.it.fast4x.rimusic.utils.audioReverbPresetKey
import app.it.fast4x.rimusic.utils.bassboostEnabledKey
import app.it.fast4x.rimusic.utils.bassboostLevelKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.volumeNormalizationKey
import app.n_zik.android.appContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
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
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAudioEffect

/** Exercises service listener binding, internal effects and external equalizer broadcasts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [RetryableAudioEffectShadow::class])
class PlayerServiceModernAudioSessionTest {
    private lateinit var application: Application
    private lateinit var service: PlayerServiceModern
    private val rawCallbackPlayer = mockk<Player>(relaxed = true)

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns application
        service = Robolectric.buildService(PlayerServiceModern::class.java).get()
        // Exercise the session integration without starting network, audio or production jobs.
        (field("coroutineScope").get(service) as CoroutineScope).cancel()
        service.preferences.edit().clear().commit()
        RetryableAudioEffectShadow.failNextBassBoostSetup = false
        every { rawCallbackPlayer.playbackState } returns Player.STATE_IDLE
        every { rawCallbackPlayer.playWhenReady } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `bind immediately opens the already playing target and session-only callback migrates it`() {
        val target = Target(sessionId = 41)
        bind(target)
        assertSessions(open(41))

        target.sessionId = 42
        target.events(Player.EVENT_AUDIO_SESSION_ID)
        assertSessions(open(41), close(41), open(42))
        val opened = broadcasts().last()
        assertEquals(service.packageName, opened.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME))
        assertEquals(AudioEffect.CONTENT_TYPE_MUSIC,
            opened.getIntExtra(AudioEffect.EXTRA_CONTENT_TYPE, -1))
    }

    @Test
    fun `state and playWhenReady callbacks use bound target despite a different raw callback player`() {
        val target = Target(sessionId = 41, state = Player.STATE_IDLE, readyToPlay = false)
        bind(target)
        assertSessions()

        target.state = Player.STATE_BUFFERING
        target.readyToPlay = true
        target.events(Player.EVENT_PLAYBACK_STATE_CHANGED)
        assertSessions(open(41))
        target.state = Player.STATE_READY
        target.events(Player.EVENT_PLAYBACK_STATE_CHANGED)
        target.events(Player.EVENT_AUDIO_SESSION_ID)
        assertSessions(open(41))

        target.readyToPlay = false
        target.events(Player.EVENT_PLAY_WHEN_READY_CHANGED)
        target.events(Player.EVENT_PLAY_WHEN_READY_CHANGED)
        assertSessions(open(41), close(41))
    }

    @Test
    fun `old player callbacks cannot close active session after ownership changes`() {
        val outgoing = Target(sessionId = 41)
        bind(outgoing)
        val staleListener = outgoing.listeners.single()
        val incoming = Target(sessionId = 41)
        service.player = incoming.player
        // A callback already queued before the handoff must also be ignored before rebind.
        outgoing.readyToPlay = false
        staleListener.onEvents(rawCallbackPlayer, events(Player.EVENT_PLAY_WHEN_READY_CHANGED))
        assertSessions(open(41))

        invokeTarget("bindAudioEffectPlayer", incoming.player)
        staleListener.onEvents(rawCallbackPlayer, events(Player.EVENT_AUDIO_SESSION_ID))
        assertSessions(open(41))
        verify(exactly = 1) { outgoing.player.removeListener(staleListener) }
        incoming.readyToPlay = false
        incoming.events(Player.EVENT_PLAY_WHEN_READY_CHANGED)
        assertSessions(open(41), close(41))
    }

    @Test
    fun `rebinding the same active player does not repeat open`() {
        val target = Target(sessionId = 41)
        bind(target)
        invokeTarget("bindAudioEffectPlayer", target.player)
        assertSessions(open(41))
        assertTrue(target.listeners.isNotEmpty())
    }

    @Test
    fun `unbound secondary preload cannot publish an equalizer session`() {
        val primary = Target(sessionId = 41)
        bind(primary)
        val preload = Target(sessionId = 42)
        invokeTarget("configureAudioSession", preload.player)
        assertTrue(preload.listeners.isEmpty())
        assertSessions(open(41))
    }

    @Test
    fun `close sends remembered open ID even if the current player ID changed without callback`() {
        val target = Target(sessionId = 41)
        bind(target)
        target.sessionId = 99
        invoke("closeAudioEffectSession")
        invoke("closeAudioEffectSession")
        assertSessions(open(41), close(41))
        assertEquals(service.packageName,
            broadcasts().last().getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME))
    }

    @Test
    fun `service destruction closes the announced session and detaches its listener`() {
        enableInternalEffects()
        val target = Target(sessionId = 41)
        bind(target)
        val listener = target.listeners.single()
        assertEffectsOnSession(internalEffects(), 41)
        val normalizationJob = Job()
        field("volumeNormalizationJob").set(service, normalizationJob)
        assertSessions(open(41))

        service.onDestroy()

        assertSessions(open(41), close(41))
        assertTrue(normalizationJob.isCancelled)
        assertTrue(ShadowAudioEffect.getAudioEffects().isEmpty())
        assertNull(field("bassBoost").get(service))
        assertNull(field("reverbPreset").get(service))
        assertNull(service.loudnessEnhancer)
        verify(exactly = 1) { target.player.removeListener(listener) }
        // A queued renderer callback cannot reopen the service's session after shutdown.
        listener.onEvents(rawCallbackPlayer, events(Player.EVENT_AUDIO_SESSION_ID))
        assertSessions(open(41), close(41))
    }
    @Test
    fun `unset session is never broadcast and becomes valid on its session callback`() {
        val target = Target(sessionId = C.AUDIO_SESSION_ID_UNSET)
        bind(target)
        assertSessions()
        target.sessionId = 41
        target.events(Player.EVENT_AUDIO_SESSION_ID)
        assertSessions(open(41))
        target.sessionId = C.AUDIO_SESSION_ID_UNSET
        target.events(Player.EVENT_AUDIO_SESSION_ID)
        assertSessions(open(41), close(41))
    }

    @Test
    fun `primary and preload are configured with the shared service session`() {
        field("playbackAudioSessionId").setInt(service, 77)
        val primary = Target(sessionId = C.AUDIO_SESSION_ID_UNSET)
        val preload = Target(sessionId = C.AUDIO_SESSION_ID_UNSET)
        invokeTarget("configureAudioSession", primary.player)
        invokeTarget("configureAudioSession", preload.player)
        verify(exactly = 1) { primary.player.setAudioSessionId(77) }
        verify(exactly = 1) { preload.player.setAudioSessionId(77) }
        verify(exactly = 0) { primary.player.prepare() }
        verify(exactly = 0) { preload.player.prepare() }
        assertSessions()
    }

    @Test
    fun `allocation failure fallback adopts actual primary session for the preload`() {
        field("playbackAudioSessionId").setInt(service, C.AUDIO_SESSION_ID_UNSET)
        val primary = Target(sessionId = 41)
        bind(primary)
        val preload = Target(sessionId = C.AUDIO_SESSION_ID_UNSET)
        invokeTarget("configureAudioSession", preload.player)
        assertEquals(41, field("playbackAudioSessionId").getInt(service))
        verify(exactly = 1) { preload.player.setAudioSessionId(41) }
        assertSessions(open(41))
    }

    @Test
    fun `enabled effects release old instances and bind replacements to changed session`() {
        enableInternalEffects()
        val target = Target(sessionId = 41)
        bind(target)
        val original = internalEffects()
        assertEffectsOnSession(original, 41)

        target.sessionId = 42
        target.events(Player.EVENT_AUDIO_SESSION_ID)

        val replacement = internalEffects()
        assertEffectsOnSession(replacement, 42)
        original.zip(replacement).forEach { (old, new) ->
            assertNotSame(old, new)
            assertFalse(ShadowAudioEffect.getAudioEffects().contains(old))
        }
        assertEquals(replacement.toSet(), ShadowAudioEffect.getAudioEffects().toSet())
        assertSessions(open(41), close(41), open(42))
    }

    @Test
    fun `shared session handoff preserves enabled effects and attaches reverb to incoming track`() {
        enableInternalEffects()
        val outgoing = Target(sessionId = 41)
        bind(outgoing)
        val original = internalEffects()
        assertEffectsOnSession(original, 41)
        val reverb = original.filterIsInstance<PresetReverb>().single()
        val incoming = Target(sessionId = 41)

        bind(incoming)

        original.zip(internalEffects()).forEach { (old, current) -> assertSame(old, current) }
        assertEquals(original.toSet(), ShadowAudioEffect.getAudioEffects().toSet())
        verify(atLeast = 1) { incoming.player.setAuxEffectInfo(AuxEffectInfo(reverb.id, 1f)) }
        assertSessions(open(41))
    }

    @Test
    fun `closing releases enabled effects and cancels pending normalization work`() {
        enableInternalEffects()
        val target = Target(sessionId = 41)
        bind(target)
        assertEffectsOnSession(internalEffects(), 41)
        val normalizationJob = Job()
        field("volumeNormalizationJob").set(service, normalizationJob)

        invoke("closeAudioEffectSession")
        invoke("closeAudioEffectSession")

        assertTrue(normalizationJob.isCancelled)
        assertNull(field("volumeNormalizationJob").get(service))
        assertNull(field("bassBoost").get(service))
        assertNull(field("reverbPreset").get(service))
        assertNull(service.loudnessEnhancer)
        verify(exactly = 1) { target.player.clearAuxEffectInfo() }
        assertTrue(ShadowAudioEffect.getAudioEffects().isEmpty())
        assertSessions(open(41), close(41))
    }

    @Test
    fun `ready callback retries failed effect construction on unchanged session`() {
        assertFailedConstructionRetries(Player.EVENT_PLAYBACK_STATE_CHANGED)
    }

    @Test
    fun `failed reverb detach still releases effects and cancels normalization`() {
        enableInternalEffects()
        val target = Target(sessionId = 41)
        bind(target)
        val reverb = field("reverbPreset").get(service) as PresetReverb
        val normalizationJob = Job()
        field("volumeNormalizationJob").set(service, normalizationJob)
        var reverbWasLiveAtDetach = false
        every { target.player.clearAuxEffectInfo() } answers {
            reverbWasLiveAtDetach = ShadowAudioEffect.getAudioEffects().contains(reverb)
            throw IllegalStateException("detach failed")
        }

        invoke("closeAudioEffectSession")

        assertTrue(reverbWasLiveAtDetach)
        assertTrue(normalizationJob.isCancelled)
        assertNull(field("volumeNormalizationJob").get(service))
        assertNull(field("bassBoost").get(service))
        assertNull(field("reverbPreset").get(service))
        assertNull(service.loudnessEnhancer)
        assertTrue(ShadowAudioEffect.getAudioEffects().isEmpty())
        assertSessions(open(41), close(41))
    }

    @Test
    fun `session callback retries failed effect construction on unchanged session`() {
        assertFailedConstructionRetries(Player.EVENT_AUDIO_SESSION_ID)
    }

    private fun assertFailedConstructionRetries(event: Int) {
        enableInternalEffects()
        RetryableAudioEffectShadow.failNextBassBoostSetup = true
        val target = Target(sessionId = 41, state = Player.STATE_BUFFERING)
        bind(target)
        assertNull(field("bassBoost").get(service))
        val reverb = field("reverbPreset").get(service) as PresetReverb
        val loudness = requireNotNull(service.loudnessEnhancer)
        assertEquals(setOf(reverb, loudness), ShadowAudioEffect.getAudioEffects().toSet())

        target.state = Player.STATE_READY
        target.events(event)

        assertEffectsOnSession(internalEffects(), 41)
        assertSame(reverb, field("reverbPreset").get(service))
        assertSame(loudness, service.loudnessEnhancer)
        assertEquals(internalEffects().toSet(), ShadowAudioEffect.getAudioEffects().toSet())
        assertSessions(open(41))
    }

    private fun enableInternalEffects() {
        service.preferences.edit()
            .putBoolean(bassboostEnabledKey, true)
            .putFloat(bassboostLevelKey, 0.5f)
            .putString(audioReverbPresetKey, PresetsReverb.SMALLROOM.name)
            .putBoolean(volumeNormalizationKey, true)
            .commit()
    }

    private fun internalEffects(): List<AudioEffect> = listOf(
        field("bassBoost").get(service) as BassBoost,
        field("reverbPreset").get(service) as PresetReverb,
        requireNotNull(service.loudnessEnhancer),
    )

    private fun assertEffectsOnSession(effects: List<AudioEffect>, sessionId: Int) {
        assertEquals(setOf(BassBoost::class.java, PresetReverb::class.java, LoudnessEnhancer::class.java),
            effects.map { it.javaClass }.toSet())
        effects.forEach {
            assertEquals(sessionId, Shadow.extract<ShadowAudioEffect>(it).audioSession)
            assertTrue(ShadowAudioEffect.getAudioEffects().contains(it))
        }
        assertTrue(effects.filterIsInstance<BassBoost>().single().enabled)
        assertTrue(effects.filterIsInstance<PresetReverb>().single().enabled)
    }

    private inner class Target(
        var sessionId: Int,
        var state: Int = Player.STATE_READY,
        var readyToPlay: Boolean = true,
    ) {
        val listeners = mutableListOf<Player.Listener>()
        val player = mockk<ExoPlayer>(relaxed = true) {
            every { audioSessionId } answers { sessionId }
            every { playbackState } answers { state }
            every { playWhenReady } answers { readyToPlay }
            every { currentMediaItem } returns null
            every { addListener(any()) } answers { listeners += firstArg<Player.Listener>(); Unit }
        }

        fun events(event: Int) {
            listeners.single().onEvents(rawCallbackPlayer, this@PlayerServiceModernAudioSessionTest.events(event))
        }
    }

    private fun bind(target: Target) {
        service.player = target.player
        invokeTarget("bindAudioEffectPlayer", target.player)
    }

    private fun events(event: Int) = Player.Events(FlagSet.Builder().add(event).build())

    private fun broadcasts(): List<Intent> = shadowOf(application).broadcastIntents.filter {
        it.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION ||
            it.action == AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
    }

    private fun assertSessions(vararg expected: Pair<String, Int>) {
        assertEquals(expected.toList(), broadcasts().map {
            it.action to it.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        })
    }

    private fun open(id: Int) = AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION to id

    private fun close(id: Int) = AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION to id

    private fun field(name: String) = PlayerServiceModern::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }

    private fun invoke(name: String) = PlayerServiceModern::class.java.getDeclaredMethod(name).apply {
        isAccessible = true
    }.invoke(service)

    private fun invokeTarget(name: String, target: ExoPlayer) =
        PlayerServiceModern::class.java.getDeclaredMethod(name, ExoPlayer::class.java).apply {
            isAccessible = true
        }.invoke(service, target)
}

/** Fails the native constructor once while retaining Robolectric's real effect lifecycle. */
@Implements(AudioEffect::class)
class RetryableAudioEffectShadow : ShadowAudioEffect() {
    // Android 13 added device routing and attribution arguments to the native constructor.
    @Implementation(minSdk = 33, maxSdk = 33)
    @Suppress("UNUSED_PARAMETER")
    protected fun native_setup(
        audioEffectThis: Any?,
        type: String?,
        uuid: String?,
        priority: Int,
        audioSession: Int,
        deviceType: Int,
        deviceAddress: String?,
        id: IntArray?,
        descriptor: Array<Any?>?,
        attributionSource: Parcel?,
        probe: Boolean,
    ): Int {
        if (failNextBassBoostSetup && type == AudioEffect.EFFECT_TYPE_BASS_BOOST.toString()) {
            failNextBassBoostSetup = false
            return AudioEffect.ERROR_NO_INIT
        }
        return super.native_setup(audioEffectThis, type, uuid, priority, audioSession, id,
            descriptor, null)
    }

    companion object {
        var failNextBassBoostSetup = false
    }
}
