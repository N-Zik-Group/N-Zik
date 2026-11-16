package app.n_zik.android.components.ui.screens.playlist

import android.content.Context
import androidx.media3.datasource.cache.Cache
import app.n_zik.android.ui.screens.playlist.processPlaylistBatch
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
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
 * Playlist cache eviction and download commands must run within the same queued batch.
 * The binder may be absent even when download commands still need to be sent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaylistDownloadBatchOffMainTest {

    private val callerThread = Thread.currentThread()

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun songItem(id: String, title: String) = Innertube.SongItem(
        info = Innertube.Info(
            name = title,
            endpoint = NavigationEndpoint.Endpoint.Watch(videoId = id)
        ),
        authors = null,
        album = null,
        durationText = "3:00",
        thumbnail = null,
        explicit = false
    )

    private fun songs(count: Int) = (1..count).map { songItem("id_$it", "Song $it") }

    private fun binderWithCache(cache: Cache): PlayerServiceModern.Binder {
        val binder = mockk<PlayerServiceModern.Binder>()
        every { binder.cache } returns cache
        return binder
    }

    private fun stubWriteBatch(onEnter: () -> Unit = {}, onExit: () -> Unit = {}) {
        mockkObject(Database)
        coEvery { Database.awaitWriteBatch<Unit>(1, any()) } coAnswers {
            onEnter()
            try {
                secondArg<suspend Database.() -> Unit>().invoke(Database)
            } finally {
                onExit()
            }
        }
    }

    @Test
    fun `removeDownload runs once per song off the caller thread`() = runBlocking {
        val steps = CopyOnWriteArrayList<String>()
        val cache = mockk<Cache>()
        every { cache.removeResource(any()) } answers { steps += "cache" }
        val binder = binderWithCache(cache)
        val context = mockk<Context>(relaxed = true)

        val threads = CopyOnWriteArrayList<Thread>()
        mockkObject(MyDownloadHelper)
        every { MyDownloadHelper.removeDownload(any(), any()) } answers {
            threads += Thread.currentThread()
            steps += "command"
        }
        stubWriteBatch(onEnter = { steps += "enter" }, onExit = { steps += "exit" })

        withContext(NzikDispatchers.DATA) {
            processPlaylistBatch(songs(3), binder, context, downloadState = true)
        }

        assertEquals(listOf("enter", "cache", "command", "cache", "command", "cache", "command", "exit"), steps)
        assertEquals(3, threads.size)
        threads.forEach { assertNotEquals(callerThread, it) }
        verify(exactly = 3) { MyDownloadHelper.removeDownload(any(), any()) }
        verify(exactly = 3) { cache.removeResource(any()) }
    }

    @Test
    fun `addDownload runs once per song when the network is available`() = runBlocking {
        val cache = mockk<Cache>()
        every { cache.removeResource(any()) } just Runs
        val binder = binderWithCache(cache)
        val context = mockk<Context>(relaxed = true)

        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkAvailable(any()) } returns true

        val threads = CopyOnWriteArrayList<Thread>()
        mockkObject(MyDownloadHelper)
        every { MyDownloadHelper.addDownload(any(), any()) } answers { threads += Thread.currentThread() }
        stubWriteBatch()

        withContext(NzikDispatchers.DATA) {
            processPlaylistBatch(songs(2), binder, context, downloadState = false)
        }

        assertEquals(2, threads.size)
        threads.forEach { assertNotEquals(callerThread, it) }
        verify(exactly = 2) { MyDownloadHelper.addDownload(any(), any()) }
    }

    @Test
    fun `null binder is a safe no-op for cache eviction but still manages the download`() = runBlocking {
        val context = mockk<Context>(relaxed = true)

        val threads = CopyOnWriteArrayList<Thread>()
        mockkObject(MyDownloadHelper)
        every { MyDownloadHelper.removeDownload(any(), any()) } answers { threads += Thread.currentThread() }
        stubWriteBatch()

        withContext(NzikDispatchers.DATA) {
            processPlaylistBatch(songs(2), binder = null, context, downloadState = true)
        }

        assertEquals(2, threads.size)
        threads.forEach { assertNotEquals(callerThread, it) }
        verify(exactly = 2) { MyDownloadHelper.removeDownload(any(), any()) }
    }

    @Test
    fun `an empty batch is a no-op`() = runBlocking {
        val cache = mockk<Cache>()
        val binder = binderWithCache(cache)
        val context = mockk<Context>(relaxed = true)
        mockkObject(MyDownloadHelper)
        stubWriteBatch()

        withContext(NzikDispatchers.DATA) {
            processPlaylistBatch(emptyList(), binder, context, downloadState = true)
        }

        verify(exactly = 0) { MyDownloadHelper.removeDownload(any(), any()) }
        verify(exactly = 0) { cache.removeResource(any()) }
        coVerify(exactly = 0) { Database.awaitWriteBatch<Unit>(1, any()) }
    }
}
