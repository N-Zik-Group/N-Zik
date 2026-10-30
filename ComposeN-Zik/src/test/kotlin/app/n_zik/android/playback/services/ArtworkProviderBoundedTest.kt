package app.n_zik.android.playback.services

import android.app.Application
import android.content.ContentResolver
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.appContext
import app.n_zik.android.core.profiles.coversDir
import app.n_zik.android.playback.services.automotive.models.ArtworkBitmapDecoder
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import org.junit.After
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkProviderBoundedTest {
    @After fun cleanup() = unmockkAll()

    @Test fun `deleted registered source serves the same bounded seekable JPEG as an unknown token`() {
        assertUnavailableSourcePlaceholder { file -> assertTrue(file.delete()) }
    }

    @Test fun `registered source IO failure serves placeholder`() {
        assertUnavailableSourcePlaceholder { file ->
            mockkObject(ArtworkBitmapDecoder)
            every { ArtworkBitmapDecoder.file(file) } throws IOException("source read failed")
        }
    }

    @Test fun `registered source permission failure serves placeholder`() {
        assertUnavailableSourcePlaceholder { file ->
            mockkObject(ArtworkBitmapDecoder)
            every { ArtworkBitmapDecoder.file(file) } throws SecurityException("source permission revoked")
        }
    }

    private fun assertUnavailableSourcePlaceholder(makeUnavailable: (File) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        val previousProfile = app.profilePreferences.getString(activeProfileKey, null)
        val file = File.createTempFile("provider-unavailable", ".png", app.cacheDir).canonicalFile
        val source = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(Color.RED)
            file.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { source.recycle() }
        try {
            assertTrue(app.profilePreferences.edit().putString(activeProfileKey, "unavailable-source").commit())
            val provider = spyk(ArtworkContentProvider().also { it.attachInfo(app, null) })
            every { provider.callingPackage } returns app.packageName
            val uri = ArtworkContentProvider.uriForSource(app, "unavailable-generic", file)
            assertEquals("source", uri.pathSegments.first())
            assertRed(requireNotNull(provider.openFile(uri, "r")))

            fun readSeekableJpeg(request: Uri): ByteArray {
                val descriptor = requireNotNull(provider.openFile(request, "r"))
                assertTrue("placeholder descriptor must describe a regular seekable file", descriptor.statSize > 0)
                return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                    input.channel.position(1L)
                    assertEquals(1L, input.channel.position())
                    input.channel.position(0L)
                    input.readBytes()
                }
            }
            val placeholder = readSeekableJpeg(uri.buildUpon().path("source/unknown-token").build())
            makeUnavailable(file)
            val actual = readSeekableJpeg(uri)
            assertArrayEquals("unavailable source must use the normal placeholder", placeholder, actual)
            assertEquals(0xff, actual[0].toInt() and 0xff)
            assertEquals(0xd8, actual[1].toInt() and 0xff)
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(actual, 0, actual.size))
            try {
                assertEquals(bitmap.width, bitmap.height)
                assertTrue(bitmap.width in 1..ArtworkBitmapDecoder.MAX_SIDE)
            } finally { bitmap.recycle() }
        } finally {
            file.delete()
            app.profilePreferences.edit().putString(activeProfileKey, previousProfile).commit()
        }
    }

    @Test fun `generic source admitted in A is discarded when profile changes during decoding`() {
        assertProfileChangeDuringDecode(customCover = false)
    }

    @Test fun `canonical custom cover admitted in A is discarded when profile changes during decoding`() {
        assertProfileChangeDuringDecode(customCover = true)
    }

    private fun assertProfileChangeDuringDecode(customCover: Boolean) {
        val app = RuntimeEnvironment.getApplication()
        val previousProfile = app.profilePreferences.getString(activeProfileKey, null)
        val profileA = "provider-decode-a"
        val profileB = "provider-decode-b"
        val songId = "profile-decode-9989"
        fun image(file: File, color: Int): File {
            file.parentFile?.mkdirs()
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(color)
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
            return file.canonicalFile
        }
        val fileA = image(if (customCover) File(coversDir(app, profileA), "cover_$songId.jpg")
            else File.createTempFile("provider-profile-a", ".png", app.cacheDir), Color.RED)
        val fileB = image(if (customCover) File(coversDir(app, profileB), "cover_$songId.jpg")
            else File.createTempFile("provider-profile-b", ".png", app.cacheDir), Color.BLUE)
        val decoderEntered = CountDownLatch(1)
        val releaseDecode = CountDownLatch(1)
        val decodedA = AtomicReference<Bitmap>()
        val executor = Executors.newSingleThreadExecutor()
        try {
            assertTrue(app.profilePreferences.edit().putString(activeProfileKey, profileA).commit())
            val provider = spyk(ArtworkContentProvider().also { it.attachInfo(app, null) })
            every { provider.callingPackage } returns app.packageName
            val uriA = ArtworkContentProvider.uriForSource(app, songId, fileA)
            assertEquals(if (customCover) songId else "source", uriA.pathSegments.first())
            mockkObject(ArtworkBitmapDecoder)
            every { ArtworkBitmapDecoder.file(fileA) } answers {
                val bitmap = requireNotNull(callOriginal() as Bitmap?)
                decodedA.set(bitmap)
                // The real provider has already admitted A and decoded its pixels, but
                // cannot perform its post-decode ownership check until explicitly released.
                decoderEntered.countDown()
                check(releaseDecode.await(10, TimeUnit.SECONDS)) { "decode barrier was not released" }
                bitmap
            }
            val pending = executor.submit<ParcelFileDescriptor?> { provider.openFile(uriA, "r") }
            assertTrue("A must reach decoding before changing profiles", decoderEntered.await(10, TimeUnit.SECONDS))
            assertEquals(Color.RED, requireNotNull(decodedA.get()).getPixel(64, 64))
            assertTrue("A cannot publish while decoding is blocked", !pending.isDone)
            assertTrue(app.profilePreferences.edit().putString(activeProfileKey, profileB).commit())
            releaseDecode.countDown()
            val staleDescriptor = pending.get(10, TimeUnit.SECONDS)
            try {
                assertNull("A must not publish any descriptor after switching to B", staleDescriptor)
            } finally { staleDescriptor?.close() }
            assertTrue("discarded A bitmap must be recycled", requireNotNull(decodedA.get()).isRecycled)

            val uriB = ArtworkContentProvider.uriForSource(app, songId, fileB)
            val bytes = ParcelFileDescriptor.AutoCloseInputStream(
                requireNotNull(provider.openFile(uriB, "r"))).use { it.readBytes() }
            val bitmapB = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            try {
                val pixel = bitmapB.getPixel(bitmapB.width / 2, bitmapB.height / 2)
                assertTrue("subsequent B request must serve B pixels", Color.blue(pixel) > 230 &&
                    Color.red(pixel) < 25 && Color.green(pixel) < 25)
            } finally { bitmapB.recycle() }
        } finally {
            releaseDecode.countDown()
            executor.shutdownNow()
            assertTrue("provider worker must terminate", executor.awaitTermination(10, TimeUnit.SECONDS))
            fileA.delete()
            fileB.delete()
            app.profilePreferences.edit().putString(activeProfileKey, previousProfile).commit()
        }
    }

    private fun assertRed(descriptor: ParcelFileDescriptor) {
        assertTrue(descriptor.statSize > 0)
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try {
            assertEquals(bitmap.width, bitmap.height)
            assertTrue(bitmap.width <= 512)
            val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            assertTrue("must serve the red source, with JPEG tolerance", Color.red(pixel) > 230 &&
                Color.green(pixel) < 25 && Color.blue(pixel) < 25)
        } finally { bitmap.recycle() }
    }

    @Test fun `generic mapped file serves actual pixels only to trusted caller in originating profile`() {
        val app = RuntimeEnvironment.getApplication()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns app
        app.profilePreferences.edit().putString(activeProfileKey, "source-profile").commit()
        val file = File.createTempFile("unrelated-photo-name", ".png")
        val source = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.RED)
        file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
        source.recycle()
        try {
            val provider = spyk(ArtworkContentProvider().also { it.attachInfo(app, null) })
            every { provider.callingPackage } returns app.packageName
            val song = Song("stream-generic", "Title", "Artist", "1:00", file.toURI().toASCIIString())
            val item = SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = false)
            val uri = requireNotNull(item.mediaMetadata.artworkUri)
            assertEquals("source", uri.pathSegments.first())
            assertTrue(!uri.toString().contains(file.name))
            assertRed(requireNotNull(provider.openFile(uri, "r")))
            // Missing, untrusted and cross-profile tokens must produce the same placeholder.
            fun read(uriToRead: Uri): ByteArray = ParcelFileDescriptor.AutoCloseInputStream(
                requireNotNull(provider.openFile(uriToRead, "r"))).use { it.readBytes() }
            val placeholder = read(uri.buildUpon().path("source/unknown-token").build())
            every { provider.callingPackage } returns "untrusted.app"
            org.junit.Assert.assertArrayEquals(placeholder, read(uri))
            every { provider.callingPackage } returns app.packageName
            app.profilePreferences.edit().putString(activeProfileKey, "other-profile").commit()
            org.junit.Assert.assertArrayEquals(placeholder, read(uri))
        } finally {
            file.delete()
            app.profilePreferences.edit().remove(activeProfileKey).commit()
        }
    }

    @Test fun `untrusted caller cannot query or read MediaStore`() {
        val app = RuntimeEnvironment.getApplication()
        app.profilePreferences.edit().clear().commit()
        val resolver = mockk<ContentResolver>(relaxed = true)
        val context = object : ContextWrapper(app) {
            override fun getContentResolver(): ContentResolver = resolver
        }
        val provider = ArtworkContentProvider().also { it.attachInfo(context, null) }
        // Robolectric's caller is null, which the trust rules reject.
        val pfd = provider.openFile(Uri.parse("content://artwork/local:9987"), "r")
        assertNotNull(pfd)
        requireNotNull(pfd).close()
        verify(exactly = 0) { resolver.query(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { resolver.openInputStream(any()) }
    }

    @Test fun `custom profile cover produces bounded square seekable JPEG`() {
        val app = RuntimeEnvironment.getApplication()
        app.profilePreferences.edit().putString(activeProfileKey, "bounded").commit()
        val file = File(app.filesDir, "app_covers_bounded/cover_9988.jpg")
        file.parentFile?.mkdirs()
        val source = Bitmap.createBitmap(1700, 1100, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.RED)
        file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        source.recycle()
        try {
            val provider = ArtworkContentProvider().also { it.attachInfo(app, null) }
            val descriptor = requireNotNull(provider.openFile(Uri.parse("content://artwork/9988"), "r"))
            assertRed(descriptor)
        } finally {
            file.delete()
            app.profilePreferences.edit().remove(activeProfileKey).commit()
        }
    }
}
