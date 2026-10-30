package app.n_zik.android.playback.services.automotive.models

import android.app.Application
import android.graphics.Bitmap
import androidx.media3.common.MediaItem
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.appContext
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.utils.coroutines.NzikDispatchers
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkSuspendPipelineStressTest {
    @After fun cleanup() = unmockkAll()

    @Test
    fun `all automobile mappers and duplicate waiters progress on the same two DATA workers`() {
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns RuntimeEnvironment.getApplication()
        val executor = Executors.newFixedThreadPool(2)
        val dispatcher = executor.asCoroutineDispatcher()
        mockkObject(NzikDispatchers)
        every { NzikDispatchers.DATA } returns dispatcher
        val loader = mockk<ImageLoader>()
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.LOADER } returns loader
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val result = mockk<SuccessResult>()
        every { result.image } returns bitmap.asImage()
        every { result.dataSource } returns DataSource.MEMORY_CACHE
        val entered = AtomicInteger()
        val allSourcesEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { loader.execute(any()) } coAnswers {
            if (entered.incrementAndGet() == 12) allSourcesEntered.complete(Unit)
            release.await()
            result
        }
        try {
            // The outer test thread owns the deadline; DATA has exactly two physical workers.
            runBlocking {
                withTimeout(10_000) {
                    val items = (0 until 24).map { index ->
                        async(dispatcher) {
                            // Two callers per source exercise both cache owners and suspended waiters.
                            val source = index % 12
                            val url = "https://img.example.com/suspend-stress-$source.jpg"
                            val song = Song("local:${9000 + source}", "Title", "Artist", "1:00", url)
                            when (source % 4) {
                                0 -> CancellableAutoMediaItemMapper.mapArtistToMediaItem(
                                    "artists", "$source", "Artist", url, loadArtwork = true,
                                )
                                1 -> CancellableAutoMediaItemMapper.mapAlbumToMediaItem(
                                    "albums", "$source", "Album", "Artist", url, loadArtwork = true,
                                )
                                2 -> CancellableAutoMediaItemMapper.mapSongToMediaItem(
                                    song, "songs", loadArtwork = true,
                                )
                                else -> CancellableAutoMediaItemMapper.mapSongToMediaItem(
                                    song, isFromPersistentQueue = true, loadArtwork = true,
                                )
                            }
                        }
                    }
                    // This can complete only if suspended artwork frees both workers for other requests.
                    allSourcesEntered.await()
                    assertEquals(12, entered.get())
                    release.complete(Unit)
                    val loaded: List<MediaItem> = items.awaitAll()
                    loaded.forEach { assertNotNull(it.mediaMetadata.artworkData) }
                    (0 until 12).forEach {
                        assertArrayEquals(loaded[it].mediaMetadata.artworkData, loaded[it + 12].mediaMetadata.artworkData)
                    }
                    assertEquals("Duplicate requests must share their source load", 12, entered.get())
                }
            }
        } finally {
            release.complete(Unit)
            dispatcher.close()
            bitmap.recycle()
        }
    }
}
