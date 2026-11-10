package app.n_zik.android.playback.services

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import io.mockk.mockk
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class StreamingCacheReadersReservationTest {
    @Test
    fun `offline reader and asynchronous cleanup cannot overlap on a cache key`() {
        val cache = mockk<Cache>()
        StreamingCacheReaders.acquire(cache, "offline").use {
            assertNull(StreamingCacheReaders.reserveIdle(cache, setOf("offline")))
        }

        checkNotNull(StreamingCacheReaders.reserveIdle(cache, setOf("offline"))).use {
            assertTrue(StreamingCacheReaders.isReserved(cache, "offline"))
            assertNull(StreamingCacheReaders.reserveIdle(cache, setOf("offline")))
            assertThrows(IOException::class.java) { StreamingCacheReaders.acquire(cache, "offline") }
            StreamingCacheReaders.acquire(cache, "another").close()
        }

        assertFalse(StreamingCacheReaders.isReserved(cache, "offline"))
        StreamingCacheReaders.acquire(cache, "offline").close()
    }
}
