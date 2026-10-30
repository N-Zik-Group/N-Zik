package app.n_zik.android.playback.services.automotive.models

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import app.it.fast4x.rimusic.utils.saveImageToInternalStorage
import app.n_zik.android.components.dialog.song.ChangeCoverDialog
import java.io.RandomAccessFile
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.appContext
import app.n_zik.android.playback.services.ArtworkContentProvider
import android.content.ContentResolver
import android.content.ContextWrapper
import app.n_zik.android.core.coil.ImageCacheFactory
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.request.SuccessResult
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.profilePreferences
import java.util.concurrent.Executors
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkBoundedPipelineTest {
    @After fun cleanup() = unmockkAll()

    private fun context() {
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns RuntimeEnvironment.getApplication()
    }

    private fun write(file: File, color: Int) {
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun `encoded unicode path decodes bounded artwork and warm hits do no decode`() {
        context()
        val file = File.createTempFile("cover espace é", ".png")
        try {
            write(file, Color.RED)
            val song = Song("local:123", "title", "artist", "1:20", file.toURI().toASCIIString())
            val before = ArtworkBitmapDecoder.decodeCount.get()
            val first = SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = true)
            val second = SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = true)
            val bytes = requireNotNull(first.mediaMetadata.artworkData)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertTrue(bitmap.width <= 512 && bitmap.height <= 512)
            assertTrue(Color.red(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) > 240)
            assertTrue(Color.blue(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) < 20)
            assertArrayEquals(bytes, second.mediaMetadata.artworkData)
            assertEquals(before + 1, ArtworkBitmapDecoder.decodeCount.get())
            bitmap.recycle()
        } finally { file.delete() }
    }

    @Test fun `both mapper overloads publish provider URI for private covers without decoding`() {
        context()
        val file = File.createTempFile("cover-uri", ".png")
        try {
            write(file, Color.RED)
            val before = ArtworkBitmapDecoder.decodeCount.get()
            for (id in listOf("local:126", "stream12346")) {
                val song = Song(id, "Title", "Artist", "1:20", file.toURI().toASCIIString())
                val queue = SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = false)
                val browse = SessionMediaItemMapper.mapSongToMediaItem(song, path = "album", loadArtwork = false)
                assertEquals(ArtworkContentProvider.AUTHORITY, queue.mediaMetadata.artworkUri?.authority)
                assertEquals("source", queue.mediaMetadata.artworkUri?.pathSegments?.first())
                assertEquals(queue.mediaMetadata.artworkUri, browse.mediaMetadata.artworkUri)
                assertNull(queue.mediaMetadata.artworkData)
                assertNull(browse.mediaMetadata.artworkData)
            }
            assertEquals(before, ArtworkBitmapDecoder.decodeCount.get())
        } finally { file.delete() }
    }

    @Test fun `blocked remote load does not block a warm unrelated local cover`() {
        context()
        val file = File.createTempFile("cover-warm-unrelated", ".png")
        val pool = Executors.newFixedThreadPool(2)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val source = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        try {
            write(file, Color.RED)
            val song = Song("local:981", "Title", "Artist", "1:00", file.toURI().toASCIIString())
            val warm = SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = true)
            val count = ArtworkBitmapDecoder.decodeCount.get()
            val loader = mockk<ImageLoader>()
            val result = mockk<SuccessResult>()
            every { result.image } returns source.asImage()
            every { result.dataSource } returns DataSource.MEMORY_CACHE
            mockkObject(ImageCacheFactory)
            every { ImageCacheFactory.LOADER } returns loader
            coEvery { loader.execute(any()) } answers {
                started.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                result
            }
            val remote = pool.submit {
                SessionMediaItemMapper.mapArtistToMediaItem("artist", "blocked", "Artist",
                    "https://img.example.com/blocked-${file.name}.jpg", loadArtwork = true)
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val local = pool.submit<ByteArray> {
                requireNotNull(SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = true).mediaMetadata.artworkData)
            }
            assertArrayEquals(warm.mediaMetadata.artworkData, local.get(2, TimeUnit.SECONDS))
            assertEquals(count, ArtworkBitmapDecoder.decodeCount.get())
            release.countDown()
            remote.get(5, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            pool.shutdownNow()
            source.recycle()
            file.delete()
        }
    }

    @Test fun `content URI stream applies EXIF rotation before bounded serialization`() {
        context()
        val app = RuntimeEnvironment.getApplication()
        val file = File.createTempFile("content-rotated", ".jpg")
        val source = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        // The red left half becomes the top half after a clockwise rotation.
        for (x in 0 until source.width) for (y in 0 until source.height)
            source.setPixel(x, y, if (x < 600) Color.RED else Color.BLUE)
        file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        source.recycle()
        try {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val uri = Uri.parse("content://test.art/rotated-${file.name}")
            val resolver = mockk<ContentResolver>()
            every { resolver.openInputStream(uri) } answers { file.inputStream() }
            val wrapped = object : ContextWrapper(app) {
                override fun getContentResolver(): ContentResolver = resolver
            }
            every { appContext() } returns wrapped
            val song = Song("local:982", "Title", "Artist", "1:00", uri.toString())
            val bytes = requireNotNull(SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = true).mediaMetadata.artworkData)
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            assertEquals(bitmap.width, bitmap.height)
            assertTrue(bitmap.height <= 512)
            val top = bitmap.getPixel(bitmap.width / 2, bitmap.height / 4)
            val bottom = bitmap.getPixel(bitmap.width / 2, bitmap.height * 3 / 4)
            assertTrue(Color.red(top) > 230 && Color.blue(top) < 25)
            assertTrue(Color.blue(bottom) > 230 && Color.red(bottom) < 25)
            bitmap.recycle()
        } finally { file.delete() }
    }

    @Test fun `same path same timestamp replacement invalidates cached bytes`() {
        context()
        val file = File.createTempFile("cover-revision", ".png")
        try {
            write(file, Color.RED)
            val song = Song("local:124", "title", "artist", "1:20", file.toURI().toASCIIString())
            val first = requireNotNull(SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = true).mediaMetadata.artworkData)
            val stamp = file.lastModified()
            write(file, Color.BLUE)
            file.setLastModified(stamp)
            ArtworkRevisions.invalidate()
            val second = requireNotNull(SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = true).mediaMetadata.artworkData)
            assertFalse(first.contentEquals(second))
            file.delete()
            val fallback = SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = true).mediaMetadata.artworkData
            assertFalse(second.contentEquals(fallback))
        } finally { file.delete() }
    }

    @Test fun `real cover save hook refreshes cached artwork with unchanged size and timestamp`() {
        context()
        val app = RuntimeEnvironment.getApplication()
        val file = File.createTempFile("cover-save-hook", ".png")
        val savedUri = Uri.parse(file.toURI().toASCIIString())
        try {
            write(file, Color.RED)
            RandomAccessFile(file, "rw").use { it.setLength(65536) }
            val stamp = file.lastModified()
            val song = Song("local:127", "Title", "Artist", "1:20", savedUri.toString())
            val before = requireNotNull(SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = true).mediaMetadata.artworkData)
            val count = ArtworkBitmapDecoder.decodeCount.get()
            mockkStatic("app.it.fast4x.rimusic.utils.FileUtilsKt")
            every { saveImageToInternalStorage(app, savedUri, file.parent.orEmpty(), file.name) } answers {
                assertFalse(file.exists())
                write(file, Color.BLUE)
                RandomAccessFile(file, "rw").use { it.setLength(65536) }
                assertTrue(file.setLastModified(stamp))
                savedUri
            }
            assertEquals(savedUri, ChangeCoverDialog.saveCoverArt(app, savedUri, file, file.parent.orEmpty(), file.name))
            assertEquals(65536L, file.length())
            assertEquals(stamp, file.lastModified())
            val after = requireNotNull(SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = true).mediaMetadata.artworkData)
            assertFalse(before.contentEquals(after))
            assertEquals(count + 1, ArtworkBitmapDecoder.decodeCount.get())
        } finally { file.delete() }
    }

    @Test fun `base preserves explicit modified metadata extras and playback identity without decoding`() {
        context()
        val song = Song("stream12345", MODIFIED_PREFIX + EXPLICIT_PREFIX + "Title", MODIFIED_PREFIX + "Artist",
            "1:23", MODIFIED_PREFIX + "https://img.example.com/cover.jpg")
        val before = ArtworkBitmapDecoder.decodeCount.get()
        val expected = song.asMediaItem
        val actual = SessionMediaItemMapper.baseMediaItem(song)
        assertEquals(expected.mediaId, actual.mediaId)
        assertEquals(expected.localConfiguration?.uri, actual.localConfiguration?.uri)
        assertEquals(expected.localConfiguration?.customCacheKey, actual.localConfiguration?.customCacheKey)
        assertEquals(expected.mediaMetadata.title, actual.mediaMetadata.title)
        assertEquals(expected.mediaMetadata.artist, actual.mediaMetadata.artist)
        assertEquals(expected.mediaMetadata.artworkUri?.toString(), actual.mediaMetadata.artworkUri?.getQueryParameter("u"))
        assertEquals(expected.mediaMetadata.isPlayable, actual.mediaMetadata.isPlayable)
        assertEquals(expected.mediaMetadata.isBrowsable, actual.mediaMetadata.isBrowsable)
        val expectedExtras = requireNotNull(expected.mediaMetadata.extras)
        val actualExtras = requireNotNull(actual.mediaMetadata.extras)
        assertEquals(song.thumbnailUrl, actualExtras.getString(SessionMediaItemMapper.ORIGINAL_ARTWORK_SOURCE_EXTRA))
        assertEquals(expectedExtras.keySet(), actualExtras.keySet() - SessionMediaItemMapper.ORIGINAL_ARTWORK_SOURCE_EXTRA)
        expectedExtras.keySet().forEach { assertEquals(expectedExtras.get(it), actualExtras.get(it)) }
        assertEquals(before, ArtworkBitmapDecoder.decodeCount.get())
        assertNull(actual.mediaMetadata.artworkData)
    }

    @Test fun `profile identity separates warm artwork and concurrent callers decode once`() {
        context()
        val app = RuntimeEnvironment.getApplication()
        val file = File.createTempFile("cover-concurrent", ".png")
        val pool = Executors.newFixedThreadPool(4)
        try {
            write(file, Color.RED)
            val song = Song("local:125", "title", "artist", "1:20", file.toURI().toASCIIString())
            app.profilePreferences.edit().putString(activeProfileKey, "profileA").commit()
            val before = ArtworkBitmapDecoder.decodeCount.get()
            val futures = (1..8).map { pool.submit<ByteArray> {
                requireNotNull(SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = true).mediaMetadata.artworkData)
            } }
            val first = futures.first().get()
            futures.forEach { assertArrayEquals(first, it.get()) }
            assertEquals(before + 1, ArtworkBitmapDecoder.decodeCount.get())
            app.profilePreferences.edit().putString(activeProfileKey, "profileB").commit()
            SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = true)
            assertEquals(before + 2, ArtworkBitmapDecoder.decodeCount.get())
        } finally {
            pool.shutdownNow()
            app.profilePreferences.edit().remove(activeProfileKey).commit()
            file.delete()
        }
    }

    @Test fun `non square streamed image is strictly bounded`() {
        val bitmap = Bitmap.createBitmap(2000, 1100, Bitmap.Config.ARGB_8888)
        val data = ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }
        bitmap.recycle()
        val decoded = requireNotNull(ArtworkBitmapDecoder.decode { data.inputStream() })
        val bytes = ArtworkBitmapDecoder.jpeg(decoded)
        val result = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertTrue(result.width <= 512 && result.height <= 512)
        result.recycle()
    }

    @Test fun `EXIF orientation is applied before bounded square crop`() {
        val file = File.createTempFile("cover-rotation", ".jpg")
        val source = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        source.recycle()
        try {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val rotated = requireNotNull(ArtworkBitmapDecoder.file(file))
            assertTrue(rotated.height > rotated.width)
            val square = ArtworkBitmapDecoder.finish(rotated, square = true)
            assertEquals(square.width, square.height)
            assertTrue(square.width <= 512)
            square.recycle()
        } finally { file.delete() }
    }

    @Test fun `cache enforces byte budget as well as entries`() {
        val cache = ArtworkBytesCache(maxEntries = 10, maxBytes = 5)
        cache.put("a", ByteArray(3))
        cache.put("b", ByteArray(3))
        assertNull(cache.get("a"))
        assertNotNull(cache.get("b"))
        cache.put("large", ByteArray(6))
        assertNull(cache.get("large"))
    }
}
