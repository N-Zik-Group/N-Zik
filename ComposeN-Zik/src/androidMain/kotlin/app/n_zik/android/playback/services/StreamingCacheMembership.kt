package app.n_zik.android.playback.services

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import app.n_zik.android.core.database.FormatTable

/** Reads live cache coverage for the requested rows without retaining profile data. */
@UnstableApi
internal suspend fun FormatTable.cachedStreamingIds(
    cache: Cache,
    ids: List<String>,
    requirePositiveLength: Boolean = false,
): Set<String> = buildSet {
    ids.distinct().chunked(500).forEach { batch ->
        streamingCacheEntries(batch).forEach { entry ->
            if ((!requirePositiveLength || entry.contentLength > 0) &&
                cache.hasCachedStream(entry.songId, entry.contentLength)
            ) add(entry.songId)
        }
    }
}

@UnstableApi
internal fun Cache.hasCachedStream(id: String, length: Long?): Boolean =
    length != null && isCached(id, 0L, length)
