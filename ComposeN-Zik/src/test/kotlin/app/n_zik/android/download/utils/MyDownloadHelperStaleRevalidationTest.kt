package app.n_zik.android.download.utils

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * S1 (Phase 1 download fix, 2026-10-21/22): the startup re-validation of the completed
 * library — the OS/OEM wipe case. Completed entries whose span files are gone are
 * removed one by one (the index entry only — the files are already dead, a
 * re-download restores the data); completed entries with intact files are left
 * untouched, and non-terminal records are out of scope
 * ([MyDownloadHelper.wipeStuckDownloadsOnStart] handles them). The removed count is
 * parked in [MyDownloadHelper.pendingStaleStartupCount] — the re-validation runs before
 * the app window is visible, where the system silently drops toasts, so MainActivity
 * shows the toast (`download_stale_startup_removed`) once the window has focus.
 *
 * JUnit 4 + [RobolectricTestRunner] (executed through junit-vintage-engine on the JUnit
 * 5 platform), mirroring [MyDownloadHelperStartupWipeTest]: a real [SimpleCache] backs
 * the validator, the [DownloadManager] is the only mock (its `removeDownload` is what
 * is verified), and the real [Download] objects are required because `DownloadRequest.id`
 * is a public field in media3, never intercepted by a stub.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyDownloadHelperStaleRevalidationTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var cache: SimpleCache

    /**
     * The valid (never released) cache left behind by [resetHelperState]. Created lazily in
     * the test that needs it — `MyDownloadHelper.downloadCache` is a process-wide lateinit
     * that cannot be "unset", so it must never be left pointing at a released [SimpleCache]
     * (a released instance would poison any later test class in the same JVM fork that
     * reads it without reassigning first).
     */
    private var resetCache: SimpleCache? = null

    @After
    fun resetHelperState() {
        MyDownloadHelper.pendingStaleStartupCount.value = 0
        MyDownloadHelper.downloads.value = emptyMap()
        if (!::cache.isInitialized) return
        MyDownloadHelper.downloadCache = resetCache ?: createResetCache()
        cache.release()
    }

    private fun createResetCache(): SimpleCache {
        val cache = SimpleCache(
            tempFolder.newFolder("reset-cache"),
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(ApplicationProvider.getApplicationContext<Context>())
        )
        resetCache = cache
        return cache
    }

    private fun download(id: String, state: Int): Download {
        val request = DownloadRequest.Builder(id, Uri.parse("https://music.example.com/stream/$id")).build()
        return Download(request, state, 0L, 0L, 0L, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE)
    }

    /** Creates the cache and commits one complete span under [id]; returns the span file. */
    private fun setupCacheWithSpan(id: String): File {
        cache = SimpleCache(
            tempFolder.root,
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(ApplicationProvider.getApplicationContext<Context>())
        )
        return commitSpan(id)
    }

    /**
     * Commits one complete span under [id] on the already-initialized [cache]; returns the
     * span file. A test needing several spans must reuse this on the same cache — media3
     * rejects a second [SimpleCache] instance on a folder already in use.
     */
    private fun commitSpan(id: String): File {
        cache.startReadWriteNonBlocking(id, 0L, 1000L)
        val file = cache.startFile(id, 0L, 1000L)
        file.writeBytes(ByteArray(1000))
        cache.commitFile(file, 1000L)
        return file
    }

    @Test
    fun `a completed entry with intact files survives the re-validation`() {
        setupCacheWithSpan("na47wMFfQCo")
        MyDownloadHelper.downloadCache = cache
        MyDownloadHelper.downloads.value = mapOf(
            "na47wMFfQCo" to download("na47wMFfQCo", Download.STATE_COMPLETED)
        )
        val manager = mockk<DownloadManager>(relaxed = true)

        MyDownloadHelper.revalidateCompletedDownloads(manager)

        verify(exactly = 0) { manager.removeDownload(any()) }
        assertEquals(0, MyDownloadHelper.pendingStaleStartupCount.value)
    }

    @Test
    fun `a completed entry whose files were wiped is removed`() {
        val file = setupCacheWithSpan("ABXtWqmArUU")
        // The OS/OEM wipe: the span file is gone, the index still reports it
        assertTrue(file.delete())
        MyDownloadHelper.downloadCache = cache
        MyDownloadHelper.downloads.value = mapOf(
            "ABXtWqmArUU" to download("ABXtWqmArUU", Download.STATE_COMPLETED)
        )
        val manager = mockk<DownloadManager>(relaxed = true)

        MyDownloadHelper.revalidateCompletedDownloads(manager)

        verify(exactly = 1) { manager.removeDownload("ABXtWqmArUU") }
        assertEquals(1, MyDownloadHelper.pendingStaleStartupCount.value)
    }

    @Test
    fun `only the stale completed entries are removed, the intact and non-terminal ones stay`() {
        val staleFile = setupCacheWithSpan("ABXtWqmArUU")
        assertTrue(staleFile.delete())
        // Intact second span on the same cache (a second SimpleCache on the same
        // folder is rejected by media3 — see commitSpan KDoc).
        commitSpan("na47wMFfQCo")
        MyDownloadHelper.downloadCache = cache
        MyDownloadHelper.downloads.value = mapOf(
            "ABXtWqmArUU" to download("ABXtWqmArUU", Download.STATE_COMPLETED),
            "na47wMFfQCo" to download("na47wMFfQCo", Download.STATE_COMPLETED),
            "inProgress1" to download("inProgress1", Download.STATE_DOWNLOADING),
        )
        val manager = mockk<DownloadManager>(relaxed = true)

        MyDownloadHelper.revalidateCompletedDownloads(manager)

        verify(exactly = 1) { manager.removeDownload("ABXtWqmArUU") }
        verify(exactly = 0) { manager.removeDownload("na47wMFfQCo") }
        verify(exactly = 0) { manager.removeDownload("inProgress1") }
        assertEquals(1, MyDownloadHelper.pendingStaleStartupCount.value)
    }

    @Test
    fun `an empty download library returns early without any removal`() {
        // The most common user case: the cache exists but nothing is completed — the
        // re-validation must be a no-op (no span reads, no removals).
        cache = SimpleCache(
            tempFolder.root,
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(ApplicationProvider.getApplicationContext<Context>())
        )
        MyDownloadHelper.downloadCache = cache
        MyDownloadHelper.downloads.value = emptyMap()
        val manager = mockk<DownloadManager>(relaxed = true)

        MyDownloadHelper.revalidateCompletedDownloads(manager)

        verify(exactly = 0) { manager.removeDownload(any()) }
        assertEquals(0, MyDownloadHelper.pendingStaleStartupCount.value)
    }
}
