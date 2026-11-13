package app.n_zik.android.core.coil

import java.io.File
import java.io.IOException
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ReadOnlyCoilCacheTest {
    @TempDir lateinit var root: File

    @Test
    fun `opens committed images and leaves the protected journal and files byte exact`() {
        val key = "https://example.invalid/cover"
        val hash = key.encodeUtf8().sha256().hex()
        root.resolve("$hash.0").writeText("metadata")
        root.resolve("$hash.1").writeText("image")
        val journal = root.resolve("journal")
        val original = "libcore.io.DiskLruCache\n1\n3\n2\n\nCLEAN $hash 8 5\nREAD $hash\n"
        journal.writeText(original)
        val cache = ReadOnlyCoilCache(root, 100)

        cache.verifyReadableJournal()
        checkNotNull(cache.openSnapshot(key)).use { snapshot ->
            assertEquals("image", cache.fileSystem.read(snapshot.data) { readUtf8() })
            assertNull(snapshot.closeAndOpenEditor())
        }
        assertFalse(cache.remove(key))
        assertThrows(IOException::class.java) { cache.clear() }
        assertEquals(original, journal.readText())
        assertEquals("metadata", root.resolve("$hash.0").readText())
        assertEquals("image", root.resolve("$hash.1").readText())
    }
}
