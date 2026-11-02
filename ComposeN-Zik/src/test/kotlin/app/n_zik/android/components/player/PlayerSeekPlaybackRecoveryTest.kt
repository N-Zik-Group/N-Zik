package app.n_zik.android.components.player

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.PlayerTimelineType
import app.it.fast4x.rimusic.models.ui.UiMedia
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.GetSeekBar
import app.it.fast4x.rimusic.utils.playerTimelineTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.extensions.audiobar.utils.WaveformResult
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerSeekPlaybackRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private val preferences get() = ApplicationProvider.getApplicationContext<Application>().preferences
    private val generation = MutableStateFlow(0)
    private val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
    private var playback = Playback(false)
    private val visible = mutableStateOf(true)

    @After fun cleanup() {
        preferences.edit().clear().commit()
        listenTogetherGuestLock.value = false
        unmockkAll()
    }

    @Test fun `actual Wavy resumes from buffering callbacks and stays flat during pause and drag without Glass`() {
        assertRecovery(glass = false)
    }

    @Test fun `actual Wavy resumes from buffering callbacks and stays flat during pause and drag with Glass`() {
        assertRecovery(glass = true)
    }

    @Test fun `actual caller rebinds playback callbacks when the player changes and removes them on disposal`() {
        content(glass = true)
        awaitBinding("initial playback listener and active generation collector") {
            playback.playbackListeners.isNotEmpty() && generation.subscriptionCount.value > 0
        }
        compose.runOnIdle { playback.update(true) }
        settle()
        assertWave(wavy = true, context = "first player")
        val old = playback
        val oldPlaybackListener = old.playbackListeners.single()
        compose.runOnIdle {
            playback = Playback(false)
            generation.value++
            assertSame("Binder exposes the replacement player", playback.player, binder.player)
        }
        awaitBinding("replacement playback listener; old=${old.listeners.size}, new=${playback.listeners.size}") {
            playback.playbackListeners.isNotEmpty() && oldPlaybackListener !in old.listeners
        }
        settle()
        assertWave(wavy = false, context = "replacement seeds paused state")
        compose.runOnIdle { old.update(true) }
        settle()
        assertWave(wavy = false, context = "old player no longer owns timeline")
        compose.runOnIdle { playback.update(true) }
        settle()
        assertWave(wavy = true, context = "replacement callback")
        compose.runOnIdle { visible.value = false }
        awaitBinding("all replacement listeners removed on disposal") { playback.listeners.isEmpty() }
        assertTrue("Playback listener removed on disposal", playback.listeners.isEmpty())
    }

    private fun assertRecovery(glass: Boolean) {
        content(glass)
        settle()
        assertWave(wavy = false, context = "initial buffering glass=$glass")
        // These are ordinary fields, not Compose state. No position tick or caller argument changes.
        compose.runOnIdle { playback.update(true) }
        settle()
        assertWave(wavy = true, context = "buffering ends through callback glass=$glass")
        compose.runOnIdle { playback.update(false) }
        settle()
        assertWave(wavy = false, context = "pause glass=$glass")
        compose.runOnIdle { playback.update(true) }
        settle()
        assertWave(wavy = true, context = "play glass=$glass")
        compose.onNodeWithTag("player.seekBar.track").performTouchInput {
            down(Offset(width * .65f, height / 2f))
            moveTo(Offset(width * .8f, height / 2f), delayMillis = 100)
        }
        settle()
        assertWave(wavy = false, context = "held drag glass=$glass")
        compose.onNodeWithTag("player.seekBar.track").performTouchInput { up() }
        settle()
        assertWave(wavy = true, context = "release glass=$glass")
    }

    private fun content(glass: Boolean) {
        preferences.edit().clear().putString(playerTimelineTypeKey, PlayerTimelineType.Wavy.name).commit()
        listenTogetherGuestLock.value = false
        val cache = mockk<Cache>(relaxed = true)
        every { binder.player } answers { playback.player }
        every { binder.playerUpdateTrigger } returns generation
        every { binder.cache } returns cache
        every { binder.downloadCache } returns cache
        every { binder.service.currentSongStateDownload } returns MutableStateFlow(Download.STATE_STOPPED)
        mockkObject(WaveformExtractor)
        coEvery { WaveformExtractor.getOrExtractWaveform(any(), any(), any()) } returns
            WaveformResult.Success(List(150) { 50 })
        val media = UiMedia("playback-recovery", "Song", "Artist", 120_000L, false)
        val appearance = Appearance(DefaultDarkColorPalette,
            typographyOf(Color.White, true, false, FontType.Rubik), CircleShape, CircleShape, CircleShape)
        val bounds = PlayerInformationBounds()
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalPlayerServiceBinder provides binder,
                LocalPlayerInformationBounds provides bounds,
                LocalLifecycleOwner provides owner,
                LocalGlassPreviewSettings provides GlassSettings(enabled = glass),
            ) {
                Column(Modifier.requiredWidth(360.dp).background(Color.Black)
                    .testTag("timeline.capture").padding(top = 30.dp, bottom = 30.dp)) {
                    if (visible.value) GetSeekBar({ 60_000L }, { 120_000L }, media.id, media,
                        shouldBePlaying = true, isBuffering = true)
                }
            }
        }
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun awaitBinding(label: String, condition: () -> Boolean) {
        // DATA emissions also need Robolectric's main looper, independently of Compose frames.
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy(250)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            compose.waitForIdle()
            if (condition()) return
            Thread.sleep(25)
        }
        assertTrue("Playback binding timed out: $label; generation=${generation.value}, " +
            "collectors=${generation.subscriptionCount.value}, listeners=${playback.listeners.size}", condition())
    }

    private fun assertWave(wavy: Boolean, context: String) {
        val pixels = compose.onNodeWithTag("timeline.capture").captureToImage().toPixelMap()
        // Sample the played stroke far from both the cursor and the Glass press pane.
        // Its top edge is horizontal while paused/dragged, sinusoidal while playing.
        val tops = (40..110).map { x ->
            (15..49).firstOrNull { y -> pixels[x, y].let { it.red + it.green + it.blue > .2f } }
                ?: error("Missing played waveform at x=$x: $context")
        }
        val spread = tops.max() - tops.min()
        assertTrue("Expected ${if (wavy) "waves" else "flat stroke"}, edge spread=$spread: $context",
            if (wavy) spread >= 2 else spread <= 1)
    }

    private class Playback(initialPlaying: Boolean) {
        var playing = initialPlaying
        val listeners = mutableSetOf<Player.Listener>()
        // DurationIndicator installs a separate position listener that may stay composed.
        // Pin removal of the observer responsible for playback, rather than unrelated listeners.
        val playbackListeners get() = listeners.filter { listener ->
            listener.javaClass.declaredMethods.any { it.name == "onIsPlayingChanged" }
        }
        val player = mockk<Player>(relaxed = true)

        init {
            every { player.isPlaying } answers { playing }
            every { player.currentPosition } returns 60_000L
            every { player.duration } returns 120_000L
            every { player.addListener(any()) } answers { listeners += firstArg<Player.Listener>(); Unit }
            every { player.removeListener(any()) } answers { listeners -= firstArg<Player.Listener>(); Unit }
        }

        fun update(value: Boolean) {
            playing = value
            listeners.toList().forEach { it.onIsPlayingChanged(value) }
        }
    }
}
