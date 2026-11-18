package app.n_zik.android.core.coil

import coil3.disk.DiskCache
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
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

    @Test
    fun `stale artwork writers cannot overwrite the durable key reused after restart`() {
        val directory = temp.newFolder("artwork")
        val base = "cover"
        val revisions = ArtworkDiskRevisions()
        val lock = Any()
        fun openCache(revisions: ArtworkDiskRevisions) = CleanupCoilCache(
            DiskCache.Builder().directory(directory.toPath().toOkioPath()).maxSizeBytes(1024).build(),
            lock, revisions::resolve,
        )
        val cache = openCache(revisions)
        try {
            val oldKey = revisions.key(base)
            val staleEditor = checkNotNull(cache.openEditor(oldKey))
            staleEditor.metadata.toFile().writeText("metadata")
            staleEditor.data.toFile().writeText("stale pixels")
            synchronized(lock) {
                cache.remove(oldKey)
                revisions.refresh(setOf(base))
            }
            assertNull(cache.openEditor(oldKey))
            assertNull(staleEditor.commitAndOpenSnapshot())
            checkNotNull(cache.openEditor(revisions.key(base))).apply {
                metadata.toFile().writeText("metadata")
                data.toFile().writeText("current pixels")
                commit()
            }
        } finally {
            cache.shutdown()
        }
        val restarted = ArtworkDiskRevisions()
        val restartedCache = openCache(restarted)
        try {
            checkNotNull(restartedCache.openSnapshot(restarted.key(base))).use {
                assertEquals("current pixels", it.data.toFile().readText())
            }
            val staleKey = restarted.key(base)
            synchronized(lock) {
                restartedCache.clear()
                restarted.clear()
            }
            assertNull(restartedCache.openEditor(staleKey))
            assertNull(restartedCache.openSnapshot(restarted.key(base)))
        } finally {
            restartedCache.shutdown()
        }
    }
}
