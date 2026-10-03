package app.n_zik.android.playback.services.automotive.models

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.appContext
import app.n_zik.android.core.coil.ImageCacheFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Issue #606 — [SessionMediaItemMapper]'s private `loadArtworkBytes` is called from the 5 public
 * non-suspend Android Auto mappers, so it cannot be made suspend. Its Coil I/O used to run on the
 * caller thread through a bare `runBlocking {}`; it now dispatches to `NzikDispatchers.DATA`.
 * [ImageCacheFactory.loadBitmap] must therefore run on a thread different from the caller's,
 * while the mapper still sets the artwork bytes on the media item.
 *
 * Robolectric + JUnit 4: `toUri()`/`MediaItem` touch framework classes that plain JVM stubs reject.
 * NATIVE graphics mode: the local-artwork tests decode real files through the native Skia
 * bitmap pipeline (shadow bitmaps cannot produce decodable bytes).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionMediaItemMapperLoadArtworkOffMainTest {

    private val callerThread = Thread.currentThread()

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `mapArtistToMediaItem loadArtwork runs loadBitmap off the caller thread`() {
        val bitmap = mockk<Bitmap>(relaxed = true)
        val threads = CopyOnWriteArrayList<Thread>()
        mockkObject(ImageCacheFactory)
        coEvery { ImageCacheFactory.loadBitmap(any(), any()) } answers {
            threads += Thread.currentThread()
            bitmap
        }

        val item = SessionMediaItemMapper.mapArtistToMediaItem(
            parentId = "artist",
            id = "aBcDeF12345",
            name = "Some Artist",
            thumbnailUrl = "https://img.example.com/artist.jpg",
            loadArtwork = true,
        )

        assertEquals(1, threads.size)
        assertNotEquals(callerThread, threads.single())
        assertNotNull(item.mediaMetadata.artworkData)
    }

    /**
     * Android Auto queue regression: a local song (MediaStore file:// cover)
     * must have its artwork embedded as bytes — the head unit cannot resolve
     * phone-side URIs — and the embedded cover must be downscaled toward the
     * 512px target instead of full resolution.
     */
    @Test
    fun `mapSongToMediaItem queue local song embeds downscaled artwork bytes`() {
        // Render a source image larger than the 512px embed target so the
        // downsample path is exercised end to end.
        val source = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        val temp = File.createTempFile("nzik-artwork-test", ".png")
        temp.deleteOnExit()
        FileOutputStream(temp).use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val song = Song(
            id = "local:42",
            title = "Local Track",
            artistsText = "Some Artist",
            durationText = "3:00",
            thumbnailUrl = temp.toURI().toString()
        )

        val item = SessionMediaItemMapper.mapSongToMediaItem(song, isFromPersistentQueue = true, loadArtwork = true)

        val data = item.mediaMetadata.artworkData
        assertNotNull("local song artwork must be embedded as bytes", data)
        val embedded = BitmapFactory.decodeByteArray(data!!, 0, data.size)
        assertNotNull(embedded)
        // 1200px source sampled down toward the 512px target; power-of-two
        // tolerance plus the square center crop keeps both sides well below 640.
        // (A full-resolution embed means the file decode failed and the
        // 1200px fallback placeholder was used instead.)
        assertTrue(
            "embedded artwork is ${embedded!!.width}x${embedded.height} — expected the downscaled cover, not the full-res fallback",
            embedded.width <= 640
        )
        assertTrue(embedded.height <= 640)
    }

    /**
     * Art-less local songs must embed the serialized placeholder: an
     * android.resource:// URI is unresolvable from the head unit, so the
     * fallback is embedded as bytes (setSerializedFallback), not left as a URI.
     */
    @Test
    fun `mapSongToMediaItem queue local song without art embeds the serialized placeholder`() {
        // appContext() is only consulted for the serialized fallback — stub it with
        // the real Robolectric application so R.drawable.ic_launcher_box resolves.
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns ApplicationProvider.getApplicationContext<Application>()

        val song = Song(id = "local:43", title = "No Art", durationText = "3:00", thumbnailUrl = null)

        val item = SessionMediaItemMapper.mapSongToMediaItem(song, isFromPersistentQueue = true, loadArtwork = true)

        val data = item.mediaMetadata.artworkData
        assertNotNull(data)
        val placeholder = BitmapFactory.decodeByteArray(data!!, 0, data.size)
        assertNotNull(placeholder)
    }
}
