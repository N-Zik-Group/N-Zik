package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.it.fast4x.rimusic.utils.POSITION_POLL_INTERVAL_MS
import app.it.fast4x.rimusic.utils.positionAndDurationState
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #881 (gh-881), Phase 3.1: [positionAndDurationState] polls the live player position on
 * a plain timer (RiPlay model) with no seek latch — the former frame poller stopped writing
 * while an `isSeeking` flag waited for a `STATE_READY` callback, and the field logs show the
 * bar frozen on the previous track after a screen-off track change.
 *
 * No seek latch exists, so a seek that never delivers READY (the old latch's failure mode) is
 * modelled by simply moving the mocked position: it must reach the state.
 *
 * Phase 3.2 (R1): the player's own events (`onMediaItemTransition`, committed-seek
 * `onPositionDiscontinuity`) re-anchor the cache instantly, between poll ticks.
 *
 * Phase 3.3: the poll loop is unconditional — the sheet-visibility gate was removed because
 * the field logs (2026-10-04, FURY, OPPO / Android 15) show the bar frozen on the last seek
 * target for up to 5 s whenever gate and visible UI disagreed, while every healthy reference
 * (RiPlay/Kreate/RiMusic/Metrolist) polls regardless of sheet visibility. The listener is
 * captured via `slot` and driven directly in the tests below.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PositionAndDurationStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val player = mockk<Player>(relaxed = true)
    private var livePosition = 53_795L
    private var liveDuration = 241_661L
    private lateinit var state: State<Pair<Long, Long>>

    @Before
    fun setUp() {
        every { player.currentPosition } answers { livePosition }
        every { player.duration } answers { liveDuration }
    }

    @After
    fun tearDown() = unmockkAll()

    private fun content() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            state = player.positionAndDurationState()
        }
    }

    private fun tick() {
        composeRule.mainClock.advanceTimeBy(POSITION_POLL_INTERVAL_MS * 2)
        composeRule.waitForIdle()
    }

    @Test
    fun `follows the live player position across a track change with no listener callback`() {
        content()
        assertEquals(53_795L to 241_661L, state.value)

        // Track change + seek on the new track: no READY / transition callback is delivered
        // (the old poller's latch would have kept the previous track's 53 795 ms).
        livePosition = 2_976L
        liveDuration = 247_441L
        tick()
        assertEquals(2_976L to 247_441L, state.value)

        livePosition = 56_030L
        tick()
        assertEquals(56_030L to 247_441L, state.value)
    }

    @Test
    fun `the cache follows live playback unconditionally - no visibility gate`() {
        // Phase 3.3 (gh-881): documents the ungated helper contract — the poll follows the
        // live position on every tick. The gate's absence is enforced at compile time by the
        // removed parameter; the call-site removal is pinned at the GetSeekBar level by
        // GetSeekBarPendingSeekTargetTest (collapsed-sheet pause-between-songs trigger).
        content()
        assertEquals(53_795L to 241_661L, state.value)

        livePosition = 54_000L
        tick()
        assertEquals(54_000L to 241_661L, state.value)

        livePosition = 55_000L
        tick()
        assertEquals(55_000L to 241_661L, state.value)
    }

    @Test
    fun `the poll keeps the cached duration while the player duration is unset`() {
        // Phase 3.3 review (P1): while a stream is resolving after a track change the
        // player's duration is C.TIME_UNSET — the poll must keep the cached duration instead
        // of clobbering it (Metrolist's guard), then converge once the real duration is known.
        content()
        assertEquals(53_795L to 241_661L, state.value)

        livePosition = 2_976L
        liveDuration = C.TIME_UNSET
        tick()
        assertEquals(2_976L to 241_661L, state.value)

        liveDuration = 247_441L
        tick()
        assertEquals(2_976L to 247_441L, state.value)
    }

    // Phase 3.2 (R1): capture the listener the composable registers on the player and drive
    // it directly — no clock advance below, so the asserted value can only have been written
    // by the event, not by a poll tick.
    private val listenerSlot = slot<Player.Listener>()

    // media3 1.10.1 Player.PositionInfo has no simple (position, duration) constructor — the
    // listener under test ignores the arguments entirely, so a minimal valid instance suffices.
    private fun posInfo(positionMs: Long): Player.PositionInfo =
        Player.PositionInfo(null, 0, null, 0, positionMs, positionMs, 0, 1)

    @Test
    fun `a media item transition re-anchors the cache instantly - no poll tick needed`() {
        every { player.addListener(capture(listenerSlot)) } just Runs
        content()

        // Track change (field log scenario: it happens across screen off/on + background
        // cycles): the player is now on the new track at 2_976 ms, while the cached position
        // is still the previous track's 53_795 ms.
        livePosition = 2_976L
        liveDuration = 247_441L
        listenerSlot.captured.onMediaItemTransition(
            MediaItem.Builder().setMediaId("newTrack").build(),
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
        )

        // R1: the event write lands before the next poll tick. RiMusic keeps the previously
        // cached duration so the slider range does not jump while the stream resolves.
        assertEquals(2_976L to 241_661L, state.value)
    }

    @Test
    fun `a committed seek re-anchors the cache instantly - no poll tick needed`() {
        every { player.addListener(capture(listenerSlot)) } just Runs
        content()

        // ExoPlayer commits the seek to 56_030 ms and fires the SEEK discontinuity — the
        // cache must follow immediately, not wait for the next poll tick. The live
        // duration follows as well (metadata can arrive with the commit), unlike the
        // transition write which deliberately keeps the previously cached duration.
        livePosition = 56_030L
        liveDuration = 247_441L
        listenerSlot.captured.onPositionDiscontinuity(
            posInfo(2_976L),
            posInfo(56_030L),
            Player.DISCONTINUITY_REASON_SEEK,
        )

        assertEquals(56_030L to 247_441L, state.value)
    }

    @Test
    fun `non-seek discontinuities do not re-anchor the cache`() {
        every { player.addListener(capture(listenerSlot)) } just Runs
        content()

        // A REMOVE discontinuity (queue change handled elsewhere) is not a seek: the cache
        // must not be rewritten from a position that does not correspond to a seek commit.
        livePosition = 56_030L
        listenerSlot.captured.onPositionDiscontinuity(
            posInfo(2_976L),
            posInfo(56_030L),
            Player.DISCONTINUITY_REASON_REMOVE,
        )

        assertEquals(53_795L to 241_661L, state.value)
    }

    @Test
    fun `the cache re-seeds live when the player instance changes`() {
        // Phase 3.3 (gh-881): on a player swap (production crossfade: playerUpdateTrigger
        // bump + new player object) the poll effect restarts — its immediate first write
        // re-seeds the cache from the new player's live values (this replaces the Phase 3.2
        // one-shot re-seed, which only existed to cover the now-removed visibility gate).
        val playerState = mutableStateOf(player)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            state = playerState.value.positionAndDurationState()
        }
        composeRule.waitForIdle()

        val nextPlayer = mockk<Player>(relaxed = true)
        every { nextPlayer.currentPosition } returns 77_000L
        every { nextPlayer.duration } returns 180_000L
        playerState.value = nextPlayer
        // Two short advances (measured: the snapshot-flip recomposition lands on the second
        // clock advance in this harness).
        composeRule.mainClock.advanceTimeBy(30)
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(30)
        composeRule.waitForIdle()

        assertEquals(77_000L to 180_000L, state.value)
    }

    @Test
    fun `the event listener is unregistered when the composition is disposed`() {
        // Phase 3.2 review patch: DisposableListener's onDispose/removeListener is shared by
        // the three call sites' listeners on the same player — pin it, or a cleanup
        // regression would leak a listener feeding events to an abandoned cache.
        // (The composable is disposed by flipping a composition-level gate: ComposeTestRule
        // does not allow a second setContent on the same activity.)
        val show = mutableStateOf(true)
        every { player.addListener(capture(listenerSlot)) } just Runs
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            if (show.value) {
                state = player.positionAndDurationState()
            }
        }
        composeRule.waitForIdle()
        val captured = listenerSlot.captured

        show.value = false
        // Two short advances (measured: the snapshot-flip recomposition lands on the second
        // clock advance in this harness) so the disposal runs.
        composeRule.mainClock.advanceTimeBy(30)
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(30)
        composeRule.waitForIdle()

        verify(exactly = 1) { player.removeListener(captured) }
    }
}
