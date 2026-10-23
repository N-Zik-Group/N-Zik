package app.n_zik.android.download.utils

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The download-cache validity check of [DownloadValidator.hasValidDownload] (Phase 1
 * download fix, 2026-10-21): a `STATE_COMPLETED` entry is only playable offline when
 * EVERY span recorded in the index still has its file on disk with the exact length
 * the index claims. The index alone lies — it keeps reporting spans whose files were
 * wiped underneath (the OS/OEM case, documented on Samsung).
 *
 * JUnit 4 + [RobolectricTestRunner] (executed through junit-vintage-engine on the JUnit
 * 5 platform): Robolectric provides the application [Context] a real [SimpleCache]
 * needs for its SQLite index — no mock of the media3 cache, the real library behavior
 * is what is under test.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadValidatorTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var cache: SimpleCache

    @After
    fun releaseCache() {
        if (::cache.isInitialized) cache.release()
    }

    private fun newCache() {
        cache = SimpleCache(
            tempFolder.root,
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(ApplicationProvider.getApplicationContext<Context>())
        )
    }

    /** Commits a span of [length] bytes at [position] under [key] and returns its file. */
    private fun commitSpan(key: String, position: Long, length: Long): File {
        cache.startReadWriteNonBlocking(key, position, length)
        val file = cache.startFile(key, position, length)
        file.writeBytes(ByteArray(length.toInt()))
        cache.commitFile(file, length)
        return file
    }

    @Test
    fun `a key with no indexed content is not a valid download`() {
        newCache()

        assertFalse(DownloadValidator.hasValidDownload(cache, "ABXtWqmArUU"))
    }

    @Test
    fun `a committed span with its intact file is a valid download`() {
        newCache()
        commitSpan("na47wMFfQCo", 0L, 4166806L)

        assertTrue(DownloadValidator.hasValidDownload(cache, "na47wMFfQCo"))
    }

    @Test
    fun `a span whose file was deleted is not a valid download`() {
        newCache()
        val file = commitSpan("niXNQ5dEcEA", 0L, 5233501L)
        assertTrue(file.delete())

        assertFalse(DownloadValidator.hasValidDownload(cache, "niXNQ5dEcEA"))
    }

    @Test
    fun `a span whose file was truncated is not a valid download`() {
        newCache()
        val file = commitSpan("CUCLNPOjPZw", 0L, 1000L)
        // Simulate partial corruption: keep only half the bytes
        file.writeBytes(ByteArray(500))

        assertFalse(DownloadValidator.hasValidDownload(cache, "CUCLNPOjPZw"))
    }

    @Test
    fun `a multi span download with one deleted file is not a valid download`() {
        newCache()
        val first = commitSpan("FS8p_F0Stog", 0L, 2000L)
        commitSpan("FS8p_F0Stog", 2000L, 3000L)
        assertTrue(DownloadValidator.hasValidDownload(cache, "FS8p_F0Stog"))
        first.delete()

        assertFalse(DownloadValidator.hasValidDownload(cache, "FS8p_F0Stog"))
    }
}
