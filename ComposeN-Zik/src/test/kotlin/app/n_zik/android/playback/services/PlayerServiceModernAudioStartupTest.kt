package app.n_zik.android.playback.services

import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Looper
import androidx.media3.common.FlagSet
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaController
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.profilePreferences
import app.n_zik.android.MainActivity
import app.n_zik.android.MainApplication
import app.n_zik.android.appContext
import app.n_zik.android.awaitLibraryReady
import app.n_zik.android.core.backup.LibraryStartup
import app.n_zik.android.core.backup.LibraryStartupException
import app.n_zik.android.core.backup.LibraryStartupState
import app.n_zik.android.core.backup.isLibraryReady
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.diagnostics.DiagExoPlayer
import app.n_zik.android.playback.services.automotive.session.AutoSessionCallback
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        (field("coroutineScope").get(service) as CoroutineScope).cancel()
        service.preferences.unregisterOnSharedPreferenceChangeListener(service)
        service.profilePreferences.unregisterOnSharedPreferenceChangeListener(
            field("sessionProfileListener").get(service) as SharedPreferences.OnSharedPreferenceChangeListener,
        )
        runCatching { service.cache.release() }
        PlayerServiceModern.isRunning = false
        unmockkAll()
    }

    @Test
    fun `controllers receive a stable idle session while library recovery is pending`() {
        mockkStatic("app.n_zik.android.core.backup.LibraryStartupWorkerGateKt")
        every { any<Context>().isLibraryReady() } returns false
        val controller = mockk<MediaSession.ControllerInfo> {
            every { packageName } returns "test.browser"
        }
        val session = requireNotNull(service.onGetSession(controller))
        val token = session.token
        val remoteFuture = MediaController.Builder(application, token).buildAsync()
        try {
            assertSame(session, service.onGetSession(controller))
            assertEquals(Player.Commands.EMPTY, session.player.availableCommands)
            assertEquals(Player.STATE_IDLE, session.player.playbackState)
            val callback = field("mediaLibrarySessionCallback").get(service) as AutoSessionCallback
            val root = callback.onGetLibraryRoot(session, controller, null)
            assertTrue("Legacy connection must receive the root immediately", root.isDone)
            val rootId = requireNotNull(root.get().value).mediaId
            val children = callback.onGetChildren(session, controller, rootId, 0, 20, null)
            assertTrue("Browsing must wait for playback readiness", !children.isDone)
            verify(exactly = 0) { MyDownloadHelper.getDownloadCache(any()) }
            pumpMainUntil { remoteFuture.isDone }
            val remote = remoteFuture.get()
            assertTrue(remote.isConnected)
            assertTrue(!remote.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))

            // Exercise the Media3 handoff with a real connected controller and a memory-only player.
            session.player = object : SimpleBasePlayer(Looper.getMainLooper()) {
                override fun getState(): State = State.Builder()
                    .setAvailableCommands(Player.Commands.Builder().add(Player.COMMAND_PLAY_PAUSE).build())
                    .build()
            }
            pumpMainUntil { remote.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
            assertEquals(token, session.token)
            assertTrue(remote.isConnected)
        } finally {
            MediaController.releaseFuture(remoteFuture)
            service.onDestroy()
        }
    }

    @Test
    fun `media button received during recovery enters foreground and replays once ready`() {
        mockkStatic("app.n_zik.android.core.backup.LibraryStartupWorkerGateKt")
        mockkStatic("app.n_zik.android.MainApplicationKt")
        val gate = CompletableDeferred<Unit>()
        every { any<Context>().isLibraryReady() } answers { gate.isCompleted }
        coEvery { any<Context>().awaitLibraryReady() } coAnswers { gate.await() }
        every { service["initializePlayback"]() } answers {
            field("playbackInitializationStarted").setBoolean(service, true)
        }
        val play = Intent(Intent.ACTION_MEDIA_BUTTON)
        every { service["dispatchPlaybackStart"](play, 0, 1) } returns Service.START_STICKY

        service.onCreate()
        val mainLooper = shadowOf(Looper.getMainLooper())
        mainLooper.idle()
        assertEquals(Service.START_STICKY, service.onStartCommand(play, 0, 1))
        assertEquals(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID, shadowOf(service).lastForegroundNotificationId)
        verify(exactly = 0) { service["initializePlayback"]() }
        verify(exactly = 0) { service["dispatchPlaybackStart"](play, 0, 1) }

        gate.complete(Unit)
        mainLooper.idle()
        verify(exactly = 1) { service["initializePlayback"]() }
        verify(exactly = 1) { service["dispatchPlaybackStart"](play, 0, 1) }
    }

    @Test
    fun `failed recovery removes foreground and a later media button retries with the same session`() {
        mockkStatic("app.n_zik.android.core.backup.LibraryStartupWorkerGateKt")
        mockkStatic("app.n_zik.android.MainApplicationKt")
        val gate = CompletableDeferred<Unit>()
        val readiness = MutableStateFlow<LibraryStartupState>(LibraryStartupState.Waiting)
        val startup = mockk<LibraryStartup> {
            every { state } returns readiness.asStateFlow()
            every { start() } answers { readiness.value = LibraryStartupState.Waiting }
        }
        every { service.application } returns mockk<MainApplication> {
            every { libraryStartup } returns startup
        }
        every { any<Context>().isLibraryReady() } answers { readiness.value == LibraryStartupState.Ready }
        coEvery { any<Context>().awaitLibraryReady() } coAnswers { gate.await() }
        every { service["initializePlayback"]() } answers {
            field("playbackInitializationStarted").setBoolean(service, true)
        }
        val firstPlay = Intent(Intent.ACTION_MEDIA_BUTTON)
        val secondPlay = Intent(Intent.ACTION_MEDIA_BUTTON)
        every { service["dispatchPlaybackStart"](any<Intent>(), 0, any<Int>()) } returns Service.START_STICKY
        val controller = mockk<MediaSession.ControllerInfo>()
        val mainLooper = shadowOf(Looper.getMainLooper())
        val serviceShadow = shadowOf(service)

        service.onCreate()
        mainLooper.idle()
        val session = requireNotNull(service.onGetSession(controller))
        service.onStartCommand(firstPlay, 0, 1)
        assertTrue(serviceShadow.isLastForegroundNotificationAttached)
        val launch = shadowOf(requireNotNull(serviceShadow.lastForegroundNotification.contentIntent)).savedIntent
        assertEquals(MainActivity::class.java.name, launch.component?.className)

        val error = IllegalStateException("Recovery failed")
        readiness.value = LibraryStartupState.Failed(error)
        gate.completeExceptionally(LibraryStartupException(error))
        mainLooper.idle()
        assertTrue(serviceShadow.isForegroundStopped)
        assertTrue(!serviceShadow.isLastForegroundNotificationAttached)
        assertSame(session, service.onGetSession(controller))
        verify(exactly = 0) { service["dispatchPlaybackStart"](any<Intent>(), 0, any<Int>()) }

        service.onStartCommand(secondPlay, 0, 2)
        assertTrue(serviceShadow.isLastForegroundNotificationAttached)
        verify(exactly = 1) { startup.start() }
        readiness.value = LibraryStartupState.Ready
        mainLooper.idle()
        assertSame(session, service.onGetSession(controller))
        verifyOrder {
            service["dispatchPlaybackStart"](firstPlay, 0, 1)
            service["dispatchPlaybackStart"](secondPlay, 0, 2)
        }
        session.release()
    }

    @Test
    fun `onCreate prefix installs primary listener which publishes real open and close broadcasts`() {
        // Suppress unrelated network/store background work in the real playback initialization.
        (field("coroutineScope").get(service) as CoroutineScope).cancel()
        try {
            service.onCreate()
            fail("Expected the named boundary after primary audio initialization")
        } catch (actual: StartupAudioBoundary) {
            assertSame(boundary, actual)
        }
        assertTrue(service.player is DiagExoPlayer)
        // Per-player sessions (Metrolist model): startup must not pin an explicit session;
        // the raw player keeps the one ExoPlayer allocated for it.
        verify(exactly = 0) { rawPlayer.setAudioSessionId(any()) }
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

    private fun pumpMainUntil(condition: () -> Boolean) {
        val mainLooper = shadowOf(Looper.getMainLooper())
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition() && System.nanoTime() < deadline) {
            mainLooper.idle()
            Thread.sleep(5)
        }
        assertTrue("Media3 did not reach the expected controller state", condition())
    }

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
