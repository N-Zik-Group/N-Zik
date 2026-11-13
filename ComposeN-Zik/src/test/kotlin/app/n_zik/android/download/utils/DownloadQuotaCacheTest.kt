package app.n_zik.android.download.utils

import android.app.Application
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DownloadQuotaCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `writes exceeding quota keep committed offline audio intact`() {
        val database = StandaloneDatabaseProvider(RuntimeEnvironment.getApplication())
        val root = temporary.newFolder("downloads").canonicalFile
        val cache = DownloadQuotaCache(SimpleCache(root, NoOpCacheEvictor(), database), 6, root)
        try {
            val original = cache.startReadWrite("old", 0, 5)
            try {
                val file = cache.startFile("old", 0, 5).apply { writeText("audio") }
                cache.commitFile(file, 5)
            } finally {
                cache.releaseHoleSpan(original)
            }

            val incoming = cache.startReadWrite("new", 0, -1)
            try {
                assertThrows(Cache.CacheException::class.java) { cache.startFile("new", 0, 5) }
                val unknownSize = cache.startFile("new", 0, -1).apply { writeText("audio") }
                assertThrows(Cache.CacheException::class.java) { cache.commitFile(unknownSize, 5) }
                assertFalse(unknownSize.exists())
                assertTrue(cache.isCached("old", 0, 5))
                assertFalse(cache.isCached("new", 0, 5))
            } finally {
                cache.releaseHoleSpan(incoming)
            }
        } finally {
            cache.release()
            database.close()
        }
    }
}
