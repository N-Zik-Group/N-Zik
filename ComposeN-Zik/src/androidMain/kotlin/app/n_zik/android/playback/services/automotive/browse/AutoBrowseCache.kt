package app.n_zik.android.playback.services.automotive.browse

import androidx.media3.common.MediaItem

class AutoBrowseCache {
    private val cache = mutableMapOf<String, CacheEntry>()
    private val ttlMillis = 30_000L

    private data class CacheEntry(
        val items: List<MediaItem>,
        val timestamp: Long
    )

    @Synchronized
    fun get(key: String): List<MediaItem>? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > ttlMillis) {
            cache.remove(key)
            return null
        }
        return entry.items
    }

    @Synchronized
    fun put(key: String, items: List<MediaItem>) {
        cache[key] = CacheEntry(items, System.currentTimeMillis())
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }
}
