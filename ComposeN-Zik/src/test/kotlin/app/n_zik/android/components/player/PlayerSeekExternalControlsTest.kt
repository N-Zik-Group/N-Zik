package app.n_zik.android.components.player

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
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
import app.it.fast4x.rimusic.utils.formatAsDuration
import app.it.fast4x.rimusic.utils.playerTimelineTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.showSkipTimeButtonsKey
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** Notification-equivalent Player commands/callbacks; no native notification UI or touch input. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerSeekExternalControlsTest {
    @get:Rule val compose = createComposeRule()
    private val application get() = ApplicationProvider.getApplicationContext<Application>()
    private val preferences get() = application.preferences
    private val scene = mutableStateOf(0)
    private val glass = mutableStateOf(false)
    private val playback = Playback()
    private val generation = MutableStateFlow(0)
    private val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)

    @After fun cleanup() {
        preferences.edit().clear().commit()
        listenTogetherGuestLock.value = false
        unmockkAll()
    }

    @Test fun `all actual timelines follow external controls and recover from buffering without touching the bar`() {
        mountPlayer()
        for (type in PlayerTimelineType.entries) for (enabled in listOf(false, true)) {
            val context = "$type glass=$enabled"
            compose.runOnIdle {
                preferences.edit().putString(playerTimelineTypeKey, type.name).commit()
                glass.value = enabled
                playback.reset()
                scene.value++
            }
            awaitListeners()
            settle()
            assertLabels(30_000L, 120_000L, "initial $context")
            val beforeSeek = trackPixels()
            compose.runOnIdle { playback.player.seekTo(90_000L) }
            settle()
            assertLabels(90_000L, 120_000L, "external seek $context")
            assertTrue("Native progress must move after an external seek: $context",
                beforeSeek != trackPixels())
            compose.runOnIdle { assertEquals(listOf(90_000L), playback.seeks) }

            compose.runOnIdle { playback.player.play() }
            settle()
            assertWaveState(type, active = true, "external play $context")
            compose.runOnIdle { playback.player.pause() }
            settle()
            assertWaveState(type, active = false, "external pause $context")
            compose.runOnIdle { playback.player.play() }
            settle()
            assertWaveState(type, active = true, "external resume $context")
            // Plain Player fields change; the caller's media/position state does not change.
            compose.runOnIdle { playback.buffering(true) }
            settle()
            assertWaveState(type, active = false, "buffering $context")
            assertLabels(90_000L, 120_000L, "buffering $context")
            compose.runOnIdle { playback.buffering(false) }
            settle()
            assertWaveState(type, active = true, "buffer recovery $context")

            compose.runOnIdle { playback.player.pause() }
            settle()
            var previousTrack = trackPixels()
            compose.runOnIdle { playback.player.seekToNextMediaItem() }
            settle()
            assertLabels(12_000L, 120_000L, "next equal duration $context")
            assertTrue("Next equal duration must move native progress: $context", previousTrack != trackPixels())
            previousTrack = trackPixels()
            compose.runOnIdle { playback.player.seekToPreviousMediaItem() }
            settle()
            assertLabels(30_000L, 120_000L, "previous equal duration $context")
            assertTrue("Previous equal duration must move native progress: $context", previousTrack != trackPixels())
            compose.runOnIdle { playback.player.seekToNextMediaItem() }
            settle()
            previousTrack = trackPixels()
            compose.runOnIdle { playback.player.seekToNextMediaItem() }
            settle()
            assertLabels(45_000L, 180_000L, "next different duration $context")
            assertTrue("Next different duration must move native progress: $context", previousTrack != trackPixels())
            // The newly selected track can buffer independently of controller play/pause commands.
            compose.runOnIdle { playback.buffering(true) }
            settle()
            assertLabels(45_000L, 180_000L, "changed-track buffering $context")
            assertWaveState(type, active = false, "changed-track buffering $context")
            compose.runOnIdle { playback.buffering(false) }
            settle()
            assertLabels(45_000L, 180_000L, "changed-track buffer recovery $context")
            assertWaveState(type, active = true, "changed-track buffer recovery $context")
            // Freeze native geometry through a player callback before comparing progress again.
            compose.runOnIdle { playback.updatePlaying(false) }
            settle()
            previousTrack = trackPixels()
            compose.runOnIdle { playback.player.seekToPreviousMediaItem() }
            settle()
            assertLabels(12_000L, 120_000L, "previous different duration $context")
            assertTrue("Previous different duration must move native progress: $context", previousTrack != trackPixels())
            compose.runOnIdle {
                assertEquals("Controller callbacks must not cause UI seeks: $context",
                    listOf(90_000L), playback.seeks)
                assertEquals("Only the two controller play commands: $context", 2, playback.playCalls)
                assertEquals("Only the two controller pause commands: $context", 2, playback.pauseCalls)
            }
        }
    }

    private fun mountPlayer() {
        preferences.edit().clear().putBoolean(showSkipTimeButtonsKey, false).commit()
        listenTogetherGuestLock.value = false
        // Skip permission UI; invalid session keeps hardware FFT capture outside this contract test.
        shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val cache = mockk<Cache>(relaxed = true)
        every { binder.player } returns playback.player
        every { binder.playerUpdateTrigger } returns generation
        every { binder.cache } returns cache
        every { binder.downloadCache } returns cache
        every { binder.service.currentSongStateDownload } returns MutableStateFlow(Download.STATE_STOPPED)
        mockkObject(WaveformExtractor)
        coEvery { WaveformExtractor.getOrExtractWaveform(any(), any(), any()) } returns
            WaveformResult.Success(List(150) { 50 })
        val appearance = Appearance(DefaultDarkColorPalette,
            typographyOf(Color.White, true, false, FontType.Rubik), CircleShape, CircleShape, CircleShape)
        val bounds = PlayerInformationBounds()
        val owner = object : LifecycleOwner { override val lifecycle = LifecycleRegistry(this) }
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalPlayerServiceBinder provides binder,
                LocalPlayerInformationBounds provides bounds,
                LocalLifecycleOwner provides owner,
                LocalGlassPreviewSettings provides GlassSettings(enabled = glass.value),
            ) {
                key(scene.value) {
                    // The host mirrors the service's callback-driven media/position contract.
                    // External commands never write these states or a slider value directly.
                    val snapshot = androidx.compose.runtime.remember { mutableStateOf(playback.snapshot()) }
                    DisposableEffect(playback.player) {
                        val listener = object : Player.Listener {
                            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                                snapshot.value = playback.snapshot()
                            }
                            override fun onPositionDiscontinuity(reason: Int) {
                                snapshot.value = playback.snapshot()
                            }
                        }
                        playback.player.addListener(listener)
                        onDispose { playback.player.removeListener(listener) }
                    }
                    val current = snapshot.value
                    val media = UiMedia(current.id, "Song", "Artist", current.duration, false)
                    Column(Modifier.requiredWidth(360.dp).background(Color.Black)
                        .testTag("external.timeline.capture").padding(top = 30.dp, bottom = 30.dp)) {
                        GetSeekBar({ current.position }, { current.duration }, media.id, media,
                            shouldBePlaying = true, isBuffering = false)
                    }
                }
            }
        }
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun awaitListeners() {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(250)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            compose.waitForIdle()
            if (generation.subscriptionCount.value > 0 && playback.listeners.any { listener ->
                    listener.javaClass.declaredMethods.any { it.name == "onIsPlayingChanged" }
                }) return
            Thread.sleep(25)
        }
        assertTrue("Production playback listener must bind", generation.subscriptionCount.value > 0)
        assertTrue("Production playback callback must bind", playback.listeners.any { listener ->
            listener.javaClass.declaredMethods.any { it.name == "onIsPlayingChanged" }
        })
    }

    private fun assertLabels(position: Long, duration: Long, context: String) {
        for (value in listOf(position, duration, duration - position)) {
            assertTrue("Expected ${formatAsDuration(value)}: $context",
                compose.onAllNodesWithText(formatAsDuration(value), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty())
        }
    }

    private fun trackPixels(): List<Int> {
        val pixels = compose.onNodeWithTag("player.seekBar.track", useUnmergedTree = true)
            .captureToImage().toPixelMap()
        return List(pixels.width * pixels.height) { index ->
            pixels[index % pixels.width, index / pixels.width].hashCode()
        }
    }

    private fun assertWaveState(type: PlayerTimelineType, active: Boolean, context: String) {
        if (type != PlayerTimelineType.Wavy && type != PlayerTimelineType.AudioWaves) return
        val pixels = compose.onNodeWithTag("external.timeline.capture").captureToImage().toPixelMap()
        if (type == PlayerTimelineType.Wavy) {
            val current = playback.snapshot()
            val playedEnd = (pixels.width * current.position / current.duration).toInt()
            val sampleEnd = minOf(110, playedEnd - 20)
            assertTrue("Played stroke must have enough room to sample: $context", sampleEnd > 20)
            val tops = (20..sampleEnd).map { x ->
                (15..49).firstOrNull { y -> pixels[x, y].let { it.red + it.green + it.blue > .2f } }
                    ?: error("Missing played stroke: $context")
            }
            val spread = tops.max() - tops.min()
            assertTrue("Expected ${if (active) "waves" else "flat stroke"}, spread=$spread: $context",
                if (active) spread >= 2 else spread <= 1)
        } else {
            // Exclude the cursor near 75% and labels below the 40 dp native waveform slot.
            val colored = (40..110).sumOf { x ->
                (30..69).count { y -> pixels[x, y].let { it.red + it.green + it.blue > .2f } }
            }
            assertTrue("Expected ${if (active) "expanded" else "flat"} AudioWaves, pixels=$colored: $context",
                if (active) colored > 500 else colored < 300)
        }
    }

    private data class Snapshot(val id: String, val position: Long, val duration: Long)

    private class Playback {
        val player = mockk<Player>(relaxed = true)
        val listeners = mutableSetOf<Player.Listener>()
        val seeks = mutableListOf<Long>()
        var playCalls = 0
        var pauseCalls = 0
        private var track = 0
        private var position = 30_000L
        private var playing = false
        private var state = Player.STATE_READY
        private val durations = listOf(120_000L, 120_000L, 180_000L)
        private val positions = listOf(30_000L, 12_000L, 45_000L)

        init {
            every { player.isPlaying } answers { playing }
            every { player.playbackState } answers { state }
            every { player.currentPosition } answers { position }
            every { player.duration } answers { durations[track] }
            every { player.audioSessionId } returns -1
            every { player.addListener(any()) } answers { listeners += firstArg<Player.Listener>(); Unit }
            every { player.removeListener(any()) } answers { listeners -= firstArg<Player.Listener>(); Unit }
            every { player.seekTo(any<Long>()) } answers {
                position = firstArg()
                seeks += position
                listeners.toList().forEach { it.onPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK) }
            }
            every { player.play() } answers { playCalls++; updatePlaying(true) }
            every { player.pause() } answers { pauseCalls++; updatePlaying(false) }
            every { player.seekToNextMediaItem() } answers { transition(1) }
            every { player.seekToPreviousMediaItem() } answers { transition(-1) }
        }

        fun reset() {
            track = 0
            position = positions[track]
            playing = false
            state = Player.STATE_READY
            seeks.clear()
            playCalls = 0
            pauseCalls = 0
        }

        fun snapshot() = Snapshot("external-$track", position, durations[track])

        fun buffering(value: Boolean) {
            state = if (value) Player.STATE_BUFFERING else Player.STATE_READY
            listeners.toList().forEach { it.onPlaybackStateChanged(state) }
            updatePlaying(!value)
        }

        fun updatePlaying(value: Boolean) {
            playing = value
            listeners.toList().forEach { it.onIsPlayingChanged(value) }
        }

        private fun transition(delta: Int) {
            track = (track + delta).coerceIn(durations.indices)
            position = positions[track]
            val media = MediaItem.Builder().setMediaId("external-$track").build()
            listeners.toList().forEach { it.onMediaItemTransition(media, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) }
        }
    }
}
