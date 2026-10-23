package app.n_zik.android.download.utils

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * S1 (Phase 1 download fix, 2026-10-21): the INIT call-site pin — the startup
 * re-validation is wired inside the private `ensureDownloadManagerInitialized` right
 * after `getDownloads()`; the sibling function-level tests
 * ([MyDownloadHelperStaleRevalidationTest]) cover WHAT it does, this test covers that
 * the wiring EXISTS and WHERE it runs. It drives the REAL public entry point
 * (`MyDownloadHelper.getDownloadManager`) end-to-end under Robolectric with a real
 * [androidx.media3.exoplayer.offline.DownloadManager] + real [DefaultDownloadIndex]
 * (the same `exoplayer_internal.db` the init opens) + real [SimpleCache], and proves:
 *  (a) the S1 call-site runs at init — a pre-seeded stale `STATE_COMPLETED` entry is
 *      removed; delete the `coroutineScope.launch { revalidateCompletedDownloads(...) }`
 *      line and this test fails;
 *  (b) the revalidation work lands on the `NzikDispatchers.DATA` pool, never on the main
 *      thread — the off-main convention of `rules/CODE.md` (issue #606 verification pillar).
 *
 * JUnit 4 + [RobolectricTestRunner] (executed through junit-vintage-engine on the JUnit
 * 5 platform). The revalidation thread is observed on [Cache.getCachedSpans] through a
 * delegating spy — the validator is the only index-read caller on this path, and it is
 * invoked from the revalidation coroutine. `NzikDispatchers.DATA` is `Dispatchers.IO`
 * (standard `DefaultDispatcher-worker-*` pool threads — the scope-level `CoroutineName`
 * never renames the underlying thread), so the off-main proof is the pool thread-name
 * prefix. media3 1.10.1 `DownloadManager.removeDownload`
 * is self-contained (internal HandlerThread + `DefaultDownloadIndex.removeDownload`,
 * listener notification on the main looper), so no
 * [app.n_zik.android.download.services.MyDownloadService] start is involved — the
 * removal is deterministic. Robolectric's main looper is paused, so the wait loop
 * idles it to let the manager's listener callbacks (`onDownloadChanged`) execute.
 *
 * Single test on purpose: the helper's lateinit guard initializes the manager once per
 * process, so the S1 launch fires only on the FIRST `getDownloadManager` call. The
 * manager/cache are deliberately left unreleased in @After (the process-wide
 * `downloadCache` must never be left pointing at a released [SimpleCache]).
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyDownloadHelperInitS1OffMainTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    // Thread names observed on the spy cache's index reads — the S1 revalidation
    // thread must be among them (the validator is called from the revalidation coroutine).
    private val indexReadThreads = CopyOnWriteArrayList<String>()

    @Before
    fun initDependencies() {
        // MainApplication is final, so Dependencies.application is a mock wired to this
        // app's context (same pattern as MediaItemUtilsTest) — the init path reads
        // appContext() for the `download_prefs` SharedPreferences.
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns context
        Dependencies.init(mainApplication)
    }

    @After
    fun resetHelperState() {
        MyDownloadHelper.downloads.value = emptyMap()
        // P9 (review, 2026-10-23): the S1 flow sets pendingStaleStartupCount — reset it like the
        // sibling tests so the static doesn't leak into the next class of the fork.
        MyDownloadHelper.pendingStaleStartupCount.value = 0
    }

    private fun download(id: String): Download {
        val request = DownloadRequest.Builder(id, Uri.parse("https://music.example.com/stream/$id")).build()
        return Download(request, Download.STATE_COMPLETED, 0L, 0L, 0L, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE)
    }

    /**
     * Seeds the SAME index database the app init will open (base name →
     * [StandaloneDatabaseProvider] → `exoplayer_internal.db` in the context cache dir)
     * with one `STATE_COMPLETED` download whose span file has been wiped, and points
     * the helper at the (spy) cache.
     */
    private fun seedStaleCompletedDownload(id: String) {
        val cache = SimpleCache(
            tempFolder.root,
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(context)
        )
        cache.startReadWriteNonBlocking(id, 0L, 1000L)
        val file = cache.startFile(id, 0L, 1000L)
        file.writeBytes(ByteArray(1000))
        cache.commitFile(file, 1000L)
        // The OS/OEM wipe: the span file is gone, the index still reports it
        assertTrue(file.delete())

        // A second [DefaultDownloadIndex] on the same database file — its lazy,
        // thread-safe ensureInitialized makes the row visible to the app's own index
        // instance (the one the real DownloadManager builds during init).
        DefaultDownloadIndex(StandaloneDatabaseProvider(context)).putDownload(download(id))

        // Pre-assign the helper's cache so the init reuses it (getDownloadCache skips
        // initDownloadCache when already initialized) and the revalidation thread is
        // observable on the index read.
        MyDownloadHelper.downloadCache = spyOf(cache)
    }

    private fun spyOf(real: SimpleCache): Cache {
        return mockk<Cache>(relaxed = true) {
            every { getCachedSpans(any()) } answers {
                indexReadThreads.add(Thread.currentThread().name)
                real.getCachedSpans(firstArg())
            }
        }
    }

    @Test
    fun `the s1 call site runs at init on the data thread and removes the stale entry`() {
        seedStaleCompletedDownload("ABXtWqmArUU")

        // The real public entry point: drives ensureDownloadManagerInitialized — real
        // DownloadManager + DefaultDownloadIndex + listener wiring + the S1 launch.
        MyDownloadHelper.getDownloadManager(context)

        // Bounded wait: the S1 coroutine (DATA), the internal HandlerThread removal and
        // the onDownloadChanged listener (main looper) all run off the test thread.
        waitUntil(10_000) {
            indexReadThreads.isNotEmpty() && !MyDownloadHelper.isSongDownloaded("ABXtWqmArUU")
        }

        // (a) The S1 call-site ran at init (the validator read the index)...
        assertTrue(
            "S1 revalidation never ran — the call site in ensureDownloadManagerInitialized is missing",
            indexReadThreads.isNotEmpty()
        )
        // ...and on the NzikDispatchers.DATA pool (Dispatchers.IO — `DefaultDispatcher-worker-*`
        // threads), never on the main thread.
        assertTrue(
            "S1 revalidation did not stay on the NzikDispatchers.DATA pool: $indexReadThreads",
            indexReadThreads.all { it.startsWith("DefaultDispatcher-worker") }
        )
        // (b) The stale entry was removed: the badge predicate is honest again.
        assertFalse(
            "the stale entry survived the init re-validation",
            MyDownloadHelper.isSongDownloaded("ABXtWqmArUU")
        )
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            // The manager posts its listener callbacks on the main looper, which is
            // paused under Robolectric — idle it so onDownloadChanged/syncDownloads run.
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(50)
        }
    }
}
