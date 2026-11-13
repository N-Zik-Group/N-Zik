package app.n_zik.android.components.tab

import android.content.Context
import androidx.media3.datasource.cache.Cache
import app.n_zik.android.models.Song
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Issue #606 H4 — [deleteDownloadsOffMain] is the SmartTrash "delete downloads" batch extracted
 * from `SmartTrash.deleteDownloads`, which used to run a per-song `DocumentFile` I/O +
 * `removeResource` + `asyncTransaction` + `removeDownload` loop on the Main thread. It must
 * evict both the streaming and the download cache, remove the download, and run off the caller's
 * (UI-simulating) thread.
 *
 * Robolectric + JUnit 4: `Song.asMediaItem` builds a `Bundle`. The songs carry a plain HTTP
 * thumbnail URL so `asMediaItem` resolves its artwork from the URL (never the
 * `appContext()`/drawable fallback). [Database] is mocked (relaxed) to no-op the internal
 * `asyncTransaction`; [MyDownloadHelper] is object-mocked to verify the `removeDownload` fan-out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SmartTrashDeleteDownloadsTest {

    private val callerThread = Thread.currentThread()

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun songs(count: Int) = (1..count).map {
        Song(id = "id_$it", title = "Song $it", durationText = null, thumbnailUrl = "https://img.example.com/a.jpg")
    }

    @Test
    fun `deleteDownloadsOffMain evicts both caches and removes the download off the caller thread`() = runBlocking {
        val cache = mockk<Cache>()
        val downloadCache = mockk<Cache>()
        every { cache.removeResource(any()) } just Runs
        every { downloadCache.removeResource(any()) } just Runs
        val context = mockk<Context>(relaxed = true)

        val threads = CopyOnWriteArrayList<Thread>()
        mockkObject(MyDownloadHelper)
        every { MyDownloadHelper.removeDownload(any(), any()) } answers { threads += Thread.currentThread() }
        mockkObject(Database)
        every { Database.asyncTransaction(any(), any()) } just Runs

        withContext(NzikDispatchers.DATA) {
            deleteDownloadsOffMain(songs(3), cache, downloadCache, context)
        }

        assertEquals(3, threads.size)
        threads.forEach { assertNotEquals(callerThread, it) }
        verify(exactly = 3) { cache.removeResource(any()) }
        verify(exactly = 3) { downloadCache.removeResource(any()) }
        verify(exactly = 3) { MyDownloadHelper.removeDownload(any(), any()) }
    }

    @Test
    fun `an empty selection is a no-op`() = runBlocking {
        val cache = mockk<Cache>()
        val downloadCache = mockk<Cache>()
        val context = mockk<Context>(relaxed = true)
        mockkObject(MyDownloadHelper)
        mockkObject(Database)
        every { Database.asyncTransaction(any(), any()) } just Runs

        withContext(NzikDispatchers.DATA) {
            deleteDownloadsOffMain(emptyList(), cache, downloadCache, context)
        }

        verify(exactly = 0) { cache.removeResource(any()) }
        verify(exactly = 0) { downloadCache.removeResource(any()) }
        verify(exactly = 0) { MyDownloadHelper.removeDownload(any(), any()) }
    }
}
