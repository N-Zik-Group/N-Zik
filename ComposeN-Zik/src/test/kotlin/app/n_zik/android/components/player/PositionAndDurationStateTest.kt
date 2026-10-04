package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
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
 * Phase 3.2 (R1/R3): the gated poll loop is only the base stream — the player's own events
 * (`onMediaItemTransition`, committed-seek `onPositionDiscontinuity`) re-anchor the cache
 * instantly, even while [positionAndDurationState] is inactive (screen-off / collapsed sheet),
 * and the visibility gate flip re-seeds the cache one-shot (Metrolist pattern). The listener
 * is captured via `slot` and driven directly in the tests below.
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

    private fun content(active: () -> Boolean = { true }) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            state = player.positionAndDurationState(active = active())
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
    fun `does not write while inactive`() {
        content(active = { false })

        livePosition = 99_000L
        tick()

        assertEquals(53_795L to 241_661L, state.value)
    }

    // Phase 3.2 (R1): capture the listener the composable registers on the player and drive
    // it directly — the field scenario is an event that fires while the sheet is INACTIVE
    // (screen off / collapsed), where the gated poll loop writes nothing.
    private val listenerSlot = slot<Player.Listener>()

    // media3 1.10.1 Player.PositionInfo has no simple (position, duration) constructor — the
    // listener under test ignores the arguments entirely, so a minimal valid instance suffices.
    private fun posInfo(positionMs: Long): Player.PositionInfo =
        Player.PositionInfo(null, 0, null, 0, positionMs, positionMs, 0, 1)

    @Test
    fun `a media item transition re-anchors the cache while the sheet is inactive`() {
        every { player.addListener(capture(listenerSlot)) } just Runs
        content(active = { false })

        // Track change while the screen is off (field log scenario): the player is now on the
        // new track at 2_976 ms, while the cached position is still the previous track's
        // 53_795 ms.
        livePosition = 2_976L
        liveDuration = 247_441L
        listenerSlot.captured.onMediaItemTransition(
            MediaItem.Builder().setMediaId("newTrack").build(),
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
        )

        // R1: the event write lands even though active=false. RiMusic keeps the previously
        // cached duration so the slider range does not jump while the stream resolves.
        assertEquals(2_976L to 241_661L, state.value)
    }

    @Test
    fun `a committed seek re-anchors the cache while the sheet is inactive`() {
        every { player.addListener(capture(listenerSlot)) } just Runs
        content(active = { false })

        // ExoPlayer commits the seek to 56_030 ms and fires the SEEK discontinuity — the
        // cache must follow immediately, not wait for the next gated poll tick. The live
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
        content(active = { false })

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
    fun `the cache re-seeds live when the visibility gate flips to active`() {
        // Phase 3.2 (R3): Metrolist's one-shot re-sync — the cache is written from the live
        // player as soon as the gate flips, before the poll loop's first 100 ms tick.
        // Harness note (measured): under the manual clock a snapshot flip recomposes on the
        // SECOND clock advance — the flip is therefore pumped with two short advances below
        // (total 60 ms stays below the poll loop's 100 ms first tick).
        val active = mutableStateOf(false)
        content(active = { active.value })

        livePosition = 99_000L
        active.value = true
        // Two short advances (measured: the snapshot-flip recomposition lands on the second
        // clock advance in this harness); total 60 ms stays below the poll loop's 100 ms
        // first tick, so only the one-shot re-seed can have produced the value.
        composeRule.mainClock.advanceTimeBy(30)
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(30)
        composeRule.waitForIdle()

        assertEquals(99_000L to 241_661L, state.value)
    }

    @Test
    fun `a media item transition re-anchors the cache while the sheet is active`() {
        // Phase 3.2 review patch: the event writes are unconditional — the R1 contract is
        // pinned for active=false above; pin the active=true half too, so a regression that
        // gates the event writes on visibility fails.
        every { player.addListener(capture(listenerSlot)) } just Runs
        content(active = { true })

        livePosition = 2_976L
        liveDuration = 247_441L
        listenerSlot.captured.onMediaItemTransition(
            MediaItem.Builder().setMediaId("newTrack").build(),
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
        )

        // No clock advance: whatever the poll loop did at composition it only ever saw the
        // old position — the asserted value can only have been written by the event.
        assertEquals(2_976L to 241_661L, state.value)
    }

    @Test
    fun `the cache re-seeds live when the player instance changes`() {
        // Phase 3.2 review patch: LaunchedEffect(this, active) also re-seeds on a player
        // swap (production crossfade: playerUpdateTrigger bump + new player object) — only
        // the gate flip was pinned before. With the sheet inactive the poll loop is gated,
        // so only the one-shot re-seed can have produced the new player's values.
        val playerState = mutableStateOf(player)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            state = playerState.value.positionAndDurationState(active = false)
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
                state = player.positionAndDurationState(active = true)
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
