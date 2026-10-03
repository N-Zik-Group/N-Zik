package app.n_zik.android.playback.services.automotive.models

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure JVM tests for [ArtworkBytesCache]: the LRU bound that keeps embedded
 * Android Auto artwork bytes paid once per source per process lifetime.
 */
class ArtworkBytesCacheTest {

    @Test
    fun `put then get returns the same bytes`() {
        val cache = ArtworkBytesCache()
        val bytes = byteArrayOf(1, 2, 3)
        cache.put("url-a", bytes)
        assertArrayEquals(bytes, cache.get("url-a"))
    }

    @Test
    fun `get on a missing key returns null`() {
        assertNull(ArtworkBytesCache().get("missing"))
    }

    @Test
    fun `putting beyond the bound evicts the least recently used entry`() {
        val cache = ArtworkBytesCache(maxEntries = 2)
        cache.put("a", byteArrayOf(1))
        cache.put("b", byteArrayOf(2))
        cache.get("a")            // refresh "a"; "b" becomes the eldest
        cache.put("c", byteArrayOf(3)) // evicts "b"
        assertNotNull(cache.get("a"))
        assertNull(cache.get("b"))
        assertNotNull(cache.get("c"))
    }

    @Test
    fun `updating an existing key does not grow the cache`() {
        val cache = ArtworkBytesCache(maxEntries = 2)
        cache.put("a", byteArrayOf(1))
        cache.put("b", byteArrayOf(2))
        cache.put("a", byteArrayOf(9))
        assertNotNull(cache.get("b"))
        assertArrayEquals(byteArrayOf(9), cache.get("a"))
    }
}
