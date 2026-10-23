package app.n_zik.android.playback.exceptions

import java.io.IOException

/**
 * S5 (offline gate, 2026-10-22): marker thrown by the [DataSpec.process] fast-fail
 * (StreamResolver) when a network-only stream is resolved while offline.
 *
 * Extends [IOException] so ExoPlayer's load-error handling treats it like any load
 * failure (the retry policy receives it in `LoadErrorInfo.exception`), while carrying
 * a stable type so both offline gates — the load-level `isOfflineLoadFailure` and the
 * `onPlayerError` `shouldWaitForNetwork` — recognize the offline fast-fail even when
 * the connectivity monitor state is stale: right after a wifi switch the monitor can
 * still report online, and a plain-IOException fast-fail then slipped through the
 * gate into the recovery path ("Retrying: …" ×3 → "Playback failed: Source error
 * [unknown]" — the double-toast defect, device-verified 2026-10-22).
 */
class StreamResolutionOfflineException(videoId: String) :
    IOException("Stream resolution skipped for $videoId: no network")
