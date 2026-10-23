package app.n_zik.android.download.utils

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache

/**
 * Download-cache validity checks (Phase 1 download fix, 2026-10-21).
 *
 * The OS / OEM storage management (documented on Samsung devices) can wipe the span
 * files under the download cache directory while the `exoplayer_internal.db`
 * DownloadIndex survives — leaving `STATE_COMPLETED` rows whose files no longer
 * exist ("ghost" entries). The index-only APIs (`getCachedSpans`, `isCached`,
 * [MyDownloadHelper.isSongDownloaded]) then keep claiming the track is downloaded
 * forever: the badge lies, "Nothing to download" is wrong, and offline playback falls
 * through to a network fetch that fails. These validators re-check the entries
 * against the actual files.
 */
@UnstableApi
object DownloadValidator {

    /**
     * Returns whether the download [mediaId] is actually playable offline, i.e. every
     * span recorded for it in the cache index still has its backing file on disk with
     * the exact length the index claims.
     *
     * The per-span file-length test is the same one the library itself applies when it
     * drops stale spans (`SimpleCache.getSpan` → `removeStaleSpans`); the index alone
     * is NOT a reliable existence check — it counts ghost spans.
     *
     * Safe to call from any thread: `SimpleCache` is internally monitor-synchronized,
     * so `getCachedSpans` is a synchronized read of the in-memory index DB returning
     * a defensive copy, and the only file-system work is one `File.length()` stat per
     * span. The blocking `startReadWrite` must never be used for an existence check
     * (it waits for write locks).
     *
     * @param downloadCache the download cache (e.g. [MyDownloadHelper.downloadCache])
     * @param mediaId the cache key the download was stored under (the media item id)
     * @return `false` when no span is indexed for [mediaId], or when any indexed span is
     *   a hole, has no file, or its file length differs from the recorded length
     */
    fun hasValidDownload(downloadCache: Cache, mediaId: String): Boolean {
        val spans = downloadCache.getCachedSpans(mediaId)
        if (spans.isEmpty()) return false
        return spans.all { span ->
            span.isCached && span.file?.length() == span.length
        }
    }
}
