package app.n_zik.android.playback.services

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import androidx.media3.common.FlagSet
import androidx.media3.common.Player
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.profilePreferences
import app.n_zik.android.appContext
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.diagnostics.DiagExoPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Executes the production startup prefix through primary creation and EQ-listener installation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlayerServiceModernAudioStartupTest {
    private lateinit var application: Application
    private lateinit var service: PlayerServiceModern
    private val listeners = mutableListOf<Player.Listener>()
    private val analyticsListeners = mutableListOf<AnalyticsListener>()
    private var state = Player.STATE_IDLE
    private var readyToPlay = false
    private val rawPlayer = mockk<ExoPlayer>(relaxed = true)
    private val boundary = StartupAudioBoundary()

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns application
        service = spyk(Robolectric.buildService(PlayerServiceModern::class.java).get(), recordPrivateCalls = true)
        val audioManager = mockk<AudioManager> {
            every { generateAudioSessionId() } returns 41
        }
        every { service.getSystemService(AudioManager::class.java) } returns audioManager
        // Suppress unrelated network/store background work, retaining synchronous startup itself.
        (field("coroutineScope").get(service) as CoroutineScope).cancel()
        service.preferences.edit().clear().commit()

        mockkStatic("app.n_zik.android.playback.services.PlayerServiceModernKt")
        every { mediaCacheLocation(any()) } returns MediaCacheLocation(
            application.cacheDir.resolve("audio-startup"), "audio-startup", "audio-startup-index.db",
        )

        mockkObject(MyDownloadHelper)
        every { MyDownloadHelper.getDownloadCache(any()) } returns mockk<Cache>(relaxed = true)
        every { service["createMediaSourceFactory"]() } returns mockk<DefaultMediaSourceFactory>(relaxed = true)
        // Playback is deterministic; onCreate still constructs its real diagnostics facade.
        mockkConstructor(ExoPlayer.Builder::class)
        every { anyConstructed<ExoPlayer.Builder>().build() } returns rawPlayer
        every { rawPlayer.audioSessionId } returns 41
        every { rawPlayer.playbackState } answers { state }
        every { rawPlayer.playWhenReady } answers { readyToPlay }
        every { rawPlayer.currentMediaItem } returns null
        every { rawPlayer.addListener(any()) } answers { listeners += firstArg<Player.Listener>(); Unit }
        every { rawPlayer.addAnalyticsListener(any()) } answers { analyticsListeners += firstArg<AnalyticsListener>(); Unit }
        // The next startup phase builds MediaSession/UI integration. Stop at that explicit
        // boundary; neither onCreate nor bindAudioEffectPlayer is stubbed or invoked reflectively.
        every { service["createGuestGuardPlayer"](any<Player>()) } throws boundary
    }

    @After
    fun tearDown() {
        service.preferences.unregisterOnSharedPreferenceChangeListener(service)
        service.profilePreferences.unregisterOnSharedPreferenceChangeListener(
            field("sessionProfileListener").get(service) as SharedPreferences.OnSharedPreferenceChangeListener,
        )
        runCatching { service.cache.release() }
        PlayerServiceModern.isRunning = false
        unmockkAll()
    }

    @Test
    fun `onCreate prefix installs primary listener which publishes real open and close broadcasts`() {
        try {
            service.onCreate()
            fail("Expected the named boundary after primary audio initialization")
        } catch (actual: StartupAudioBoundary) {
            assertSame(boundary, actual)
        }
        assertTrue(service.player is DiagExoPlayer)
        verify(exactly = 1) { rawPlayer.setAudioSessionId(41) }
        assertSame(service.player, field("audioEffectPlayer").get(service))
        val diagnostics = field("crossfadeAudioDiagnostics").get(service) as Map<*, *>
        val primaryDiagnostics = requireNotNull(diagnostics[service.player])
        assertTrue(analyticsListeners.any { it === primaryDiagnostics })
        val listener = field("audioEffectListener").get(service) as Player.Listener
        assertTrue("Startup must attach its EQ listener to the raw primary", listeners.any { it === listener })
        assertSessions()

        state = Player.STATE_READY
        readyToPlay = true
        listener.onEvents(rawPlayer, events(Player.EVENT_PLAYBACK_STATE_CHANGED))
        assertSessions(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION to 41)
        val opened = broadcasts().single()
        assertEquals(service.packageName, opened.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME))
        assertEquals(AudioEffect.CONTENT_TYPE_MUSIC, opened.getIntExtra(AudioEffect.EXTRA_CONTENT_TYPE, -1))

        readyToPlay = false
        listener.onEvents(rawPlayer, events(Player.EVENT_PLAY_WHEN_READY_CHANGED))
        assertSessions(
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION to 41,
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION to 41,
        )

        readyToPlay = true
        listener.onEvents(rawPlayer, events(Player.EVENT_PLAY_WHEN_READY_CHANGED))
        // The real shutdown helper is valid at this boundary; full onDestroy requires later init.
        PlayerServiceModern::class.java.getDeclaredMethod("closeAudioEffectSession").apply {
            isAccessible = true
        }.invoke(service)
        verify(exactly = 1) { rawPlayer.removeListener(listener) }
        listener.onEvents(rawPlayer, events(Player.EVENT_PLAY_WHEN_READY_CHANGED))
        assertSessions(
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION to 41,
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION to 41,
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION to 41,
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION to 41,
        )
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

    private fun field(name: String) = PlayerServiceModern::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }

    private class StartupAudioBoundary : RuntimeException()
}
