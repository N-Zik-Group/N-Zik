package app.n_zik.android.components.dialog.media

import androidx.media3.datasource.cache.Cache
import app.n_zik.android.models.Song
import app.n_zik.android.core.database.Database
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Issue #606 H5 — [runDownloadBatch] is the download-dialog cache-eviction + per-song
 * `onAction` loop extracted from `MediaDownloadDialog.onConfirm`, which used to run
 * `removeResource` + `asyncTransaction` + `onAction` per song on the Main thread. It must evict
 * the cache once per song, run `onAction` per song, and run off the caller's (UI-simulating)
 * thread; a null cache must still run `onAction` (it only skips the eviction).
 *
 * Plain JUnit 5 — [runDownloadBatch] only reads `Song.id` (no `asMediaItem`, no Android
 * framework dependency). [Database] is mocked (relaxed) to no-op the internal `asyncTransaction`.
 */
class MediaDownloadBatchOffMainTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun songs(count: Int) = (1..count).map {
        Song(id = "id_$it", title = "Song $it", durationText = null, thumbnailUrl = null)
    }

    @Test
    fun `runDownloadBatch evicts the cache and runs onAction per song off the caller thread`() = runBlocking {
        val callerThread = Thread.currentThread()
        val cache = mockk<Cache>()
        every { cache.removeResource(any()) } just Runs
        mockkObject(Database)
        every { Database.asyncTransaction(any(), any()) } just Runs

        val actions = CopyOnWriteArrayList<Song>()
        val actionThreads = CopyOnWriteArrayList<Thread>()
        val onAction: (Song) -> Unit = { song ->
            actions += song
            actionThreads += Thread.currentThread()
        }

        withContext(NzikDispatchers.DATA) {
            runDownloadBatch(songs(3), cache, onAction)
        }

        assertEquals(3, actions.size)
        assertEquals(3, actionThreads.size)
        actionThreads.forEach { assertNotEquals(callerThread, it) }
        verify(exactly = 3) { cache.removeResource(any()) }
    }

    @Test
    fun `runDownloadBatch still runs onAction per song when the cache is null`() = runBlocking {
        mockkObject(Database)
        every { Database.asyncTransaction(any(), any()) } just Runs

        val actions = CopyOnWriteArrayList<Song>()
        withContext(NzikDispatchers.DATA) {
            runDownloadBatch(songs(2), cache = null) { actions += it }
        }

        assertEquals(2, actions.size, "onAction must still run for every song even without a cache")
    }

    @Test
    fun `an empty batch is a no-op`() = runBlocking {
        val cache = mockk<Cache>()
        every { cache.removeResource(any()) } just Runs
        mockkObject(Database)
        every { Database.asyncTransaction(any(), any()) } just Runs

        val actions = CopyOnWriteArrayList<Song>()
        withContext(NzikDispatchers.DATA) {
            runDownloadBatch(emptyList(), cache) { actions += it }
        }

        assertEquals(0, actions.size)
        verify(exactly = 0) { cache.removeResource(any()) }
    }
}
