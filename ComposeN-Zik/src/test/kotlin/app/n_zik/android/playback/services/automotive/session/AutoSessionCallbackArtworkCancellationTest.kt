package app.n_zik.android.playback.services.automotive.session

import android.app.Application
import android.graphics.Bitmap
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.persistentQueueKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.core.database.QueuedMediaItemTable
import kotlinx.coroutines.flow.flow
import app.n_zik.android.appContext
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.download.utils.MyDownloadHelper
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.request.SuccessResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Drives the real synchronous mapper through its public future cancellation boundary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutoSessionCallbackArtworkCancellationTest {
    private val callbacks = mutableListOf<AutoSessionCallback>()

    @After
    fun tearDown() {
        callbacks.forEach { it.release() }
        unmockkAll()
    }

    @Test
    fun `cancelling onGetItem future cancels real Coil request and allows retry`() {
        verifyCancellation(release = false)
    }

    @Test
    fun `releasing callback cancels real Coil request and next session can load`() {
        verifyCancellation(release = true)
    }

    @Test
    fun `cancelling playback resumption future cancels its suspended queue reader`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        mockkObject(Database)
        val queue = mockk<QueuedMediaItemTable>()
        every { Database.queueTable } returns queue
        val entered = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        every { queue.all(any()) } returns flow {
            entered.countDown()
            try { awaitCancellation() } finally { stopped.countDown() }
        }
        context.preferences.edit().putBoolean(persistentQueueKey, true).commit()
        val callback = AutoSessionCallback(context, Database, mockk<MyDownloadHelper>(relaxed = true))
        callbacks += callback
        try {
            val future = callback.onPlaybackResumption(mockk<MediaSession>(), mockk<MediaSession.ControllerInfo>())
            assertTrue(entered.await(15, TimeUnit.SECONDS))
            assertTrue(future.cancel(true))
            assertTrue("Future cancellation must cancel the queue reader", stopped.await(15, TimeUnit.SECONDS))
        } finally {
            callback.release()
            context.preferences.edit().remove(persistentQueueKey).commit()
        }
    }
    private fun verifyCancellation(release: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        val song = Song(
            id = "local:42", title = "Cancellable artwork", artistsText = "Artist",
            durationText = "3:00",
            thumbnailUrl = "https://img.example.com/cancel-${if (release) "release" else "future"}.jpg",
        )
        val songs = mockk<SongTable>()
        mockkObject(Database)
        every { Database.songTable } returns songs
        every { songs.findByIdDirect(song.id) } returns song
        val loader = mockk<ImageLoader>()
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.LOADER } returns loader
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val result = mockk<SuccessResult>()
        every { result.image } returns bitmap.asImage()
        every { result.dataSource } returns DataSource.MEMORY_CACHE
        val entered = CompletableFuture<Job>()
        val finallyReached = CountDownLatch(1)
        val requestCompleted = CountDownLatch(1)
        val calls = AtomicInteger()
        coEvery { loader.execute(any()) } coAnswers {
            if (calls.incrementAndGet() == 1) {
                val job = requireNotNull(currentCoroutineContext()[Job])
                job.invokeOnCompletion { requestCompleted.countDown() }
                entered.complete(job)
                try {
                    awaitCancellation()
                } finally {
                    finallyReached.countDown()
                }
            } else {
                result
            }
        }
        fun newCallback() = AutoSessionCallback(
            context, Database, mockk<MyDownloadHelper>(relaxed = true),
        ).also { callbacks += it }
        val callback = newCallback()
        val session = mockk<MediaLibrarySession>()
        val controller = mockk<MediaSession.ControllerInfo>()
        // A contextual ID uses the artwork-loading song overload for both local and remote art.
        val mediaId = "${AutoSessionConstants.ID_SONGS_ALL}/${song.id}"
        try {
            val future = callback.onGetItem(session, controller, mediaId)
            val requestJob = entered.get(15, TimeUnit.SECONDS)
            if (release) callback.release() else assertTrue(future.cancel(true))
            assertTrue("Coil request finally must run", finallyReached.await(15, TimeUnit.SECONDS))
            assertTrue("Coil request job must complete", requestCompleted.await(15, TimeUnit.SECONDS))
            assertTrue("Underlying Coil job must be cancelled", requestJob.isCancelled)
            assertThrows(CancellationException::class.java) { future.get(15, TimeUnit.SECONDS) }

            val nextCallback = if (release) newCallback() else callback
            val loaded = nextCallback.onGetItem(session, controller, mediaId).get(15, TimeUnit.SECONDS)
            assertEquals(SessionResult.RESULT_SUCCESS, loaded.resultCode)
            val item = requireNotNull(loaded.value)
            assertEquals(mediaId, item.mediaId)
            assertNotNull(item.mediaMetadata.artworkData)
            assertEquals("Cancelled load must not poison the artwork key", 2, calls.get())
        } finally {
            callbacks.forEach { it.release() }
            bitmap.recycle()
        }
    }
}
