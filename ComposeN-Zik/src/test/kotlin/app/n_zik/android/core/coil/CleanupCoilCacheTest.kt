package app.n_zik.android.core.coil

import coil3.disk.DiskCache
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CleanupCoilCacheTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `cleanup preserves an open editor and rechecks entries committed after confirmation`() {
        val directory = temp.newFolder("coil")
        val cache = CleanupCoilCache(
            DiskCache.Builder().directory(directory.toPath().toOkioPath()).maxSizeBytes(1024).build(),
            Any(),
        )
        fun payloads() = directory.listFiles().orEmpty().filter { it.name.endsWith(".1") }.map { it.name }.toSet()
        try {
            checkNotNull(cache.openEditor("old")).apply {
                metadata.toFile().writeText("metadata")
                data.toFile().writeText("confirmed")
                commit()
            }
            val confirmed = payloads()
            val writer = checkNotNull(cache.openEditor("new"))
            writer.metadata.toFile().writeText("metadata")
            writer.data.toFile().writeText("after confirmation")
            fun cleanup() = cache.whileIdle {
                if (payloads() != confirmed) false
                else {
                    cache.clear()
                    true
                }
            }
            assertFalse(cleanup())
            writer.commit()
            assertFalse(cleanup())
            checkNotNull(cache.openSnapshot("new")).close()
            assertTrue(cache.remove("new"))
            assertTrue(cleanup())
            assertNull(cache.openSnapshot("old"))
        } finally {
            cache.shutdown()
        }
    }
}
