package app.n_zik.android.playback.utils

import android.app.Application
import android.content.ContentResolver
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.playback.services.ArtworkContentProvider
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.setActiveProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionBitmapLoaderTest {
    @Test fun `file fallback is bounded and oriented after a single shared request`() {
        val file = File.createTempFile("session-cover", ".jpg")
        val uri = Uri.fromFile(file)
        val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
        mockkObject(ImageCacheFactory)
        try {
            val source = Bitmap.createBitmap(960, 480, Bitmap.Config.ARGB_8888)
            try {
                source.eraseColor(Color.RED)
                file.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
            } finally {
                source.recycle()
            }
            ExifInterface(file.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            coEvery {
                ImageCacheFactory.loadBitmap(uri.toString(), requestSize = 250, cacheDecodedBitmap = false, profile = "default")
            } returns null

            val loader = SessionBitmapLoader(RuntimeEnvironment.getApplication(), scope, 250)
            val artwork = loader.loadBitmap(uri).get(5, TimeUnit.SECONDS)

            assertEquals(125, artwork.width)
            assertEquals(250, artwork.height)
            assertTrue(Color.red(artwork.getPixel(0, 0)) > 240)
            coVerify(exactly = 1) {
                ImageCacheFactory.loadBitmap(uri.toString(), requestSize = 250, cacheDecodedBitmap = false, profile = "default")
            }
            artwork.recycle()
        } finally {
            scope.cancel()
            unmockkObject(ImageCacheFactory)
            file.delete()
        }
    }

    @Test fun `missing local artwork completes with bounded launcher image without retry`() {
        val uri = Uri.fromFile(File(RuntimeEnvironment.getApplication().cacheDir, "missing-session-cover.jpg"))
        val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
        mockkObject(ImageCacheFactory)
        try {
            coEvery {
                ImageCacheFactory.loadBitmap(uri.toString(), requestSize = 250, cacheDecodedBitmap = false, profile = "default")
            } returns null
            val loader = SessionBitmapLoader(RuntimeEnvironment.getApplication(), scope, 250)
            val artwork = loader.loadBitmap(uri).get(5, TimeUnit.SECONDS)

            assertNotEquals(0, minOf(artwork.width, artwork.height))
            assertTrue(maxOf(artwork.width, artwork.height) <= 250)
            coVerify(exactly = 1) {
                ImageCacheFactory.loadBitmap(uri.toString(), requestSize = 250, cacheDecodedBitmap = false, profile = "default")
            }
        } finally {
            scope.cancel()
            unmockkObject(ImageCacheFactory)
        }
    }

    @Test fun `provider fallback uses the captured owner and discards the decode after a profile switch`() {
        val app = RuntimeEnvironment.getApplication()
        val songId = "session-loader-cover"
        val legacy = ArtworkContentProvider.uriFor(songId)
        val bound = requireNotNull(ArtworkContentProvider.scopedUriFor(app, "default", songId))
        val resolver = mockk<ContentResolver>()
        val file = File.createTempFile("session-provider-cover", ".png", app.cacheDir)
        every { resolver.openFileDescriptor(bound, "r") } answers {
            setActiveProfile("secondary", app)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        val context = object : ContextWrapper(app) {
            override fun getContentResolver(): ContentResolver = resolver
        }
        val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
        mockkObject(ImageCacheFactory)
        try {
            val source = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            try {
                source.eraseColor(Color.RED)
                file.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                source.recycle()
            }
            coEvery {
                ImageCacheFactory.loadBitmap(bound.toString(), requestSize = 250, cacheDecodedBitmap = false, profile = "default")
            } returns null
            val loader = SessionBitmapLoader(context, scope, 250)
            val artwork = loader.loadBitmap(legacy).get(5, TimeUnit.SECONDS)

            assertSame(loader.loadBitmap(bound).get(5, TimeUnit.SECONDS), artwork)
            verify(exactly = 1) { resolver.openFileDescriptor(bound, "r") }
            verify(exactly = 0) { resolver.openFileDescriptor(legacy, "r") }
        } finally {
            scope.cancel()
            setActiveProfile("default", app)
            unmockkObject(ImageCacheFactory)
            file.delete()
        }
    }

    @Test fun `a stale coil result stays borrowed and the session receives the fallback`() {
        val app = RuntimeEnvironment.getApplication()
        val uri = Uri.fromFile(File(app.cacheDir, "switched-coil-cover.jpg"))
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
        mockkObject(ImageCacheFactory)
        try {
            coEvery {
                ImageCacheFactory.loadBitmap(uri.toString(), requestSize = 250, cacheDecodedBitmap = false, profile = "default")
            } answers {
                setActiveProfile("secondary", app)
                bitmap
            }
            val loader = SessionBitmapLoader(app, scope, 250)
            val artwork = loader.loadBitmap(uri).get(5, TimeUnit.SECONDS)

            assertSame(loader.loadBitmap(uri).get(5, TimeUnit.SECONDS), artwork)
            assertFalse(bitmap.isRecycled)
        } finally {
            scope.cancel()
            setActiveProfile("default", app)
            unmockkObject(ImageCacheFactory)
            bitmap.recycle()
        }
    }
}
