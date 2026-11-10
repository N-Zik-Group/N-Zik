package app.n_zik.android.core.profiles

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ReadOnlyProfileCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `reads shared offline bytes while leaving spans and index unchanged`() {
        val legacy = temporary.newFolder("legacy")
        val scratch = temporary.newFolder("scratch")
        legacy.resolve("abc.uid").createNewFile()
        val bytes = "offline bytes".encodeToByteArray()
        val span = legacy.resolve("2").apply { mkdirs() }.resolve("5.0.99.v3.exo").apply { writeBytes(bytes) }
        val index = legacy.resolve("exoplayer_internal.db")
        SQLiteDatabase.openOrCreateDatabase(index, null).use { db ->
            db.execSQL("CREATE TABLE ExoPlayerCacheIndexabc (id INTEGER, key TEXT, metadata BLOB)")
            db.execSQL("INSERT INTO ExoPlayerCacheIndexabc VALUES (5, 'track', X'00000000')")
        }
        val indexBefore = index.readBytes()
        val sourceFiles = legacy.walkTopDown().map { it.relativeTo(legacy).path }.sorted().toList()
        val cache = ReadOnlyProfileCache.open(legacy, index, scratch)
        val reader = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(null)
            .setCacheWriteDataSinkFactory(null).createDataSource()
        try {
            reader.open(DataSpec.Builder().setUri(Uri.parse("https://example.invalid/track"))
                .setKey("track").setLength(bytes.size.toLong()).build())
            val output = ByteArray(bytes.size)
            assertEquals(bytes.size, reader.read(output, 0, output.size))
            assertArrayEquals(bytes, output)
        } finally {
            reader.close()
        }

        assertTrue(cache.isCached("track", 0, bytes.size.toLong()))
        assertThrows(Cache.CacheException::class.java) { cache.startFile("track", 0, 5) }
        cache.removeResource("track")
        cache.release()
        assertArrayEquals(bytes, span.readBytes())
        assertArrayEquals(indexBefore, index.readBytes())
        assertEquals(sourceFiles, legacy.walkTopDown().map { it.relativeTo(legacy).path }.sorted().toList())
    }
}
