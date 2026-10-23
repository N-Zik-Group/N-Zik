package app.n_zik.android.playback.services

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.download.utils.MyDownloadHelper
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
 * S2 (Phase 1 download fix, 2026-10-21): the stale-entry predicate
 * [isStaleDownloadEntry] — true only when the download index claims the track is
 * downloaded AND its span files are gone (the OS/OEM wipe case the pre-playback
 * self-heal in `PlayerServiceModern.onMediaItemTransition` acts on).
 *
 * JUnit 4 + [RobolectricTestRunner] (executed through junit-vintage-engine on the JUnit
 * 5 platform), mirroring [MyDownloadHelperStaleRevalidationTest]: a real [SimpleCache]
 * backs [app.n_zik.android.download.utils.DownloadValidator.hasValidDownload].
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StaleDownloadEntryTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var cache: SimpleCache

    @After
    fun resetHelperState() {
        if (::cache.isInitialized) cache.release()
        MyDownloadHelper.downloads.value = emptyMap()
    }

    private fun download(id: String): Download {
        val request = DownloadRequest.Builder(id, Uri.parse("https://music.example.com/stream/$id")).build()
        return Download(request, Download.STATE_COMPLETED, 0L, 0L, 0L, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE)
    }

    /** Creates the cache and commits one complete span under [id]; returns the span file. */
    private fun setupCacheWithSpan(id: String): File {
        cache = SimpleCache(
            tempFolder.root,
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(ApplicationProvider.getApplicationContext<Context>())
        )
        cache.startReadWriteNonBlocking(id, 0L, 1000L)
        val file = cache.startFile(id, 0L, 1000L)
        file.writeBytes(ByteArray(1000))
        cache.commitFile(file, 1000L)
        return file
    }

    @Test
    fun `an indexed entry whose files were wiped is stale`() {
        val file = setupCacheWithSpan("ABXtWqmArUU")
        // The OS/OEM wipe: the span file is gone, the index still reports it
        assertTrue(file.delete())
        MyDownloadHelper.downloadCache = cache
        MyDownloadHelper.downloads.value = mapOf(
            "ABXtWqmArUU" to download("ABXtWqmArUU")
        )

        assertTrue(isStaleDownloadEntry("ABXtWqmArUU", cache))
    }

    @Test
    fun `an indexed entry with intact files is not stale`() {
        setupCacheWithSpan("na47wMFfQCo")
        MyDownloadHelper.downloadCache = cache
        MyDownloadHelper.downloads.value = mapOf(
            "na47wMFfQCo" to download("na47wMFfQCo")
        )

        assertFalse(isStaleDownloadEntry("na47wMFfQCo", cache))
    }

    @Test
    fun `an entry absent from the download index is not stale`() {
        setupCacheWithSpan("na47wMFfQCo")
        MyDownloadHelper.downloadCache = cache
        // Nothing is marked downloaded — the first conjunct of the predicate is false,
        // so the entry cannot be stale whatever the files on disk say.
        MyDownloadHelper.downloads.value = emptyMap()

        assertFalse(isStaleDownloadEntry("na47wMFfQCo", cache))
    }
}
