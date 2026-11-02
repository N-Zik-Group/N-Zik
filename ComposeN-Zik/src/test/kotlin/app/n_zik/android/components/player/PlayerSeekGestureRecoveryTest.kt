package app.n_zik.android.components.player

import android.Manifest
import android.app.Application
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
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
import app.it.fast4x.rimusic.utils.showRemainingSongTimeKey
import app.it.fast4x.rimusic.utils.showSkipTimeButtonsKey
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.extensions.audiobar.utils.WaveformResult
import app.n_zik.android.listentogether.ListenTogetherGuestGuardPlayer
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

/** Integration coverage: input reaches the selected native bar and GetSeekBar owns scrub cleanup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w900dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerSeekGestureRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private val preferences get() = ApplicationProvider.getApplicationContext<Application>().preferences

    @After fun cleanup() {
        listenTogetherGuestLock.value = false
        preferences.edit().clear().commit()
        unmockkAll()
    }

    @Test fun `all eight actual timelines recover after taps and horizontal drags with Glass on and off`() {
        val fixture = mountPlayer()
        for (type in PlayerTimelineType.entries) for (glass in listOf(false, true)) {
            val context = "$type glass=$glass"
            fixture.prepare(type, glass)
            track().performTouchInput { click(Offset(width * .3f, center.y)) }
            compose.runOnIdle {
                assertTrue("Tap must seek: $context", fixture.seeks.isNotEmpty())
                assertTrue("Tap must use the touched position: $context", fixture.seeks.last() in 25_000L..45_000L)
            }
            fixture.assertFollowsLivePosition("tap: $context")

            compose.runOnIdle { fixture.seeks.clear() }
            track().performTouchInput {
                down(Offset(width * .25f, center.y))
                moveTo(Offset(width * .5f, center.y), delayMillis = 32)
                moveTo(Offset(width * .75f, center.y), delayMillis = 32)
                up()
            }
            compose.runOnIdle {
                assertTrue("Drag must seek: $context", fixture.seeks.isNotEmpty())
                assertTrue("Drag must advance from its start: $context", fixture.seeks.last() in 65_000L..110_000L)
                assertTrue("Seeks must stay in range: $context", fixture.seeks.all { it in 0L..fixture.duration.value })
            }
            fixture.assertFollowsLivePosition("drag: $context")
        }
    }

    @Test fun `cancelled presses and vertical scroll takeover never leave an actual timeline scrubbing`() {
        val fixture = mountPlayer()
        for (type in PlayerTimelineType.entries) for (glass in listOf(false, true)) {
            for (verticalTakeover in listOf(false, true)) {
                val context = "$type glass=$glass verticalTakeover=$verticalTakeover"
                fixture.prepare(type, glass)
                track().performTouchInput { down(Offset(width * .3f, center.y)) }
                // Observe a distinct frame with the press alive, rather than cancelling before onPress runs.
                compose.mainClock.advanceTimeByFrame()
                if (verticalTakeover) {
                    track().performTouchInput {
                        moveTo(Offset(width * .3f, center.y + 100f), delayMillis = 32)
                        moveTo(Offset(width * .3f, center.y + 180f), delayMillis = 32)
                        up()
                    }
                } else {
                    track().performTouchInput { cancel() }
                }
                compose.runOnIdle {
                    assertEquals("A cancelled press must not commit a seek: $context", emptyList<Long>(), fixture.seeks)
                    if (verticalTakeover) assertTrue("The vertical parent must take over: $context", fixture.scrolled != 0f)
                }
                fixture.assertFollowsLivePosition("cancelled press: $context")
            }

            fixture.prepare(type, glass)
            track().performTouchInput {
                down(Offset(width * .25f, center.y))
                moveTo(Offset(width * .5f, center.y), delayMillis = 32)
                moveTo(Offset(width * .7f, center.y), delayMillis = 32)
            }
            compose.mainClock.advanceTimeByFrame()
            track().performTouchInput { cancel() }
            // Some native timelines commit established drags on cancel; all must release their display.
            fixture.assertFollowsLivePosition("cancelled established drag: $type glass=$glass")
        }
    }

    @Test fun `duration and media replacement during a press release the actual parent scrub state`() {
        val fixture = mountPlayer()
        for (type in PlayerTimelineType.entries) for (glass in listOf(false, true)) {
            for (replaceMedia in listOf(false, true)) {
                val context = "$type glass=$glass replaceMedia=$replaceMedia"
                fixture.prepare(type, glass)
                track().performTouchInput { down(Offset(width * .3f, center.y)) }
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle {
                    if (replaceMedia) fixture.mediaId.value = "replacement-${fixture.scene.value}"
                    else fixture.duration.value = 180_000L
                }
                compose.mainClock.advanceTimeByFrame()
                track().performTouchInput { cancel() }
                fixture.assertFollowsLivePosition("range or media replaced: $context")
                compose.runOnIdle {
                    assertEquals("Replacing a pressed range must not seek: $context", emptyList<Long>(), fixture.seeks)
                }
                // The replacement must remain usable without another recomposition or player restart.
                track().performTouchInput { down(Offset(width * .65f, center.y)) }
                compose.mainClock.advanceTimeByFrame()
                if (type != PlayerTimelineType.VisualizerBar) {
                    assertTrue("The current media must display its new press: $context",
                        compose.onAllNodesWithText(formatAsDuration(23_000L), useUnmergedTree = true)
                            .fetchSemanticsNodes().isEmpty())
                }
                track().performTouchInput { up() }
                compose.runOnIdle {
                    assertTrue("Replacement timeline must accept its next tap: $context", fixture.seeks.isNotEmpty())
                    assertTrue("Replacement range must bound its seeks: $context", fixture.seeks.all { it in 0L..fixture.duration.value })
                }
                fixture.assertFollowsLivePosition("replacement next gesture: $context")
            }
        }
    }

    @Test fun `media replacement cancels pressed and established gestures before release or further motion`() {
        val fixture = mountPlayer()
        for (type in PlayerTimelineType.entries) for (glass in listOf(false, true)) {
            for (drag in listOf(false, true)) for (continueMoving in listOf(false, true)) {
                val context = "$type glass=$glass drag=$drag continue=$continueMoving"
                fixture.prepare(type, glass)
                fixture.beginGesture(drag)
                compose.runOnIdle {
                    fixture.mediaId.value = "replacement-${fixture.scene.value}"
                    fixture.position.value = 23_000L
                    fixture.seeks.clear()
                }
                compose.waitForIdle()
                track().performTouchInput {
                    if (continueMoving) moveTo(Offset(width * .85f, center.y), delayMillis = 32)
                    up()
                }
                compose.runOnIdle {
                    assertEquals("An old gesture must never seek the new media: $context", emptyList<Long>(), fixture.seeks)
                }
                fixture.assertFollowsLivePosition("media replacement: $context")
                fixture.assertNextTapWorks(context)
            }
        }
    }

    @Test fun `shrinking and unavailable ranges cancel active gestures and remain bounded`() {
        val fixture = mountPlayer()
        for (type in PlayerTimelineType.entries) for (glass in listOf(false, true)) {
            for (nextDuration in listOf(30_000L, 0L, C.TIME_UNSET)) for (drag in listOf(false, true)) {
                val context = "$type glass=$glass duration=$nextDuration drag=$drag"
                fixture.prepare(type, glass)
                fixture.beginGesture(drag)
                compose.runOnIdle {
                    fixture.duration.value = nextDuration
                    fixture.position.value = if (nextDuration > 0) 12_000L else 0L
                    fixture.seeks.clear()
                }
                compose.waitForIdle()
                // The track can be replaced by the indeterminate placeholder; retained touch
                // coordinates still deliver the terminal event to the original input stream.
                track().performTouchInput { up() }
                compose.runOnIdle {
                    assertTrue("A changed range must never receive an invalid target: $context",
                        fixture.seeks.all { nextDuration > 0 && it in 0L..nextDuration })
                }
                if (nextDuration > 0) {
                    fixture.assertFollowsLivePosition(context)
                    fixture.assertNextTapWorks(context)
                } else {
                    track()
                        .performTouchInput { click(center) }
                    compose.runOnIdle {
                        assertEquals("Unavailable duration must be inert: $context", emptyList<Long>(), fixture.seeks)
                        fixture.duration.value = 120_000L
                    }
                    compose.waitForIdle()
                    fixture.assertNextTapWorks(context)
                }
            }
        }
    }

    @Test fun `track endpoints and drags outside either edge stay inside the playable range`() {
        val fixture = mountPlayer()
        for (type in PlayerTimelineType.entries) for (glass in listOf(false, true)) {
            for (end in listOf(false, true)) {
                val context = "$type glass=$glass end=$end"
                fixture.prepare(type, glass)
                val nativeInset = with(compose.density) { 10.dp.toPx() } + 1f
                track().performTouchInput {
                    click(Offset(if (end) width - nativeInset else nativeInset, center.y))
                }
                compose.runOnIdle {
                    assertTrue("Endpoint touch must seek: $context", fixture.seeks.isNotEmpty())
                    assertTrue("Endpoint target must stay bounded: $context", fixture.seeks.all { it in 0L..120_000L })
                    assertTrue("Endpoint must map near the native edge: $context",
                        if (end) fixture.seeks.last() >= 110_000L else fixture.seeks.last() <= 10_000L)
                    fixture.seeks.clear()
                }
                track().performTouchInput {
                    down(center)
                    moveTo(Offset(if (end) width * .8f else width * .2f, center.y), delayMillis = 32)
                    moveTo(Offset(if (end) width * 1.5f else -width * .5f, center.y), delayMillis = 32)
                    up()
                }
                compose.runOnIdle {
                    assertTrue("Out-of-bounds movement must not escape the range: $context",
                        fixture.seeks.all { it in 0L..120_000L })
                }
                fixture.assertFollowsLivePosition(context)
            }
        }
    }

    @Test fun `Glass changes and style replacement during a gesture leave the current bar usable`() {
        val fixture = mountPlayer()
        for (type in PlayerTimelineType.entries) for (glass in listOf(false, true)) {
            for (changeStyle in listOf(false, true)) {
                val context = "$type glass=$glass style=$changeStyle"
                fixture.prepare(type, glass)
                fixture.beginGesture(true)
                compose.runOnIdle {
                    if (changeStyle) preferences.edit().putString(playerTimelineTypeKey,
                        PlayerTimelineType.entries[(type.ordinal + 1) % PlayerTimelineType.entries.size].name).commit()
                    else fixture.glass.value = !glass
                    fixture.seeks.clear()
                }
                compose.waitForIdle()
                track().performTouchInput { moveTo(Offset(width * .8f, center.y), delayMillis = 32); up() }
                compose.runOnIdle {
                    assertTrue("Configuration changes must leave targets bounded: $context",
                        fixture.seeks.all { it in 0L..fixture.duration.value })
                }
                fixture.assertFollowsLivePosition(context)
                fixture.assertNextTapWorks(context)
            }
        }
    }

    @Test fun `guest lock engaging during press or drag prevents the terminal seek`() {
        val fixture = mountPlayer()
        for (type in PlayerTimelineType.entries) for (glass in listOf(false, true)) {
            for (drag in listOf(false, true)) {
                val context = "$type glass=$glass drag=$drag"
                fixture.prepare(type, glass)
                fixture.beginGesture(drag)
                compose.runOnIdle { listenTogetherGuestLock.value = true; fixture.seeks.clear() }
                compose.waitForIdle()
                track().performTouchInput { moveTo(Offset(width * .85f, center.y), delayMillis = 32); up() }
                compose.runOnIdle {
                    assertEquals("A lock engaged during input must prevent seeking: $context", emptyList<Long>(), fixture.seeks)
                    listenTogetherGuestLock.value = false
                }
                compose.waitForIdle()
                fixture.assertFollowsLivePosition(context)
                fixture.assertNextTapWorks(context)
            }
        }
    }

    @Test fun `secondary pointer does not commit until the native gesture has ended`() {
        val fixture = mountPlayer()
        for (type in PlayerTimelineType.entries) for (glass in listOf(false, true)) {
            val context = "$type glass=$glass"
            fixture.prepare(type, glass)
            track().performTouchInput { down(0, Offset(width * .25f, center.y)) }
            compose.waitForIdle()
            track().performTouchInput {
                down(1, Offset(width * .8f, center.y))
                up(1)
            }
            compose.runOnIdle {
                assertEquals("Lifting a secondary finger must not commit: $context", emptyList<Long>(), fixture.seeks)
            }
            track().performTouchInput { up(0) }
            compose.runOnIdle {
                assertTrue("Native first-pointer tap must remain bounded: $context", fixture.seeks.all { it in 0L..120_000L })
            }
            fixture.assertFollowsLivePosition(context)
            fixture.assertNextTapWorks(context)
        }
    }

    private fun track() = compose.onNodeWithTag("player.seekBar.track", useUnmergedTree = true)

    private inner class Fixture {
        val scene = mutableStateOf(0)
        val glass = mutableStateOf(false)
        val position = mutableStateOf(12_000L)
        val duration = mutableStateOf(120_000L)
        val mediaId = mutableStateOf("initial")
        val seeks = mutableListOf<Long>()
        var scrolled = 0f

        fun beginGesture(drag: Boolean) {
            track().performTouchInput {
                down(Offset(width * .3f, center.y))
                if (drag) {
                    moveTo(Offset(width * .5f, center.y), delayMillis = 32)
                    moveTo(Offset(width * .7f, center.y), delayMillis = 32)
                }
            }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }

        fun assertNextTapWorks(context: String) {
            compose.runOnIdle { seeks.clear() }
            track().performTouchInput { click(Offset(width * .65f, center.y)) }
            compose.runOnIdle {
                assertTrue("A fresh gesture must seek: $context", seeks.isNotEmpty())
                assertTrue("A fresh gesture must use the current range: $context", seeks.all { it in 0L..duration.value })
            }
        }

        fun prepare(type: PlayerTimelineType, enabled: Boolean) {
            compose.runOnIdle {
                listenTogetherGuestLock.value = false
                preferences.edit().putString(playerTimelineTypeKey, type.name).commit()
                scene.value += 1
                glass.value = enabled
                position.value = 12_000L
                duration.value = 120_000L
                mediaId.value = "gesture-${scene.value}"
                seeks.clear()
                scrolled = 0f
            }
            compose.mainClock.advanceTimeBy(64)
            compose.waitForIdle()
        }

        fun assertFollowsLivePosition(context: String) {
            // Let a committed seek be acknowledged before moving playback on independently.
            compose.mainClock.advanceTimeBy(240)
            compose.runOnIdle { position.value = 17_000L }
            compose.mainClock.advanceTimeBy(240)
            assertTrue("Duration label must follow live playback after $context",
                compose.onAllNodesWithText(formatAsDuration(17_000L), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty())
            compose.runOnIdle { position.value = 23_000L }
            compose.mainClock.advanceTimeBy(240)
            assertTrue("Duration label must continue following playback after $context",
                compose.onAllNodesWithText(formatAsDuration(23_000L), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty())
        }
    }

    private fun mountPlayer(): Fixture {
        listenTogetherGuestLock.value = false
        mockkObject(ListenTogetherGuestGuardPlayer.Companion)
        every { ListenTogetherGuestGuardPlayer.reportUiBlockedOp(any()) } returns Unit
        val fixture = Fixture()
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        preferences.edit().clear()
            .putString(playerTimelineTypeKey, PlayerTimelineType.Default.name)
            .putBoolean(showRemainingSongTimeKey, false)
            .putBoolean(showSkipTimeButtonsKey, false)
            .commit()
        val player = mockk<Player>(relaxed = true)
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        val cache = mockk<Cache>(relaxed = true)
        every { binder.player } returns player
        every { binder.cache } returns cache
        every { binder.downloadCache } returns cache
        every { binder.playerUpdateTrigger } returns MutableStateFlow(0)
        every { binder.service.currentSongStateDownload } returns MutableStateFlow(Download.STATE_STOPPED)
        every { player.currentPosition } answers { fixture.position.value }
        every { player.duration } answers { fixture.duration.value }
        // Exercise the real VisualizerBar input without starting hardware capture.
        every { player.isPlaying } returns false
        every { player.seekTo(any<Long>()) } answers {
            val target = firstArg<Long>()
            fixture.seeks += target
            fixture.position.value = target
        }
        mockkObject(WaveformExtractor)
        coEvery { WaveformExtractor.getOrExtractWaveform(any(), any(), any()) } returns
            WaveformResult.Success(List(150) { 50 })
        val appearance = Appearance(DefaultDarkColorPalette,
            typographyOf(Color.White, true, false, FontType.Rubik), CircleShape, CircleShape, CircleShape)
        compose.setContent {
            key(fixture.scene.value) {
                CompositionLocalProvider(
                    LocalAppearance provides appearance,
                    LocalPlayerServiceBinder provides binder,
                    LocalPlayerInformationBounds provides PlayerInformationBounds(),
                    LocalGlassPreviewSettings provides GlassSettings(enabled = fixture.glass.value),
                ) {
                    Column(Modifier.requiredWidth(360.dp).scrollable(
                        rememberScrollableState { delta -> fixture.scrolled += delta; delta },
                        Orientation.Vertical,
                    )) {
                        val media = UiMedia(fixture.mediaId.value, "Song", "Artist", fixture.duration.value, false)
                        GetSeekBar({ fixture.position.value }, { fixture.duration.value },
                            media.id, media, shouldBePlaying = true, isBuffering = false)
                    }
                }
            }
        }
        return fixture
    }
}
