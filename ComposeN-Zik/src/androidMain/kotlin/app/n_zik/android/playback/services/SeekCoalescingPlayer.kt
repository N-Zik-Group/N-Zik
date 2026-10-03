package app.n_zik.android.playback.services

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.playback.services.diagnostics.PLAYBACK_DIAG_TAG
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Issue #881 (Phase 3, Fix B): coalesces rapid absolute seeks issued while the player is still
 * recovering from the previous one.
 *
 * Every `seekTo` on the progressive streams used by this app re-opens the whole data-source
 * chain (one full `DataSource.open()` + buffer refill per seek — 80 ms to several seconds on
 * field devices). A user seeking again before the refill finishes invalidates the refill and
 * the cycle restarts from zero: the progress bar never resumes, which is the perceived
 * "frozen bar / seek loop" of gh-881 (field logs 2026-10-03: 50+34 seeks, each triggering a
 * re-open, while the player never left BUFFERING).
 *
 * Behavior:
 * - A seek issued while the player is still in `BUFFERING` within [coalesceWindowMs] of the
 *   last applied seek is NOT applied immediately — it overwrites the single pending target.
 * - The pending target is applied as soon as the player reaches `READY` (settle), or after the
 *   window elapses even if the player never recovered (safety net: the user's last seek is
 *   never lost).
 * - Everything else — item-qualified seeks, relative seeks (back/forward), track navigation,
 *   `release` — passes through immediately and supersedes (clears) any coalesced target: a
 *   fresh explicit intent is always honored without delay.
 *
 * Pure delegation otherwise (`Player by upstream`, same pattern as [DiagExoPlayer]); no other
 * member's behavior changes. Sits between the guarded facade and the diagnostics wrapper, so
 * the Listen Together guest lock still vetoes seeks before they reach this coalescer.
 */
