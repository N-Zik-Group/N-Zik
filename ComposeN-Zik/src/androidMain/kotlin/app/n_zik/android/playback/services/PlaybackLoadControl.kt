package app.n_zik.android.playback.services

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/**
 * Issue #881 (gh-881), Phase 3 Fix C: back buffer duration (ms) kept behind the playhead.
 *
 * The media3 default is 0 (`DefaultLoadControl.DEFAULT_BACK_BUFFER_DURATION_MS`): a backward
 * seek always falls outside the buffered window, so `ProgressiveMediaPeriod.seekToUs` cannot
 * resolve it in-buffer and cancels the current load to re-open the full data-source chain
 * (field cost: 80–760 ms of BUFFERING per seek, progress bar stuck on the old position until
 * the seek commits).
 *
 * 35 s covers the app's rewind increments (DurationIndicator ±5/±10/±30 s,
 * `setSeekBackIncrementMs(5000)`) with headroom for sample alignment. Memory cost is a few
 * hundred KB for audio (bounded by the total buffer size).
 */
internal const val SEEK_BACK_BUFFER_MS: Int = 35_000

/**
 * Issue #881 (gh-881), Phase 3: post-REBUFFER settle target (ms) — the media3 default is
 * 2000. A rebuffer is a buffer depletion (network hiccup), NOT a user seek: after one, the
 * player only resumes once 2 s of media is buffered again. The app's tracks are served from
 * its disk caches (streaming LRU / downloads), where that refill is a few milliseconds of
 * wall time, so the 2 s target delayed the resume for no reason. 1000 ms resumes playback
 * faster after a depletion with a marginal stutter risk on slow networks.
 *
 * Note: the seek-commit target is a DIFFERENT knob — `bufferForPlaybackMs` (media3 default
 * 1000 ms, left unchanged here); the dominant per-seek cost is the data-source re-open
 * pipeline (Fix C back buffer makes in-window seeks skip it entirely).
 */
internal const val REBUFFER_SETTLE_TARGET_MS: Int = 1000

/**
 * Issue #881 (gh-881), Phase 3 Fix C: playback [LoadControl] with a back buffer so backward
 * seeks within [SEEK_BACK_BUFFER_MS] resolve in-buffer (no data-source re-open, no buffer
 * refill) instead of re-opening the whole chain.
 *
 * `retainBackBufferFromKeyframe = true` keeps the buffer before the seek target across
 * seeks (audio: every sample is a sync sample), so back-and-forth taps stay in-buffer.
 *
 * [REBUFFER_SETTLE_TARGET_MS] lowers the post-rebuffer settle target (see its KDoc).
 *
 * `setBufferDurationsMs` sets BOTH the streaming and the local-playback variants (the
 * `onlyGenericConfigurationMethodsCalled` builder flag), which is the desired behavior:
 * every other value is passed back as the media3 default, so nothing else changes — in
 * particular the forward in-buffer window (`DEFAULT_MIN_BUFFER_MS`/`DEFAULT_MAX_BUFFER_MS`
 * = 50 s) and the seek-commit target (`DEFAULT_BUFFER_FOR_PLAYBACK_MS` = 1 s).
 */
internal fun createSeekFriendlyLoadControl(): LoadControl =
    DefaultLoadControl.Builder()
        .setBackBuffer(SEEK_BACK_BUFFER_MS, /* retainBackBufferFromKeyframe = */ true)
        .setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            REBUFFER_SETTLE_TARGET_MS,
        )
        .build()
