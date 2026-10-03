package app.n_zik.android.playback.services.diagnostics

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import timber.log.Timber

/**
 * Issue #881 (gh-881): diagnostics wrapper that observes every seek command reaching a real
 * player, with the caller's stack trace (spec S1).
 *
 * media3 1.10.1 `ExoPlayer` is an interface (the concrete class is `InternalExoPlayer`, built
 * privately by `ExoPlayer.Builder`), so a subclassing wrapper is not possible — this class
 * implements the interface by full delegation to the real player and overrides ONLY the
 * `Player` seek family: each override logs one `PlaybackDiag` `SEEK_CALL` line, then delegates
 * to the raw player. Zero behavior change: every other member (and every seek call's side
 * effects) is byte-identical to calling the raw player.
 *
 * All seek paths converge here: the UI drives `binder.player` (guarded facade), the
 * MediaSession (notification / lockscreen / AA) drives the same facade, and the service's
 * internal logic uses the service's `player` field — all wrapping this instance.
 */
@UnstableApi
class DiagExoPlayer(private val delegate: ExoPlayer) : ExoPlayer by delegate {

    /** One `SEEK_CALL` line; [details] is a `k=v` fragment (may be empty). */
    private fun logSeek(op: String, details: String = "") {
        val detailPart = details.takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()
        Timber.tag(PLAYBACK_DIAG_TAG).d(
            "SEEK_CALL op=%s playerIdentity=%d%s stack=%s",
            op,
            System.identityHashCode(this),
            detailPart,
            // Drop this wrapper's OWN frames (logSeek + the seek override) so the journal starts
            // at the real caller; `Thread.getStackTrace` and the other JVM/framework frames are
            // filtered by seekCallerStack's prefixes. (A fixed drop(N) is NOT reliable here —
            // index 0 is `Thread.getStackTrace`, whose depth varies by JVM.)
            seekCallerStack(
                Thread.currentThread().stackTrace
                    .filter { it.className != DiagExoPlayer::class.java.name }
                    .toTypedArray(),
            ),
        )
    }

    override fun seekTo(positionMs: Long) {
        logSeek("seekTo", "posMs=$positionMs")
        delegate.seekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        logSeek("seekTo", "idx=$mediaItemIndex posMs=$positionMs")
        delegate.seekTo(mediaItemIndex, positionMs)
    }

    override fun seekToDefaultPosition() {
        logSeek("seekToDefaultPosition")
        delegate.seekToDefaultPosition()
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        logSeek("seekToDefaultPosition", "idx=$mediaItemIndex")
        delegate.seekToDefaultPosition(mediaItemIndex)
    }

    override fun seekBack() {
        logSeek("seekBack")
        delegate.seekBack()
    }

    override fun seekForward() {
        logSeek("seekForward")
        delegate.seekForward()
    }

    override fun seekToPrevious() {
        logSeek("seekToPrevious")
        delegate.seekToPrevious()
    }

    override fun seekToNext() {
        logSeek("seekToNext")
        delegate.seekToNext()
    }

    override fun seekToPreviousMediaItem() {
        logSeek("seekToPreviousMediaItem")
        delegate.seekToPreviousMediaItem()
    }

    override fun seekToNextMediaItem() {
        logSeek("seekToNextMediaItem")
        delegate.seekToNextMediaItem()
    }
}
