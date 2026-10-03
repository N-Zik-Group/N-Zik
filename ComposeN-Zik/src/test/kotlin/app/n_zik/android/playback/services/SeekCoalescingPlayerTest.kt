package app.n_zik.android.playback.services

import androidx.media3.common.Player
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

/**
 * Issue #881 (Phase 3, Fix B): [SeekCoalescingPlayer] — a seek issued while the player is still
 * recovering from the previous seek (`BUFFERING` within the coalescing window) must NOT be
 * applied immediately; the latest pending target is applied on settle (`READY`) or via the
 * safety timeout. Every fresh explicit intent (relative / transport / item-qualified ops) is
 * honored immediately and supersedes the coalesced target.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeekCoalescingPlayerTest {

    private val upstream = mockk<Player>(relaxed = true)
    private val clockMs = AtomicLong(0)
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(UnconfinedTestDispatcher(scheduler))

    private lateinit var coalescer: SeekCoalescingPlayer
    private lateinit var listener: Player.Listener

    private val captured = mutableListOf<String>()

    private val captureTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            captured += message
        }
    }

    @BeforeEach
    fun setUp() {
        clockMs.set(0)
        captured.clear()
        Timber.plant(captureTree)
        val slot = slot<Player.Listener>()
        every { upstream.addListener(capture(slot)) } just Runs
        every { upstream.playbackState } returns Player.STATE_BUFFERING
        coalescer = SeekCoalescingPlayer(
            upstream = upstream,
            coalesceWindowMs = 1000L,
            clockMs = { clockMs.get() },
            settleScope = scope,
        )
        listener = slot.captured
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
        Timber.uproot(captureTree)
        clearMocks(upstream)
    }

    /** Advances both the fake clock and the virtual coroutine time (they track the same timeline). */
    private fun advance(ms: Long) {
        clockMs.addAndGet(ms)
        scheduler.advanceTimeBy(ms)
    }

    @Test
    fun `a single seek is applied immediately with zero added latency`() {
        coalescer.seekTo(100L)

        verify(exactly = 1) { upstream.seekTo(100L) }
        advance(2000L) // settle window elapses with nothing pending
        verify(exactly = 1) { upstream.seekTo(100L) }
    }

    @Test
    fun `a burst within the window is coalesced to the last seek via the safety timeout`() {
        coalescer.seekTo(100L)
        advance(300L); coalescer.seekTo(200L) // coalesced
        advance(300L); coalescer.seekTo(300L) // coalesced (overwrites 200)
        // +1 ms past the exact boundary — tasks due exactly at `now + delay` do not fire on a
        // boundary-landing advanceTimeBy (same margin the InvincibleServiceTest uses: 30_001).
        advance(401L) // t≈1001: safety timeout applies the pending 300

        verify(exactly = 1) { upstream.seekTo(100L) }
        verify(exactly = 0) { upstream.seekTo(200L) }
        verify(exactly = 1) { upstream.seekTo(300L) }
        assertTrue(captured.any { it.startsWith("SEEK_COALESCED") }, "coalesced seeks must be journaled")
    }

    @Test
    fun `reaching READY applies the latest coalesced seek immediately`() {
        coalescer.seekTo(100L)
        advance(300L); coalescer.seekTo(200L) // coalesced
        advance(200L)
        listener.onPlaybackStateChanged(Player.STATE_READY) // settled → apply pending

        verify(exactly = 1) { upstream.seekTo(100L) }
        verify(exactly = 1) { upstream.seekTo(200L) }
        advance(5000L)
        verify(exactly = 1) { upstream.seekTo(200L) }
    }

    @Test
    fun `a seek outside the window is applied immediately`() {
        coalescer.seekTo(100L)
        advance(1500L) // window elapsed, nothing pending → in-flight cleared
        coalescer.seekTo(200L)

        verify(exactly = 1) { upstream.seekTo(100L) }
        verify(exactly = 1) { upstream.seekTo(200L) }
    }

    @Test
    fun `a seek while the player is READY is applied immediately even within the window`() {
        every { upstream.playbackState } returns Player.STATE_READY
        coalescer.seekTo(100L)
        advance(300L)
        coalescer.seekTo(200L) // state READY → not recovering → applied at once

        verify(exactly = 1) { upstream.seekTo(100L) }
        verify(exactly = 1) { upstream.seekTo(200L) }
    }

    @Test
    fun `a stuck player keeps coalescing across windows and always lands the last target`() {
        // The gh-881 episode: the player never leaves BUFFERING. Applies happen at most once
        // per window and the LAST pending target always wins.
        coalescer.seekTo(100L)
        advance(400L); coalescer.seekTo(200L)   // pending
        advance(601L) // t≈1001: safety timeout applies 200 (boundary margin, see above)
        verify(exactly = 1) { upstream.seekTo(200L) }
        advance(400L); coalescer.seekTo(300L)   // coalesced again (new window)
        advance(400L); coalescer.seekTo(400L)   // pending overwritten
        advance(201L) // t≈2001: safety timeout applies 400

        verify(exactly = 1) { upstream.seekTo(100L) }
        verify(exactly = 0) { upstream.seekTo(300L) }
        verify(exactly = 1) { upstream.seekTo(400L) }
    }

    @Test
    fun `a media item change drops the coalesced seek`() {
        coalescer.seekTo(100L)
        advance(300L); coalescer.seekTo(200L) // coalesced
        listener.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) // pending dropped
        advance(5000L)

        verify(exactly = 1) { upstream.seekTo(100L) }
        verify(exactly = 0) { upstream.seekTo(200L) }
    }

    @Test
    fun `relative and transport seeks apply immediately and drop the pending seek`() {
        coalescer.seekTo(100L)
        advance(300L); coalescer.seekTo(200L) // coalesced
        coalescer.seekForward()               // fresh intent → immediate, supersedes

        verify(exactly = 1) { upstream.seekForward() }
        advance(5000L)
        verify(exactly = 0) { upstream.seekTo(200L) }
    }

    @Test
    fun `an item-qualified seek applies immediately and drops the pending seek`() {
        coalescer.seekTo(100L)
        advance(300L); coalescer.seekTo(200L) // coalesced
        coalescer.seekTo(1, 5000L)           // fresh intent → immediate, supersedes

        verify(exactly = 1) { upstream.seekTo(1, 5000L) }
        advance(5000L)
        verify(exactly = 0) { upstream.seekTo(200L) }
    }

    @Test
    fun `release removes the state observer and releases upstream`() {
        coalescer.release()

        verify(exactly = 1) { upstream.removeListener(listener) }
        verify(exactly = 1) { upstream.release() }
    }

    // ── Review patches (2026-10-03): previously uncovered paths ──────────────────────────────

    @Test
    fun `IDLE and ENDED clear any pending coalesced seek`() {
        coalescer.seekTo(100L)
        advance(300L); coalescer.seekTo(200L) // coalesced
        listener.onPlaybackStateChanged(Player.STATE_IDLE) // pending dropped
        advance(5000L)
        verify(exactly = 1) { upstream.seekTo(100L) }
        verify(exactly = 0) { upstream.seekTo(200L) }

        // Same for ENDED: a terminal state never keeps a stale pending target alive.
        coalescer.seekTo(100L)
        advance(300L); coalescer.seekTo(200L) // coalesced
        listener.onPlaybackStateChanged(Player.STATE_ENDED) // pending dropped
        advance(5000L)
        verify(exactly = 0) { upstream.seekTo(200L) }
    }

    @Test
    fun `release cancels the active settle job and drops the pending target`() {
        coalescer.seekTo(100L)
        advance(300L); coalescer.seekTo(200L) // coalesced, settle job armed
        coalescer.release()

        verify(exactly = 1) { upstream.release() }
        advance(5000L) // the armed job must be cancelled — no late apply after release
        verify(exactly = 1) { upstream.seekTo(100L) }
        verify(exactly = 0) { upstream.seekTo(200L) }
    }

    @Test
    fun `the safety net re-arms after a READY settle cleared the previous job`() {
        coalescer.seekTo(100L) // applied, settle job fires at t=1000
        advance(300L); coalescer.seekTo(200L) // coalesced (job still active)
        listener.onPlaybackStateChanged(Player.STATE_READY) // settled at t=300: applies 200
        verify(exactly = 1) { upstream.seekTo(200L) }
        // The settle replaced the first job: the fresh in-flight seek (t=300) must get its
        // own safety net firing at t≈1300.
        advance(200L); coalescer.seekTo(300L) // coalesced again
        advance(801L) // t≈1301: the re-armed job fires → applies the pending 300
        verify(exactly = 1) { upstream.seekTo(100L) }
        verify(exactly = 1) { upstream.seekTo(200L) }
        verify(exactly = 1) { upstream.seekTo(300L) }
        advance(5000L)
        verify(exactly = 1) { upstream.seekTo(300L) }
    }

    @Test
    fun `detach clears the pending seek and unregisters without releasing upstream`() {
        // Crossfade swap contract (PlayerServiceModern detaches the old wrapper before
        // rebuilding the facade over a new player): pending + job dropped, observer removed,
        // upstream KEPT (it keeps fading out under the new facade).
        coalescer.seekTo(100L)
        advance(300L); coalescer.seekTo(200L) // coalesced
        coalescer.detach()

        verify(exactly = 1) { upstream.removeListener(listener) }
        verify(exactly = 0) { upstream.release() }
        advance(5000L)
        verify(exactly = 1) { upstream.seekTo(100L) }
        verify(exactly = 0) { upstream.seekTo(200L) }
    }
}
