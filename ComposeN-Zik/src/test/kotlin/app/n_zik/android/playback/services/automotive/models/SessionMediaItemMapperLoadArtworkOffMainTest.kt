package app.n_zik.android.playback.services.automotive.models

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.appContext
import app.n_zik.android.core.coil.ImageCacheFactory
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
import org.junit.After
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList

// Compatibility mapping remains synchronous; remote decode runs on DATA and publishes bounded bytes.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionMediaItemMapperLoadArtworkOffMainTest {
    @After fun tearDown() = unmockkAll()

    private fun context() {
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns ApplicationProvider.getApplicationContext<Application>()
    }

    @Test fun `mapArtistToMediaItem runs bounded Coil decode off the caller thread`() {
        context()
        val callerThread = Thread.currentThread()
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        val loader = mockk<ImageLoader>()
        val result = mockk<SuccessResult>()
        every { result.image } returns bitmap.asImage()
        every { result.dataSource } returns DataSource.MEMORY_CACHE
        val threads = CopyOnWriteArrayList<Thread>()
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.LOADER } returns loader
        coEvery { loader.execute(any()) } answers {
            threads += Thread.currentThread()
            result
        }
        try {
            val item = SessionMediaItemMapper.mapArtistToMediaItem(
                parentId = "artist", id = "aBcDeF12345", name = "Some Artist",
                thumbnailUrl = "https://img.example.com/artist.jpg", loadArtwork = true
            )
            assertEquals(1, threads.size)
            assertNotEquals(callerThread, threads.single())
            val data = requireNotNull(item.mediaMetadata.artworkData)
            val embedded = requireNotNull(BitmapFactory.decodeByteArray(data, 0, data.size))
            assertTrue(embedded.width <= 512 && embedded.height <= 512)
            embedded.recycle()
        } finally { bitmap.recycle() }
    }

    @Test fun `remote artwork cancellation propagates instead of becoming missing artwork`() {
        context()
        val loader = mockk<ImageLoader>()
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.LOADER } returns loader
        coEvery { loader.execute(any()) } throws CancellationException("cancelled artwork")
        assertThrows(CancellationException::class.java) {
            SessionMediaItemMapper.mapArtistToMediaItem(
                parentId = "artist", id = "cancel12345", name = "Cancelled",
                thumbnailUrl = "https://img.example.com/cancelled.jpg", loadArtwork = true
            )
        }
    }

    @Test fun `local queue artwork is embedded at most 512 pixels`() {
        context()
        val source = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        val temp = File.createTempFile("nzik-artwork-test", ".png")
        try {
            FileOutputStream(temp).use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val song = Song("local:42", "Local Track", "Some Artist", "3:00", temp.toURI().toASCIIString())
            val item = SessionMediaItemMapper.mapSongToMediaItem(song, isFromPersistentQueue = true, loadArtwork = true)
            val data = requireNotNull(item.mediaMetadata.artworkData)
            val embedded = requireNotNull(BitmapFactory.decodeByteArray(data, 0, data.size))
            assertTrue(embedded.width <= 512 && embedded.height <= 512)
            embedded.recycle()
        } finally {
            source.recycle()
            temp.delete()
        }
    }

    @Test fun `local queue without artwork embeds a bounded placeholder`() {
        context()
        val song = Song("local:43", "No Art", durationText = "3:00", thumbnailUrl = null)
        val item = SessionMediaItemMapper.mapSongToMediaItem(song, isFromPersistentQueue = true, loadArtwork = true)
        val data = requireNotNull(item.mediaMetadata.artworkData)
        val placeholder = requireNotNull(BitmapFactory.decodeByteArray(data, 0, data.size))
        assertTrue(placeholder.width <= 512 && placeholder.height <= 512)
        placeholder.recycle()
    }
}