@UnstableApi
internal class SeekCoalescingPlayer(
    private val upstream: Player,
    private val coalesceWindowMs: Long = DEFAULT_COALESCE_WINDOW_MS,
    private val clockMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val settleScope: CoroutineScope = NzikDispatchers.fireAndForget(NzikDispatchers.UI),
) : Player by upstream {

    private val stateLock = Any()

    /** Clock of the last seek applied by this wrapper, still awaiting settle. `null` = idle. */
    private var inFlightSinceMs: Long? = null

    /** Latest coalesced target, waiting for the player to settle (or the safety timeout). */
    private var pendingSeekMs: Long? = null

    /** Safety-net job applying [pendingSeekMs] if the player never reaches `READY`. */
    private var settleJob: Job? = null

    /**
     * State observer registered directly on [upstream] in `init` — bypassing this facade's own
     * (delegated) `addListener` so the coalescer's events are not routed through the listener
     * list exposed to the guard/UI. (media3 1.10.1: the player calls the deprecated single-arg
     * [Player.Listener.onPlaybackStateChanged] — the form every other listener in this app
     * already uses — and [Player.Listener.onMediaItemTransition] on every current-item change.)
     */
    private val stateObserver = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> onPlayerSettled()
                Player.STATE_IDLE, Player.STATE_ENDED -> clearAll()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // A pending absolute position belongs to the previous item — drop it.
            clearAll()
        }
    }

    init {
        upstream.addListener(stateObserver)
    }

    // ── Coalesced: absolute seeks in the current item ─────────────────────────────────────────

    override fun seekTo(positionMs: Long) {
        // Issue #881 (Phase 3, Fix B, review patch): the recovering check and the
        // pending-write happen inside ONE lock section. If the player settles (READY →
        // in-flight cleared) between a separate check and act, the seek must apply
        // immediately — the old check-then-act could silently delay it by a full window.
        var coalesced = false
        var inFlightForMs = 0L
        synchronized(stateLock) {
            val inFlight = inFlightSinceMs
            if (inFlight != null && clockMs() - inFlight < coalesceWindowMs
                && upstream.playbackState == Player.STATE_BUFFERING
            ) {
                inFlightForMs = clockMs() - inFlight
                pendingSeekMs = positionMs
                coalesced = true
            }
        }
        if (coalesced) {
            Timber.tag(PLAYBACK_DIAG_TAG).d(
                "SEEK_COALESCED posMs=$positionMs inFlightForMs=$inFlightForMs windowMs=$coalesceWindowMs",
            )
            ensureSettleJob()
            return
        }
        applySeek(positionMs)
    }

    // ── Immediate: a fresh explicit intent supersedes any coalesced target ────────────────────

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        clearAll()
        upstream.seekTo(mediaItemIndex, positionMs)
    }

    override fun seekToDefaultPosition() {
        clearAll()
        upstream.seekToDefaultPosition()
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        clearAll()
        upstream.seekToDefaultPosition(mediaItemIndex)
    }

    override fun seekBack() {
        clearAll()
        upstream.seekBack()
    }

    override fun seekForward() {
        clearAll()
        upstream.seekForward()
    }

    override fun seekToPrevious() {
        clearAll()
        upstream.seekToPrevious()
    }

    override fun seekToNext() {
        clearAll()
        upstream.seekToNext()
    }

    override fun seekToPreviousMediaItem() {
        clearAll()
        upstream.seekToPreviousMediaItem()
    }

    override fun seekToNextMediaItem() {
        clearAll()
        upstream.seekToNextMediaItem()
    }

    override fun release() {
        clearAll()
        upstream.removeListener(stateObserver)
        upstream.release()
    }

    // ── Core ──────────────────────────────────────────────────────────────────────────────────

    private fun applySeek(positionMs: Long) {
        synchronized(stateLock) {
            pendingSeekMs = null
            settleJob?.cancel()
            settleJob = null
            inFlightSinceMs = clockMs()
        }
        upstream.seekTo(positionMs)
        armSettleJob()
    }

    /** Arms the safety-net timeout: after [coalesceWindowMs] the latest pending target is applied even if the player never settled. */
    private fun armSettleJob() {
        val job = settleScope.launch {
            delay(coalesceWindowMs)
            onSettleTimeout()
        }
        synchronized(stateLock) { settleJob = job }
    }

    /** Re-arms the safety net if it is no longer active (e.g. after a settle that cleared it). */
    private fun ensureSettleJob() {
        val needsJob = synchronized(stateLock) { settleJob?.isActive != true }
        if (needsJob) armSettleJob()
    }

    /** Safety net: still `BUFFERING` after the window (or the `READY` event was missed) — apply the latest pending target so the user's last seek is never lost. */
    private fun onSettleTimeout() {
        val pending = synchronized(stateLock) {
            pendingSeekMs?.also {
                pendingSeekMs = null
                settleJob = null
            }
        }
        if (pending != null) {
            applySeek(pending)
        } else {
            synchronized(stateLock) { inFlightSinceMs = null }
        }
    }

    /** The player reached `READY`: the seek settled — apply the latest coalesced target now, or clear the in-flight state. */
    private fun onPlayerSettled() {
        val pending = synchronized(stateLock) {
            pendingSeekMs?.also { pendingSeekMs = null }
        }
        if (pending != null) {
            applySeek(pending)
        } else {
            synchronized(stateLock) {
                inFlightSinceMs = null
                settleJob?.cancel()
                settleJob = null
            }
        }
    }

    /**
     * Issue #881 (Phase 3, Fix B, review patch): stops coalescing WITHOUT releasing [upstream] —
     * used when the owning player is handed to a freshly built facade (the crossfade swap).
     * Drops any pending target and the safety job, and unregisters the state observer, so the
     * orphaned wrapper can no longer seek — or watch — the player it used to wrap (it is
     * released later by `cleanupCrossfade()`, which bypasses the wrapper chain entirely).
     */
    fun detach() {
        clearAll()
        upstream.removeListener(stateObserver)
    }

    /** Drops any coalesced target and in-flight state (item change, relative/transport ops, `release`). */
    private fun clearAll() {
        synchronized(stateLock) {
            pendingSeekMs = null
            inFlightSinceMs = null
            settleJob?.cancel()
            settleJob = null
        }
    }

    companion object {
        /**
         * Field measurements (gh-881 logs 2026-10-03): per-seek settle 80–760 ms, user burst
         * intervals 300–500 ms — a 1 s window covers an entire burst while a single seek is
         * still applied with zero added latency.
         */
        const val DEFAULT_COALESCE_WINDOW_MS = 1000L
    }
}
