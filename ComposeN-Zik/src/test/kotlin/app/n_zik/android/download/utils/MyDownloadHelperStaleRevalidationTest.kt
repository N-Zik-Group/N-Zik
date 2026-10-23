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
import app.n_zik.android.core.maintenance.MaintenanceStateStore
import app.n_zik.android.utils.DataStoreUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

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
        // The S1 run is persisted by the re-validation — clear the key so the static
        // never leaks into the next test class of the same JVM fork
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .remove(MaintenanceStateStore.KEY_DOWNLOAD_REVALIDATION)
            .remove(MaintenanceStateStore.KEY_DOWNLOAD_SELF_HEAL)
            .apply()
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

    private fun download(id: String, state: Int, data: String = ""): Download {
        val builder = DownloadRequest.Builder(id, Uri.parse("https://music.example.com/stream/$id"))
        if (data.isNotEmpty()) builder.setData(data.encodeToByteArray())
        return Download(builder.build(), state, 0L, 0L, 0L, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE)
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

        MyDownloadHelper.revalidateCompletedDownloads(context, manager)

        verify(exactly = 0) { manager.removeDownload(any()) }
        assertEquals(0, MyDownloadHelper.pendingStaleStartupCount.value)
        // The map is untouched on a clean run
        assertEquals(1, MyDownloadHelper.downloads.value.size)
        // S1 saves on EVERY run: the row answers "Nothing to clean" with its date
        val state = MaintenanceStateStore.readDownloadRevalidation(context)
        assertNotNull("the clean run must persist its state", state)
        assertEquals(0, state!!.removed)
        assertTrue(state.removedTracks.isEmpty())
        // The S2 startup clean state is persisted too (empty mediaId = "nothing to heal")
        val selfHeal = MaintenanceStateStore.readDownloadSelfHeal(context)
        assertNotNull("the clean run must persist the self-heal state", selfHeal)
        assertTrue(selfHeal!!.mediaId.isEmpty())
    }

    @Test
    fun `a completed entry whose files were wiped is removed`() {
        val file = setupCacheWithSpan("ABXtWqmArUU")
        // The OS/OEM wipe: the span file is gone, the index still reports it
        assertTrue(file.delete())
        MyDownloadHelper.downloadCache = cache
        MyDownloadHelper.downloads.value = mapOf(
            "ABXtWqmArUU" to download("ABXtWqmArUU", Download.STATE_COMPLETED, "Artist X - Song A"),
        )
        val manager = mockk<DownloadManager>(relaxed = true)

        MyDownloadHelper.revalidateCompletedDownloads(context, manager)

        verify(exactly = 1) { manager.removeDownload("ABXtWqmArUU") }
        assertEquals(1, MyDownloadHelper.pendingStaleStartupCount.value)
        // The stale entry is purged from the live map immediately: the Downloads row
        // is clean from startup, without waiting for the async service round-trip
        assertFalse(MyDownloadHelper.downloads.value.containsKey("ABXtWqmArUU"))
        // The run is persisted with the removed track (title from request.data)
        val state = MaintenanceStateStore.readDownloadRevalidation(context)
        assertNotNull("the removing run must persist its state", state)
        assertEquals(1, state!!.removed)
        val track = state.removedTracks.single()
        assertEquals("ABXtWqmArUU", track.mediaId)
        assertEquals("Artist X - Song A", track.title)
        // The S2 startup clean state is persisted on a removing run too
        val selfHeal = MaintenanceStateStore.readDownloadSelfHeal(context)
        assertNotNull("the removing run must persist the self-heal state", selfHeal)
        assertTrue(selfHeal!!.mediaId.isEmpty())
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

        MyDownloadHelper.revalidateCompletedDownloads(context, manager)

        verify(exactly = 1) { manager.removeDownload("ABXtWqmArUU") }
        verify(exactly = 0) { manager.removeDownload("na47wMFfQCo") }
        verify(exactly = 0) { manager.removeDownload("inProgress1") }
        assertEquals(1, MyDownloadHelper.pendingStaleStartupCount.value)
        // Only the stale entry is purged from the live map (intact + non-terminal stay)
        assertEquals(setOf("na47wMFfQCo", "inProgress1"), MyDownloadHelper.downloads.value.keys)
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

        MyDownloadHelper.revalidateCompletedDownloads(context, manager)

        verify(exactly = 0) { manager.removeDownload(any()) }
        assertEquals(0, MyDownloadHelper.pendingStaleStartupCount.value)
        // The empty run still persists its state (removed = 0)
        val state = MaintenanceStateStore.readDownloadRevalidation(context)
        assertNotNull("the empty run must persist its state", state)
        assertEquals(0, state!!.removed)
    }

    @Test
    fun `a failed state write never breaks the cleanup`() {
        // Matrix row "write failure": the SharedPreferences write throws — the stale
        // entry must still be removed and the live map still purged
        val file = setupCacheWithSpan("ABXtWqmArUU")
        assertTrue(file.delete())
        MyDownloadHelper.downloadCache = cache
        MyDownloadHelper.downloads.value = mapOf(
            "ABXtWqmArUU" to download("ABXtWqmArUU", Download.STATE_COMPLETED, "Artist X - Song A"),
        )
        val manager = mockk<DownloadManager>(relaxed = true)
        mockkObject(DataStoreUtils)
        try {
            every { DataStoreUtils.saveString(any(), any(), any()) } throws RuntimeException("prefs write down")

            MyDownloadHelper.revalidateCompletedDownloads(context, manager)
        } finally {
            unmockkObject(DataStoreUtils)
        }

        verify(exactly = 1) { manager.removeDownload("ABXtWqmArUU") }
        assertFalse(MyDownloadHelper.downloads.value.containsKey("ABXtWqmArUU"))
        assertEquals(1, MyDownloadHelper.pendingStaleStartupCount.value)
        assertNull(MaintenanceStateStore.readDownloadRevalidation(context))
        // Both writes are guarded: a failed prefs write leaves neither state behind
        assertNull(MaintenanceStateStore.readDownloadSelfHeal(context))
    }
}
