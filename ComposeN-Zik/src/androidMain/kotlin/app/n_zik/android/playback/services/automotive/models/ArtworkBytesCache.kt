package app.n_zik.android.playback.services.automotive.models

/**
 * Bounded LRU cache of encoded Android Auto artwork bytes, keyed by source URL.
 *
 * Mapping one large queue must not re-decode and re-compress covers that were
 * already embedded once in this process lifetime (queue rebuilds, shuffle,
 * playback resumption). The backing map is access-ordered, so the least
 * recently used entry is evicted first once [maxEntries] is reached.
 */
class ArtworkBytesCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {

    private val entries: LinkedHashMap<String, ByteArray> =
        object : LinkedHashMap<String, ByteArray>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>): Boolean =
                size > maxEntries
        }

    @Synchronized
    fun get(key: String): ByteArray? = entries[key]

    @Synchronized
    fun put(key: String, value: ByteArray) {
        entries[key] = value
    }

    companion object {
        /** Default bound: ~64 entries x ~50-60 Ko (512px JPEG q85) ≈ 3-4 Mo. */
        const val DEFAULT_MAX_ENTRIES = 64
    }
}
